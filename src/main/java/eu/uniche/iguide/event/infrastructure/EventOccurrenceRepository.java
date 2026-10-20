package eu.uniche.iguide.event.infrastructure;

import eu.uniche.iguide.event.domain.EventOccurrence;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface EventOccurrenceRepository extends JpaRepository<EventOccurrence, UUID> {

    List<EventOccurrence> findByProjectIdAndEventIdInOrderByStartsAtAsc(UUID projectId, Collection<UUID> eventIds);

    Optional<EventOccurrence> findByProjectIdAndEventIdAndId(UUID projectId, UUID eventId, UUID id);

    long countByProjectIdAndEventId(UUID projectId, UUID eventId);

    /** Occurrences overlapping {@code [from, to)}, for calendar queries ({@code 08} Milestone 9). */
    @Query("""
            select o from EventOccurrence o
            where o.projectId = :projectId and o.endsAt > :from and o.startsAt < :to
            order by o.startsAt asc
            """)
    List<EventOccurrence> findOverlapping(@Param("projectId") UUID projectId, @Param("from") Instant from,
                                          @Param("to") Instant to);
}
