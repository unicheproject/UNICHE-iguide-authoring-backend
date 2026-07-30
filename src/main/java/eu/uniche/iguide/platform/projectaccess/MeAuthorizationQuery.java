package eu.uniche.iguide.platform.projectaccess;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueConditionalResponse;
import eu.uniche.iguide.platform.catalogue.CatalogueMeAuthorizationDto;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Reads the Catalogue's authorization view of the current subject, with a short cache.
 *
 * <p>Caching rules, as specified:
 *
 * <ul>
 *   <li>keyed by subject, held for the TTL the Catalogue itself declares in the response;
 *   <li>refresh is conditional with {@code If-None-Match}; a {@code 304} keeps the cached body and only
 *       extends its expiry;
 *   <li>the cache is not distributed. Across several instances the worst case is one extra round-trip
 *       per instance, never cross-instance staleness, because every instance honours the same TTL.
 * </ul>
 *
 * <p>This is not an enforcement mechanism. It exists so a client can decide what to show or hide; the
 * Catalogue still rejects any operation the subject may not perform, and every Project-scoped request
 * still performs its own access check through {@link ProjectAccessGateway}.
 */
@Service
public class MeAuthorizationQuery {

    private final CatalogueClient catalogueClient;
    private final Map<String, CachedAuthorization> cache = new ConcurrentHashMap<>();

    private record CachedAuthorization(CatalogueMeAuthorizationDto view, Instant expiresAt) {
    }

    public MeAuthorizationQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public CatalogueMeAuthorizationDto forSubject(String subject) {
        Instant now = Instant.now();
        CachedAuthorization cached = cache.get(subject);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.view();
        }

        String etag = cached == null ? null : cached.view().etag();
        CatalogueConditionalResponse<CatalogueMeAuthorizationDto> response =
                catalogueClient.getMeAuthorization(etag);

        CatalogueMeAuthorizationDto view =
                response.notModified() ? cached.view() : response.body();
        cache.put(subject, new CachedAuthorization(view, now.plusSeconds(view.ttlSeconds())));
        return view;
    }

    /** Drops the cached view for a subject, for tests and for operations that invalidate it. */
    public void evict(String subject) {
        cache.remove(subject);
    }
}
