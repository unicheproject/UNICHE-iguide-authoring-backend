package eu.uniche.iguide.platform.catalogue;

import eu.uniche.iguide.platform.correlation.CorrelationIdFilter;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter for the Catalogue.
 *
 * <p>Two things about it are deliberate.
 *
 * <p>First, the timeouts are explicit. The JDK HTTP client has no read timeout at all unless one is
 * set, so a hung Catalogue would otherwise occupy a request thread indefinitely rather than failing.
 *
 * <p>Second, error mapping is registered once on the builder rather than per call. Mapping per method
 * is what lets a method be forgotten, and a forgotten method leaks a raw client exception into the
 * catch-all handler, which reports {@code 500} and throws away the real upstream status. The
 * MuseotekBox sister backend documents exactly that defect on its unintercepted project listing.
 *
 * <p>Note the spelling: the Catalogue's own path segment is {@code organisations}, while this API
 * exposes {@code organizations}. Both are intentional and neither should be "fixed" to match the
 * other.
 */
@Component
public class RestClientCatalogueClient implements CatalogueClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientCatalogueClient.class);

    private final RestClient restClient;

    public RestClientCatalogueClient(CatalogueProperties properties, RestClient.Builder builder) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInitializer(request -> {
                    // The user's own token, forwarded unchanged: the Catalogue authorizes the person,
                    // not this backend.
                    request.getHeaders().setBearerAuth(bearerToken());
                    String requestId = CorrelationIdFilter.currentRequestId();
                    if (requestId != null) {
                        request.getHeaders().add(CorrelationIdFilter.HEADER, requestId);
                    }
                })
                .defaultStatusHandler(HttpStatusCode::isError, RestClientCatalogueClient::mapError)
                .build();
    }

    @Override
    public CatalogueProjectDto getProject(UUID projectId) {
        return call(() -> restClient.get()
                .uri("/api/v1/projects/{id}", projectId)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    @Override
    public CatalogueProjectDto createProject(UUID organizationId,
                                             CatalogueCreateProjectRequest request) {
        return call(() -> restClient.post()
                .uri("/api/v1/organisations/{orgId}/projects", organizationId)
                .body(request)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    /**
     * The Catalogue's own endpoint is {@code PUT}, while this backend exposes {@code PATCH} for the
     * same operation. That mismatch is intentional, not a bug: do not "fix" this call to {@code PATCH}.
     */
    @Override
    public CatalogueProjectDto updateProject(UUID projectId, CatalogueUpdateProjectRequest request) {
        return call(() -> restClient.put()
                .uri("/api/v1/projects/{id}", projectId)
                .body(request)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    @Override
    public void deleteProject(UUID projectId) {
        call(() -> restClient.delete()
                .uri("/api/v1/projects/{id}", projectId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public CatalogueProjectDto restoreProject(UUID projectId) {
        return call(() -> restClient.post()
                .uri("/api/v1/projects/{id}/restore", projectId)
                .retrieve()
                .body(CatalogueProjectDto.class));
    }

    @Override
    public List<CatalogueProjectDto> listProjectsForOrganization(UUID organizationId) {
        return callForList(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects", organizationId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueProjectDto>>() {
                }));
    }

    @Override
    public List<CatalogueProjectDto> listDeletedProjectsForOrganization(UUID organizationId) {
        return callForList(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}/projects/deleted", organizationId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<CatalogueProjectDto>>() {
                }));
    }

    @Override
    public CatalogueOrganizationDto getOrganization(UUID organizationId) {
        return call(() -> restClient.get()
                .uri("/api/v1/organisations/{orgId}", organizationId)
                .retrieve()
                .body(CatalogueOrganizationDto.class));
    }

    @Override
    public CatalogueConditionalResponse<CatalogueMeAuthorizationDto> getMeAuthorization(String etag) {
        ResponseEntity<CatalogueMeAuthorizationDto> response = call(() -> {
            var request = restClient.get().uri("/api/v1/me/authorization");
            if (etag != null) {
                request = request.header(HttpHeaders.IF_NONE_MATCH, etag);
            }
            return request.retrieve().toEntity(CatalogueMeAuthorizationDto.class);
        });
        if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
            return CatalogueConditionalResponse.unchanged();
        }
        CatalogueMeAuthorizationDto body = response.getBody();
        if (body == null) {
            throw new CatalogueBadResponseException(
                    "The Catalogue returned no body for the current-subject authorization view");
        }
        return CatalogueConditionalResponse.changed(body);
    }

    /**
     * Translates transport failures, which no status handler can see because no status was ever
     * received, and guarantees a non-null body for the calls that need one.
     */
    private <T> T call(java.util.function.Supplier<T> operation) {
        T result;
        try {
            result = operation.get();
        } catch (ResourceAccessException transportFailure) {
            throw transportFailureOf(transportFailure);
        }
        if (result == null) {
            throw new CatalogueBadResponseException("The Catalogue returned an empty response body");
        }
        return result;
    }

    /** An empty collection is a valid answer, so the non-null guarantee of {@link #call} is relaxed. */
    private <T> List<T> callForList(java.util.function.Supplier<List<T>> operation) {
        try {
            List<T> result = operation.get();
            return result == null ? List.of() : result;
        } catch (ResourceAccessException transportFailure) {
            throw transportFailureOf(transportFailure);
        }
    }

    private static CatalogueException transportFailureOf(ResourceAccessException failure) {
        // A timeout is worth retrying and an outage is worth reporting, so they stay distinguishable
        // all the way out to 504 and 503.
        for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return new CatalogueTimeoutException("The Catalogue did not respond in time", failure);
            }
        }
        return new CatalogueUnavailableException("The Catalogue could not be reached", failure);
    }

    private static void mapError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        // The target, never the upstream body: it may quote values this backend should not repeat.
        String target = request.getMethod() + " " + request.getURI().getPath();
        switch (status) {
            case 403 -> throw new CatalogueForbiddenException(
                    "The Catalogue denied " + target + " for this subject");
            case 404 -> throw new CatalogueNotFoundException(
                    "The Catalogue has no accessible resource for " + target);
            case 409 -> throw new CatalogueConflictException(
                    "The Catalogue reported a conflict for " + target);
            case 408, 504 -> throw new CatalogueTimeoutException(
                    "The Catalogue reported a timeout for " + target, null);
            default -> {
                if (response.getStatusCode().is5xxServerError()) {
                    throw new CatalogueUnavailableException(
                            "The Catalogue failed with status " + status + " for " + target);
                }
                // Any other 4xx means this backend and the Catalogue disagree about the contract —
                // most often a token the Catalogue will not accept, which is a misconfiguration here
                // rather than something the caller did wrong.
                log.error("Unexpected Catalogue status {} for {}", status, target);
                throw new CatalogueBadResponseException(
                        "The Catalogue rejected " + target + " with status " + status);
            }
        }
    }

    private static String bearerToken() {
        return CurrentPrincipal.jwt()
                .map(jwt -> jwt.getTokenValue())
                .orElseThrow(() -> new IllegalStateException(
                        "No validated JWT to forward to the Catalogue"));
    }
}
