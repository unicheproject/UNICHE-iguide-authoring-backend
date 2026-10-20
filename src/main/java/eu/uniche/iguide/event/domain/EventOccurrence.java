package eu.uniche.iguide.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One time an Event takes place ({@code 03} section 15): instants, rendered in the Project timezone.
 * Repeated dates are one row each; there is no recurrence rule.
 */
@Entity
@Table(name = "event_occurrences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOccurrence {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OccurrenceStatus status;

    public static EventOccurrence of(UUID projectId, UUID eventId, Instant startsAt, Instant endsAt,
                                     OccurrenceStatus status) {
        EventOccurrence occurrence = new EventOccurrence();
        occurrence.projectId = projectId;
        occurrence.eventId = eventId;
        occurrence.reschedule(startsAt, endsAt, status);
        return occurrence;
    }

    public void reschedule(Instant startsAt, Instant endsAt, OccurrenceStatus status) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
    }
}
