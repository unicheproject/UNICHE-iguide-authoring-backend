package eu.uniche.iguide.platform.identitysnapshot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySnapshotRepository extends JpaRepository<IdentitySnapshot, UUID> {

    Optional<IdentitySnapshot> findByIssuerAndSubject(String issuer, String subject);
}
