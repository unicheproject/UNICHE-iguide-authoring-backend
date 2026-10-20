package eu.uniche.iguide.language.infrastructure;

import eu.uniche.iguide.language.domain.ProjectLanguage;
import eu.uniche.iguide.language.domain.ProjectLanguageId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface ProjectLanguageRepository extends JpaRepository<ProjectLanguage, ProjectLanguageId> {

    List<ProjectLanguage> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    List<ProjectLanguage> findByProjectIdAndEnabledTrueOrderBySortOrderAsc(UUID projectId);
}
