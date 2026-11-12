package eu.uniche.iguide.project.infrastructure;

import eu.uniche.iguide.project.domain.Project;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The one repository whose primary lookup is by id alone, because here the id <em>is</em> the Project
 * identifier and there is no wider tenant to scope it to. Every authored resource's repository takes
 * Project context explicitly.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndLocallyDeletedAtIsNull(UUID id);

    /**
     * Locks the companion row for the rest of the transaction, so two concurrent initializations of one Project
     * run one after the other and the second sees {@code initializedAt} set ({@code 04} section 8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") UUID id);
}
