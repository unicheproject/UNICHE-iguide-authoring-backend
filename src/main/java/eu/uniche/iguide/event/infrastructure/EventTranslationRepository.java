package eu.uniche.iguide.event.infrastructure;

import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface EventTranslationRepository extends JpaRepository<EventTranslation, UUID> {

    List<EventTranslation> findByProjectIdAndEventId(UUID projectId, UUID eventId);

    List<EventTranslation> findByProjectIdAndEventIdIn(UUID projectId, Collection<UUID> eventIds);

    @Query("""
            select count(t) from EventTranslation t, Event e
            where t.projectId = :projectId and e.projectId = :projectId and e.id = t.eventId
              and t.languageCode = :languageCode and e.status <> :excluded
              and t.title is not null and trim(t.title) <> ''
            """)
    long countTitled(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                     @Param("excluded") ResourceStatus excluded);
}
