package eu.uniche.iguide.project.infrastructure;

import eu.uniche.iguide.project.domain.Project;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The one repository whose primary lookup is by id alone, because here the id <em>is</em> the Project
 * identifier and there is no wider tenant to scope it to. Every authored resource's repository takes
 * Project context explicitly.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndLocallyDeletedAtIsNull(UUID id);
}
