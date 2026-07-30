package eu.uniche.iguide.platform.organization;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Refreshes the organization snapshot opportunistically and idempotently. */
@Service
public class OrganizationSnapshotService {

    private final CatalogueOrganizationSnapshotRepository repository;

    public OrganizationSnapshotService(CatalogueOrganizationSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CatalogueOrganizationSnapshot upsert(UUID id, String name, String slug, String status) {
        Instant now = Instant.now();
        return repository.findById(id)
                .map(existing -> {
                    existing.apply(name, slug, status, now);
                    return existing;
                })
                .orElseGet(() -> repository.save(
                        CatalogueOrganizationSnapshot.of(id, name, slug, status, now)));
    }
}
