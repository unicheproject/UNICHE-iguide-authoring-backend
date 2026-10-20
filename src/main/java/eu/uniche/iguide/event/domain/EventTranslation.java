package eu.uniche.iguide.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The visitor-facing text of an Event in one language ({@code 03} section 15). */
@Entity
@Table(name = "event_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String summary;

    private String description;

    @Column(name = "location_text")
    private String locationText;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static EventTranslation of(UUID projectId, UUID eventId, String languageCode) {
        EventTranslation translation = new EventTranslation();
        translation.projectId = projectId;
        translation.eventId = eventId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String summary, String description, String locationText) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.locationText = locationText;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
