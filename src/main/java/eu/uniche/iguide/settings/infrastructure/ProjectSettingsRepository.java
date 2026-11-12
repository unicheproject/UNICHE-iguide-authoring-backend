package eu.uniche.iguide.settings.infrastructure;

import eu.uniche.iguide.settings.domain.ProjectSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Keyed by the Project: every lookup takes it explicitly. */
public interface ProjectSettingsRepository extends JpaRepository<ProjectSettings, UUID> {

    /** One step up in SQL; the entity never writes the column (see {@code ProjectSettings.contentRevision}). */
    @Modifying(flushAutomatically = true)
    @Query("update ProjectSettings s set s.contentRevision = s.contentRevision + 1 where s.projectId = :projectId")
    int incrementContentRevision(@Param("projectId") UUID projectId);
}
