package eu.uniche.iguide.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Route Stop ({@code 03} section 9): a first-class entity binding a 0-based position of the Route to a
 * Point of Interest of the same Project. It carries no trigger, duration, or optional flag of its own.
 */
@Entity
@Table(name = "route_stops")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStop {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "route_id", nullable = false, updatable = false)
    private UUID routeId;

    @Column(name = "poi_id", nullable = false)
    private UUID poiId;

    @Column(nullable = false)
    private int position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Version
    @Column(nullable = false)
    private long version;

    public static RouteStop of(UUID projectId, UUID routeId, UUID poiId, int position) {
        RouteStop stop = new RouteStop();
        stop.projectId = projectId;
        stop.routeId = routeId;
        stop.poiId = poiId;
        stop.position = position;
        return stop;
    }

    public void bindTo(UUID poiId) {
        this.poiId = poiId;
    }

    public void moveTo(int position) {
        this.position = position;
    }
}
