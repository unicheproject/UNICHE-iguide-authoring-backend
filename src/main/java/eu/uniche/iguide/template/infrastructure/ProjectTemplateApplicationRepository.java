package eu.uniche.iguide.template.infrastructure;

import eu.uniche.iguide.template.domain.ProjectTemplateApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Project-owned: every Project lookup takes the Project explicitly. */
public interface ProjectTemplateApplicationRepository extends JpaRepository<ProjectTemplateApplication, UUID> {

    Optional<ProjectTemplateApplication> findByProjectId(UUID projectId);

    /** Cross-Project by design: the startup check that an applied version's file is unchanged. */
    boolean existsByTemplateId(UUID templateId);
}
