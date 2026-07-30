package eu.uniche.iguide.platform.organization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogueOrganizationSnapshotRepository
        extends JpaRepository<CatalogueOrganizationSnapshot, UUID> {
}
