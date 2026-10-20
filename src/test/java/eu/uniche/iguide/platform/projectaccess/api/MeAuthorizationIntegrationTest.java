package eu.uniche.iguide.platform.projectaccess.api;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto.ProjectMembershipView;
import eu.uniche.iguide.support.PostgresIntegrationTest;
import eu.uniche.iguide.support.TestIdp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /api/v1/me/authorization} is an uncached passthrough of the Catalogue body ({@code 02} §10). */
class MeAuthorizationIntegrationTest extends PostgresIntegrationTest {

    private static final String ORGANIZATION_ID = "44444444-4444-4444-4444-444444444444";
    private static final String PROJECT_ID = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogueClient catalogueClient;

    @Test
    void returnsTheCatalogueBodyInTheCataloguesShape() throws Exception {
        when(catalogueClient.getMeAuthorization()).thenReturn(new CatalogueMeAuthorizationDto(
                "subject-1", false, List.of(ORGANIZATION_ID),
                List.of(new ProjectMembershipView(PROJECT_ID, ORGANIZATION_ID, "MEMBER")),
                "\"etag-1\"", 60L));

        mockMvc.perform(authenticated(get("/api/v1/me/authorization")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "subject": "subject-1",
                          "platformAdmin": false,
                          "managedOrganisations": ["%s"],
                          "projectMemberships": [
                            {"projectId": "%s", "orgId": "%s", "role": "MEMBER"}
                          ],
                          "etag": "\\"etag-1\\"",
                          "ttlSeconds": 60
                        }
                        """.formatted(ORGANIZATION_ID, PROJECT_ID, ORGANIZATION_ID), true));
    }

    @Test
    void asksTheCatalogueOnEveryRequest() throws Exception {
        when(catalogueClient.getMeAuthorization()).thenReturn(new CatalogueMeAuthorizationDto(
                "subject-1", false, List.of(), List.of(), "\"etag-1\"", 60L));

        mockMvc.perform(authenticated(get("/api/v1/me/authorization"))).andExpect(status().isOk());
        mockMvc.perform(authenticated(get("/api/v1/me/authorization"))).andExpect(status().isOk());

        verify(catalogueClient, times(2)).getMeAuthorization();
    }

    @Test
    void refusesAnUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/me/authorization"))
                .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TestIdp.validToken("subject-1"));
    }
}
