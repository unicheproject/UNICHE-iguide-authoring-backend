package eu.uniche.iguide.theme.infrastructure;

import eu.uniche.iguide.theme.domain.ProjectTheme;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by the Project: every lookup takes it explicitly. */
public interface ProjectThemeRepository extends JpaRepository<ProjectTheme, UUID> {
}
