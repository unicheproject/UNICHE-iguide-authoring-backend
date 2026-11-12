package eu.uniche.iguide.navigation.domain;

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
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A menu of the visitor app ({@code 03} section 13): at most one per {@code context} in a Project, shared by every
 * surface. It has no lifecycle; every save is live and raises {@code contentRevision}.
 */
@Entity
@Table(name = "navigations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Navigation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NavigationContext context;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

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

    public static Navigation of(UUID projectId, NavigationContext context, String name, String subject, Instant now) {
        Navigation navigation = new Navigation();
        navigation.projectId = projectId;
        navigation.context = context;
        navigation.name = name;
        navigation.createdAt = now;
        navigation.createdBySubject = subject;
        navigation.touch(subject, now);
        return navigation;
    }

    public void edit(String name, Map<String, Object> configuration, String subject, Instant now) {
        this.name = name;
        this.configuration = configuration == null ? new HashMap<>() : new HashMap<>(configuration);
        touch(subject, now);
    }

    /** Any change to the menu's items counts as a change of the menu. */
    public void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
