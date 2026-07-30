package eu.uniche.iguide.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueConflictException;
import eu.uniche.iguide.platform.catalogue.CatalogueCreateProjectRequest;
import eu.uniche.iguide.platform.catalogue.CatalogueForbiddenException;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.catalogue.CatalogueOrganizationDto;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.catalogue.CatalogueTimeoutException;
import eu.uniche.iguide.platform.catalogue.CatalogueUnavailableException;
import eu.uniche.iguide.project.domain.Project;
import eu.uniche.iguide.project.infrastructure.ProjectRepository;
import eu.uniche.iguide.support.PostgresIntegrationTest;
import eu.uniche.iguide.support.TestIdp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Project lifecycle end to end: real HTTP handling, real security chain, real PostgreSQL, with only
 * the Catalogue itself stubbed.
 */
class ProjectLifecycleIntegrationTest extends PostgresIntegrationTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORGANIZATION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projects;

    @MockitoBean
    private CatalogueClient catalogueClient;

    @BeforeEach
    void clean() {
        projects.deleteAll();
    }

    // --- Authentication ---------------------------------------------------------------------------

    @Test
    void refusesAnUnauthenticatedRequestWithTheStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID)
                        .header("X-Request-Id", "unauth-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().string("X-Request-Id", "unauth-1"))
                .andExpect(jsonPath("$.type").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").value("unauth-1"));
    }

    @Test
    void refusesATokenMintedForAnotherClient() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestIdp.tokenWithAudience("subject-1", "other-client")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesATokenFromAnotherIssuer() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + TestIdp.tokenWithIssuer("subject-1", "https://evil.example/realms/x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyResponseCarriesAGeneratedRequestIdWhenTheClientSuppliesNone() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    // --- Reading, and the reconciliation it performs ----------------------------------------------

    @Test
    void readingAProjectCreatesItsCompanionRow() throws Exception {
        when(catalogueClient.getProject(PROJECT_ID)).thenReturn(catalogueProject("Museum of Naxos"));

        mockMvc.perform(authenticated(get("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.name").value("Museum of Naxos"))
                .andExpect(jsonPath("$.initialized").value(false));

        assertThat(projects.findById(PROJECT_ID)).isPresent();
    }

    @Test
    void aCatalogueNotFoundReturnsNotFoundAndSoftDeletesTheCompanionRow() throws Exception {
        givenAnExistingCompanionRow();
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueNotFoundException("gone"));

        mockMvc.perform(authenticated(get("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));

        assertThat(projects.findById(PROJECT_ID).orElseThrow().isLocallyDeleted()).isTrue();
        assertThat(projects.findByIdAndLocallyDeletedAtIsNull(PROJECT_ID)).isEmpty();
    }

    /** A present local row is not access. It is only evidence the Catalogue once acknowledged the Project. */
    @Test
    void aLocalCompanionRowNeverGrantsAccess() throws Exception {
        givenAnExistingCompanionRow();
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueNotFoundException("not yours"));

        mockMvc.perform(authenticated(get("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnavailableCatalogueReturnsServiceUnavailableAndLeavesTheCompanionRowAlone()
            throws Exception {
        givenAnExistingCompanionRow();
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueUnavailableException("outage"));

        mockMvc.perform(authenticated(get("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("UPSTREAM_UNAVAILABLE"));

        assertThat(projects.findById(PROJECT_ID).orElseThrow().isLocallyDeleted()).isFalse();
    }

    @Test
    void aTimedOutCatalogueReturnsGatewayTimeout() throws Exception {
        when(catalogueClient.getProject(PROJECT_ID))
                .thenThrow(new CatalogueTimeoutException("too slow", null));

        mockMvc.perform(authenticated(get("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.type").value("UPSTREAM_TIMEOUT"));
    }

    // --- Writing -----------------------------------------------------------------------------------

    @Test
    void createsAProjectUpstreamFirstThenItsCompanionRow() throws Exception {
        when(catalogueClient.createProject(eq(ORGANIZATION_ID), any()))
                .thenReturn(catalogueProject("Museum of Naxos"));
        when(catalogueClient.getOrganization(ORGANIZATION_ID))
                .thenReturn(new CatalogueOrganizationDto(ORGANIZATION_ID.toString(), "Naxos", "naxos",
                        "ACTIVE"));

        mockMvc.perform(authenticated(post("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Museum of Naxos", "slug": "museum-of-naxos"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/projects/" + PROJECT_ID))
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()));

        // The tool slug identifies this backend and comes from configuration, never from the caller.
        var sentToCatalogue = org.mockito.ArgumentCaptor.forClass(CatalogueCreateProjectRequest.class);
        verify(catalogueClient).createProject(eq(ORGANIZATION_ID), sentToCatalogue.capture());
        assertThat(sentToCatalogue.getValue().toolSlug()).isEqualTo("iguide");

        assertThat(projects.findById(PROJECT_ID)).isPresent();
    }

    @Test
    void aDuplicateSlugUpstreamIsAConflict() throws Exception {
        when(catalogueClient.createProject(eq(ORGANIZATION_ID), any()))
                .thenThrow(new CatalogueConflictException("slug taken"));

        mockMvc.perform(authenticated(post("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Museum of Naxos", "slug": "museum-of-naxos"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("CONFLICT"));
    }

    @Test
    void aRefusedCreationIsForbiddenAndCreatesNoLocalRow() throws Exception {
        when(catalogueClient.createProject(eq(ORGANIZATION_ID), any()))
                .thenThrow(new CatalogueForbiddenException("not a manager"));

        mockMvc.perform(authenticated(post("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Museum of Naxos", "slug": "museum-of-naxos"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("FORBIDDEN"));

        assertThat(projects.count()).isZero();
    }

    @Test
    void updatesTheCatalogueOwnedNameAndTheLocallyOwnedDescription() throws Exception {
        when(catalogueClient.getProject(PROJECT_ID)).thenReturn(catalogueProject("Old name"));
        when(catalogueClient.updateProject(eq(PROJECT_ID), any()))
                .thenReturn(catalogueProject("New name"));

        mockMvc.perform(authenticated(patch("/api/v1/projects/{id}", PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New name", "localDescription": "Authoring notes"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.localDescription").value("Authoring notes"));

        assertThat(projects.findById(PROJECT_ID).orElseThrow().getLocalDescription())
                .isEqualTo("Authoring notes");
    }

    @Test
    void deletingAProjectSoftDeletesItsCompanionRow() throws Exception {
        when(catalogueClient.getProject(PROJECT_ID)).thenReturn(catalogueProject("Museum of Naxos"));

        mockMvc.perform(authenticated(delete("/api/v1/projects/{id}", PROJECT_ID)))
                .andExpect(status().isNoContent());

        verify(catalogueClient).deleteProject(PROJECT_ID);
        assertThat(projects.findById(PROJECT_ID).orElseThrow().isLocallyDeleted()).isTrue();
    }

    @Test
    void restoringAProjectClearsTheSoftDeleteFlag() throws Exception {
        givenASoftDeletedCompanionRow();
        when(catalogueClient.restoreProject(PROJECT_ID)).thenReturn(catalogueProject("Museum of Naxos"));

        mockMvc.perform(authenticated(post("/api/v1/projects/{id}/restore", PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROJECT_ID.toString()));

        assertThat(projects.findById(PROJECT_ID).orElseThrow().isLocallyDeleted()).isFalse();
    }

    // --- Listings, which touch no local state ------------------------------------------------------

    @Test
    void listingProjectsIsAPassthroughThatWritesNothingLocally() throws Exception {
        when(catalogueClient.listProjectsForOrganization(ORGANIZATION_ID))
                .thenReturn(List.of(catalogueProject("Museum of Naxos")));

        mockMvc.perform(authenticated(get("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PROJECT_ID.toString()));

        assertThat(projects.count()).isZero();
    }

    @Test
    void listingDeletedProjectsIsAlsoAPassthrough() throws Exception {
        when(catalogueClient.listDeletedProjectsForOrganization(ORGANIZATION_ID))
                .thenReturn(List.of(catalogueProject("Museum of Naxos")));

        mockMvc.perform(authenticated(
                        get("/api/v1/organizations/{orgId}/projects/deleted", ORGANIZATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PROJECT_ID.toString()));

        assertThat(projects.count()).isZero();
    }

    // --- Validation output -------------------------------------------------------------------------

    /**
     * The field path and the message, never the rejected value. The default framework field-error
     * representation includes that value, which would leak the contents of any sensitive field that later
     * acquires a constraint.
     */
    @Test
    void validationErrorsCarryPathsButNeverTheRejectedValue() throws Exception {
        String response = mockMvc.perform(
                        authenticated(post("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name": "", "slug": "Not A Valid Slug"}
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].path").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("slug").doesNotContain("Not A Valid Slug");
    }

    @Test
    void anUnparseableBodyIsABadRequestAndQuotesNothingBack() throws Exception {
        String response = mockMvc.perform(
                        authenticated(post("/api/v1/organizations/{orgId}/projects", ORGANIZATION_ID))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"secret-looking-value\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("MALFORMED_REQUEST"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("secret-looking-value");
    }

    // --- Helpers -----------------------------------------------------------------------------------

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            authenticated(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                                  request) {
        return request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + TestIdp.validToken("subject-1"));
    }

    private static CatalogueProjectDto catalogueProject(String name) {
        return new CatalogueProjectDto(PROJECT_ID.toString(), ORGANIZATION_ID.toString(), name,
                "museum-of-naxos", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("iguide"));
    }

    private void givenAnExistingCompanionRow() {
        projects.saveAndFlush(Project.companionOf(PROJECT_ID, ORGANIZATION_ID, "Museum of Naxos",
                "museum-of-naxos", "ACTIVE", java.time.Instant.now()));
    }

    private void givenASoftDeletedCompanionRow() {
        Project companion = Project.companionOf(PROJECT_ID, ORGANIZATION_ID, "Museum of Naxos",
                "museum-of-naxos", "ACTIVE", java.time.Instant.now());
        companion.markLocallyDeleted(java.time.Instant.now());
        projects.saveAndFlush(companion);
    }
}
