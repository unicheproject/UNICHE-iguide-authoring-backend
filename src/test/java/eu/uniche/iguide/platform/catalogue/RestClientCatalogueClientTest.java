package eu.uniche.iguide.platform.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.uniche.iguide.support.StubCatalogueServer;
import eu.uniche.iguide.support.StubCatalogueServer.Response;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Status mapping is registered once on the builder rather than per method, so these assertions hold for
 * every call the client makes. A per-method mapping is what allows one method to be forgotten, and a
 * forgotten method reports the wrong status: the raw client exception reaches the catch-all handler,
 * which answers {@code 500} and discards the real upstream status.
 */
class RestClientCatalogueClientTest {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private StubCatalogueServer catalogue;
    private CatalogueClient client;

    @BeforeEach
    void setUp() {
        catalogue = new StubCatalogueServer();
        client = clientFor(catalogue.baseUrl(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(Jwt.withTokenValue("the-users-token")
                        .header("alg", "RS256")
                        .subject("subject-1")
                        .claim("preferred_username", "someone")
                        .build()));
        MDC.put("requestId", "req-42");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
        catalogue.close();
    }

    @Test
    void forwardsTheUsersBearerTokenAndTheCorrelationHeader() {
        catalogue.willRespond(Response.of(200, projectJson()));

        client.getProject(PROJECT_ID);

        var request = catalogue.lastRequest();
        assertThat(request.authorization()).isEqualTo("Bearer the-users-token");
        assertThat(request.requestId()).isEqualTo("req-42");
    }

    @Test
    void readsAProject() {
        catalogue.willRespond(Response.of(200, projectJson()));

        CatalogueProjectDto project = client.getProject(PROJECT_ID);

        assertThat(project.id()).isEqualTo(PROJECT_ID.toString());
        assertThat(project.orgId()).isEqualTo(ORGANIZATION_ID.toString());
        assertThat(project.name()).isEqualTo("Museum of Naxos");
        assertThat(catalogue.lastRequest().path()).isEqualTo("/api/v1/projects/" + PROJECT_ID);
    }

    /** The Catalogue spells the segment {@code organisations}; this API exposes {@code organizations}. */
    @Test
    void usesTheCatalogueSpellingOfTheOrganizationSegment() {
        catalogue.willRespond(Response.of(200, "[]"));

        client.listProjectsForOrganization(ORGANIZATION_ID);

        assertThat(catalogue.lastRequest().path())
                .isEqualTo("/api/v1/organisations/" + ORGANIZATION_ID + "/projects");
    }

    /** Exposed as PATCH by this API, but PUT upstream. The mismatch is intentional. */
    @Test
    void updatesAProjectWithPutEvenThoughThisApiExposesPatch() {
        catalogue.willRespond(Response.of(200, projectJson()));

        client.updateProject(PROJECT_ID, new CatalogueUpdateProjectRequest("Renamed"));

        assertThat(catalogue.lastRequest().method()).isEqualTo("PUT");
    }

    @Test
    void mapsNotFoundOnEveryMethod() {
        assertMappedForEveryMethod(404, CatalogueNotFoundException.class);
    }

    @Test
    void mapsForbiddenOnEveryMethod() {
        assertMappedForEveryMethod(403, CatalogueForbiddenException.class);
    }

    @Test
    void mapsConflictOnEveryMethod() {
        assertMappedForEveryMethod(409, CatalogueConflictException.class);
    }

    @Test
    void mapsUpstreamServerErrorToUnavailableOnEveryMethod() {
        assertMappedForEveryMethod(503, CatalogueUnavailableException.class);
    }

    @Test
    void mapsUpstreamGatewayTimeoutToTimeout() {
        assertMappedForEveryMethod(504, CatalogueTimeoutException.class);
    }

    /**
     * An upstream {@code 401} means the Catalogue would not accept the token this backend forwarded,
     * which is a misconfiguration here rather than something the caller did wrong. It must not be
     * reflected back as an authentication failure of the caller's own token.
     */
    @Test
    void mapsAnUnexpectedUpstreamStatusToABadResponse() {
        assertMappedForEveryMethod(401, CatalogueBadResponseException.class);
    }

    @Test
    void mapsAReadTimeoutToATimeout() {
        CatalogueClient impatient =
                clientFor(catalogue.baseUrl(), Duration.ofSeconds(2), Duration.ofMillis(150));
        catalogue.willRespond(Response.slow(Duration.ofSeconds(2)));

        assertThatThrownBy(() -> impatient.getProject(PROJECT_ID))
                .isInstanceOf(CatalogueTimeoutException.class);
    }

    /** An unreachable Catalogue is 503, and distinguishable from a timeout, which is 504. */
    @Test
    void mapsAnUnreachableCatalogueToUnavailable() {
        CatalogueClient pointingAtNothing =
                clientFor("http://127.0.0.1:1", Duration.ofMillis(300), Duration.ofMillis(300));

        assertThatThrownBy(() -> pointingAtNothing.getProject(PROJECT_ID))
                .isInstanceOf(CatalogueUnavailableException.class);
    }

    @Test
    void treatsAnEmptyBodyWhereOneIsRequiredAsABadResponse() {
        catalogue.willRespond(Response.status(204));

        assertThatThrownBy(() -> client.getProject(PROJECT_ID))
                .isInstanceOf(CatalogueBadResponseException.class);
    }

    @Test
    void readsTheAuthorizationViewUnconditionallyInTheCataloguesShape() {
        catalogue.willRespond(Response.of(200, authorizationJson()));

        var view = client.getMeAuthorization();

        assertThat(view.managedOrganisations()).containsExactly(ORGANIZATION_ID.toString());
        assertThat(view.etag()).isEqualTo("\"etag-1\"");
        assertThat(view.ttlSeconds()).isEqualTo(60L);
        assertThat(catalogue.lastRequest().ifNoneMatch()).isNull();
    }

    /**
     * Every method, one status. The point of the exercise is coverage of the method list, not of the
     * status list: a new method added without mapping fails here.
     */
    private void assertMappedForEveryMethod(int status, Class<? extends Throwable> expected) {
        catalogue.willRespond(Response.status(status));
        for (Consumer<CatalogueClient> call : allMethods()) {
            assertThatThrownBy(() -> call.accept(client))
                    .describedAs("upstream %d", status)
                    .isInstanceOf(expected);
        }
    }

    private static List<Consumer<CatalogueClient>> allMethods() {
        return List.of(
                catalogue -> catalogue.getProject(PROJECT_ID),
                catalogue -> catalogue.createProject(ORGANIZATION_ID,
                        new CatalogueCreateProjectRequest("Name", "slug", "iguide")),
                catalogue -> catalogue.updateProject(PROJECT_ID,
                        new CatalogueUpdateProjectRequest("Name")),
                catalogue -> catalogue.deleteProject(PROJECT_ID),
                catalogue -> catalogue.restoreProject(PROJECT_ID),
                catalogue -> catalogue.listProjectsForOrganization(ORGANIZATION_ID),
                catalogue -> catalogue.listDeletedProjectsForOrganization(ORGANIZATION_ID),
                catalogue -> catalogue.getOrganization(ORGANIZATION_ID),
                catalogue -> catalogue.getMeAuthorization());
    }

    private static CatalogueClient clientFor(String baseUrl, Duration connectTimeout,
                                            Duration readTimeout) {
        return new RestClientCatalogueClient(
                new CatalogueProperties(baseUrl, connectTimeout, readTimeout),
                RestClient.builder());
    }

    private static String projectJson() {
        return """
                {
                  "id": "%s",
                  "orgId": "%s",
                  "name": "Museum of Naxos",
                  "slug": "museum-of-naxos",
                  "status": "ACTIVE",
                  "tool": { "slug": "iguide" }
                }
                """.formatted(PROJECT_ID, ORGANIZATION_ID);
    }

    private static String authorizationJson() {
        return """
                {
                  "subject": "subject-1",
                  "platformAdmin": false,
                  "managedOrganisations": ["%s"],
                  "projectMemberships": [],
                  "etag": "\\"etag-1\\"",
                  "ttlSeconds": 60
                }
                """.formatted(ORGANIZATION_ID);
    }

    /** Guards the assumption that a non-JWT authentication cannot be used to reach the Catalogue. */
    @Test
    void refusesToCallWithoutAValidatedToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "credentials"));

        assertThatThrownBy(() -> client.getProject(PROJECT_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
