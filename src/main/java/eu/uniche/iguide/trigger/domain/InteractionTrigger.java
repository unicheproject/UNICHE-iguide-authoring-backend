package eu.uniche.iguide.trigger.domain;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An Interaction Trigger ({@code 03} section 14): a mechanism that opens its target Resource in the visitor
 * Application. The type and the target are fixed at creation; {@code code} is the payload of a QR code.
 */
@Entity
@Table(name = "interaction_triggers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionTrigger {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_resource_type", nullable = false, updatable = false)
    private TriggerTargetType targetResourceType;

    @Column(name = "target_resource_id", nullable = false, updatable = false)
    private UUID targetResourceId;

    private String code;

    /** Null means every surface able to run the type. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_surfaces")
    private List<Surface> supportedSurfaces;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_subject", nullable = false, updatable = false)
    private String createdBySubject;

    @Column(name = "updated_by_subject", nullable = false)
    private String updatedBySubject;

    @Version
    @Column(nullable = false)
    private long version;

    public static InteractionTrigger draft(UUID projectId, TriggerType type, TriggerTargetType targetType, UUID targetId,
                                           Settings settings, int sortOrder, String subject, Instant now) {
        InteractionTrigger trigger = new InteractionTrigger();
        trigger.projectId = projectId;
        trigger.triggerType = type;
        trigger.targetResourceType = targetType;
        trigger.targetResourceId = targetId;
        trigger.status = ResourceStatus.DRAFT;
        trigger.sortOrder = sortOrder;
        trigger.createdAt = now;
        trigger.createdBySubject = subject;
        trigger.edit(settings, subject, now);
        return trigger;
    }

    public void edit(Settings settings, String subject, Instant now) {
        this.code = settings.code();
        this.supportedSurfaces = settings.supportedSurfaces();
        this.configuration = new HashMap<>(settings.configuration());
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    private void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }

    /** The editable part of a trigger, already validated and normalized. */
    public record Settings(String code, List<Surface> supportedSurfaces, Map<String, Object> configuration) {
    }
}
