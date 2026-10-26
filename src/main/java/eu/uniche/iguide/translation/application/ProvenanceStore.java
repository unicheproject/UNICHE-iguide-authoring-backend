package eu.uniche.iguide.translation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.workingcopy.application.WorkingCopyApplyHook;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import jakarta.persistence.EntityManager;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The content provenance of translation rows ({@code 03} section 1; DECISIONS.md D-035, D-036): the
 * {@code provenance} column of the six kinds' translation tables, of Route Stops', Guide Blocks', and Assets'. For an
 * ACTIVE Resource, the pending provenance its working copy carries in {@code translation_provenance}, written to the
 * rows on apply: under the language code for the Resource's own texts, under {@code stops/<stopId>/<lang>} or
 * {@code blocks/<blockId>/<lang>} for its child rows ({@code null} clearing it). Assets have no working copy.
 *
 * <p>Plain SQL on purpose: the working copy's {@code @Version} must not move when only provenance changes, or the
 * CMS's next save of the copy would conflict, and the translation entities keep the column as they loaded it. Every
 * write flushes the persistence context first, so a pending entity write cannot overwrite it afterwards. Callers run
 * inside a transaction.
 */
@Component
public class ProvenanceStore implements WorkingCopyApplyHook {

    public static final String AI_GENERATED = "AI_GENERATED";
    public static final String ROUTE_STOP = "ROUTE_STOP";
    public static final String GUIDE_BLOCK = "GUIDE_BLOCK";
    public static final String ASSET = "ASSET";

    /**
     * One provenance entry of a language, live or pending in a working copy. {@code parentType} and {@code parentId}
     * name the Route or Guide of a stop or block; {@code null} otherwise.
     */
    public record Entry(String resourceType, UUID resourceId, String parentType, UUID parentId, String languageCode,
                        Map<String, Object> provenance, boolean pending) {
    }

    /**
     * Whose texts: a Resource's own ({@code resourceType} its working-copy type), a Route Stop or Guide Block of a
     * Route or Guide ({@code parentType}, {@code parentId}), or an Asset.
     */
    public record Subject(String resourceType, UUID resourceId, WorkingCopyResourceType parentType, UUID parentId) {

        public static Subject of(WorkingCopyResourceType type, UUID resourceId) {
            return new Subject(type.name(), resourceId, null, null);
        }

        public static Subject stop(UUID routeId, UUID stopId) {
            return new Subject(ROUTE_STOP, stopId, WorkingCopyResourceType.ROUTE, routeId);
        }

        public static Subject block(UUID guideId, UUID blockId) {
            return new Subject(GUIDE_BLOCK, blockId, WorkingCopyResourceType.GUIDE, guideId);
        }

        public static Subject asset(UUID assetId) {
            return new Subject(ASSET, assetId, null, null);
        }

        /** The kind whose working copy carries the pending provenance; {@code null} for an Asset, which has none. */
        public WorkingCopyResourceType copyType() {
            if (parentType != null) {
                return parentType;
            }
            return ASSET.equals(resourceType) ? null : WorkingCopyResourceType.valueOf(resourceType);
        }

        public UUID copyId() {
            return parentId != null ? parentId : resourceId;
        }

        /** The key of a language in the working copy's {@code translation_provenance}. */
        String key(String language) {
            return parentType == null ? language : CHILDREN.get(resourceType) + "/" + resourceId + "/" + language;
        }

        Entry entry(String language, Map<String, Object> provenance, boolean pending) {
            return new Entry(resourceType, resourceId, parentType == null ? null : parentType.name(), parentId,
                    language, provenance, pending);
        }
    }

    private record Table(String name, String owner, String parent) {
    }

    /** The child collections of a working-copy payload, by the child's provenance type. */
    private static final Map<String, String> CHILDREN = Map.of(ROUTE_STOP, "stops", GUIDE_BLOCK, "blocks");

    private static final Map<String, Table> TABLES = Map.of(
            "PLACE", new Table("place_translations", "place_id", null),
            "POI", new Table("poi_translations", "poi_id", null),
            "ROUTE", new Table("route_translations", "route_id", null),
            "GUIDE", new Table("guide_translations", "guide_id", null),
            "EVENT", new Table("event_translations", "event_id", null),
            "CATEGORY", new Table("category_translations", "category_id", null),
            ROUTE_STOP, new Table("route_stop_translations", "route_stop_id", "(SELECT p.route_id FROM route_stops p"
                    + " WHERE p.project_id = t.project_id AND p.id = t.route_stop_id)"),
            GUIDE_BLOCK, new Table("guide_block_translations", "guide_block_id", "(SELECT p.guide_id FROM guide_blocks p"
                    + " WHERE p.project_id = t.project_id AND p.id = t.guide_block_id)"),
            ASSET, new Table("asset_translations", "asset_id", null));

    private static final Map<String, WorkingCopyResourceType> PARENTS = Map.of(
            ROUTE_STOP, WorkingCopyResourceType.ROUTE, GUIDE_BLOCK, WorkingCopyResourceType.GUIDE);

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public ProvenanceStore(NamedParameterJdbcTemplate jdbc, EntityManager entityManager, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    /** The {@code 03} section 1 provenance of a machine translation, not yet reviewed. */
    public static Map<String, Object> machineTranslation(String serviceId, String requestId, String sourceLanguage,
                                                         List<Map<String, Object>> citations, Instant generatedAt) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("origin", AI_GENERATED);
        provenance.put("serviceId", serviceId);
        provenance.put("function", "translate");
        provenance.put("requestId", requestId);
        provenance.put("sourceLanguage", sourceLanguage);
        provenance.put("citations", citations);
        provenance.put("generatedAt", generatedAt.truncatedTo(ChronoUnit.MICROS).toString());
        provenance.put("reviewedBySubject", null);
        provenance.put("reviewedAt", null);
        return provenance;
    }

    public static boolean machineGenerated(Map<String, Object> provenance) {
        return provenance != null && AI_GENERATED.equals(provenance.get("origin"));
    }

    public static boolean unreviewed(Map<String, Object> provenance) {
        return machineGenerated(provenance) && provenance.get("reviewedAt") == null;
    }

    /** The provenance with the reviewer set, or unchanged when it is already reviewed. */
    public static Map<String, Object> reviewed(Map<String, Object> provenance, String subject, Instant now) {
        if (!unreviewed(provenance)) {
            return provenance;
        }
        Map<String, Object> reviewed = new LinkedHashMap<>(provenance);
        reviewed.put("reviewedBySubject", subject);
        reviewed.put("reviewedAt", now.truncatedTo(ChronoUnit.MICROS).toString());
        return reviewed;
    }

    /** The live row's provenance; {@code null} when it has none or the language has no row. */
    public Map<String, Object> live(UUID projectId, Subject subject, String language) {
        Table table = table(subject.resourceType());
        List<String> found = jdbc.queryForList("SELECT provenance::text FROM " + table.name()
                + " WHERE project_id = :projectId AND " + table.owner() + " = :resourceId"
                + " AND language_code = :language", params(projectId, subject.resourceId())
                .addValue("language", language), String.class);
        return found.isEmpty() ? null : parse(found.get(0));
    }

    /** Sets the live row's provenance; nothing when the language has no row. */
    public void writeLive(UUID projectId, Subject subject, String language, Map<String, Object> provenance) {
        entityManager.flush();
        Table table = table(subject.resourceType());
        jdbc.update("UPDATE " + table.name() + " SET provenance = CAST(:provenance AS jsonb)"
                + " WHERE project_id = :projectId AND " + table.owner() + " = :resourceId"
                + " AND language_code = :language", params(projectId, subject.resourceId())
                .addValue("language", language).addValue("provenance", json(provenance), Types.VARCHAR));
    }

    /**
     * What the Resource's working copy carries, by key ({@link Subject#key}); empty when it has no copy or carries
     * nothing.
     */
    public Map<String, Map<String, Object>> pending(UUID projectId, WorkingCopyResourceType type, UUID resourceId) {
        List<String> found = jdbc.queryForList("SELECT translation_provenance::text FROM resource_working_copies"
                + " WHERE project_id = :projectId AND resource_type = :type AND resource_id = :resourceId",
                params(projectId, resourceId).addValue("type", type.name()), String.class);
        Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
        if (!found.isEmpty() && found.get(0) != null) {
            parse(found.get(0)).forEach((key, provenance) -> pending.put(key, map(provenance)));
        }
        return pending;
    }

    /** Replaces what the working copy carries, leaving its version alone; nothing when the Resource has no copy. */
    public void writePending(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                             Map<String, Map<String, Object>> pending) {
        entityManager.flush();
        jdbc.update("UPDATE resource_working_copies SET translation_provenance = CAST(:provenance AS jsonb)"
                + " WHERE project_id = :projectId AND resource_type = :type AND resource_id = :resourceId",
                params(projectId, resourceId).addValue("type", type.name())
                        .addValue("provenance", pending.isEmpty() ? null : json(pending), Types.VARCHAR));
    }

    /** Every machine-generated entry of the Project: live rows, then what working copies carry, flagged pending. */
    public List<Entry> machineGenerated(UUID projectId) {
        List<Entry> entries = new ArrayList<>();
        TABLES.forEach((type, table) -> jdbc.query("SELECT t." + table.owner() + " AS resource_id,"
                + (table.parent() == null ? " NULL::uuid" : " " + table.parent()) + " AS parent_id,"
                + " t.language_code, t.provenance::text AS provenance FROM " + table.name() + " t"
                + " WHERE t.project_id = :projectId AND t.provenance ->> 'origin' = '" + AI_GENERATED + "'",
                new MapSqlParameterSource("projectId", projectId), row -> {
                    UUID parentId = row.getObject("parent_id", UUID.class);
                    WorkingCopyResourceType parentType = PARENTS.get(type);
                    entries.add(new Subject(type, row.getObject("resource_id", UUID.class), parentType, parentId)
                            .entry(row.getString("language_code"), parse(row.getString("provenance")), false));
                }));
        jdbc.query("SELECT resource_type, resource_id, translation_provenance::text AS provenance"
                + " FROM resource_working_copies WHERE project_id = :projectId AND translation_provenance IS NOT NULL",
                new MapSqlParameterSource("projectId", projectId), row -> {
                    WorkingCopyResourceType type = WorkingCopyResourceType.valueOf(row.getString("resource_type"));
                    UUID resourceId = row.getObject("resource_id", UUID.class);
                    parse(row.getString("provenance")).forEach((key, value) -> {
                        Map<String, Object> provenance = map(value);
                        Keyed keyed = keyed(type, resourceId, key);
                        if (keyed != null && machineGenerated(provenance)) {
                            entries.add(keyed.subject().entry(keyed.language(), provenance, true));
                        }
                    });
                });
        entries.sort(Comparator.comparing(Entry::resourceType).thenComparing(Entry::resourceId)
                .thenComparing(Entry::languageCode).thenComparing(Entry::pending));
        return entries;
    }

    /**
     * The position of every Route Stop and Guide Block of the Project in its Route or Guide, counted from 0 in the
     * live order, for readiness paths such as {@code stops[i]}.
     */
    public Map<UUID, Integer> childPositions(UUID projectId) {
        Map<UUID, Integer> positions = new HashMap<>();
        MapSqlParameterSource params = new MapSqlParameterSource("projectId", projectId);
        jdbc.query("SELECT id, ROW_NUMBER() OVER (PARTITION BY route_id ORDER BY position) - 1 AS i FROM route_stops"
                + " WHERE project_id = :projectId", params,
                row -> {
                    positions.put(row.getObject("id", UUID.class), row.getInt("i"));
                });
        jdbc.query("SELECT id, ROW_NUMBER() OVER (PARTITION BY guide_id ORDER BY sort_order) - 1 AS i"
                + " FROM guide_blocks WHERE project_id = :projectId", params,
                row -> {
                    positions.put(row.getObject("id", UUID.class), row.getInt("i"));
                });
        return positions;
    }

    /**
     * Apply: what the copy carries becomes the rows' provenance, the Resource's own and its stops' or blocks'. A
     * stop or block the apply recreated has a new id, so what the copy carried for it is lost (GAPS.md G-037).
     */
    @Override
    public void applied(UUID projectId, WorkingCopyResourceType type, UUID resourceId) {
        if (!TABLES.containsKey(type.name())) {
            return;
        }
        pending(projectId, type, resourceId).forEach((key, provenance) -> {
            Keyed keyed = keyed(type, resourceId, key);
            if (keyed != null) {
                writeLive(projectId, keyed.subject(), keyed.language(), provenance);
            }
        });
    }

    private record Keyed(Subject subject, String language) {
    }

    /** A key of a working copy's {@code translation_provenance}; {@code null} when it cannot be read. */
    private static Keyed keyed(WorkingCopyResourceType type, UUID resourceId, String key) {
        String[] parts = key.split("/");
        if (parts.length == 1) {
            return new Keyed(Subject.of(type, resourceId), key);
        }
        String childType = CHILDREN.entrySet().stream().filter(e -> e.getValue().equals(parts[0]))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (parts.length != 3 || childType == null || PARENTS.get(childType) != type) {
            return null;
        }
        try {
            return new Keyed(new Subject(childType, UUID.fromString(parts[1]), type, resourceId), parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Table table(String type) {
        Table table = TABLES.get(type);
        if (table == null) {
            throw new IllegalArgumentException(type + " has no translations");
        }
        return table;
    }

    private static MapSqlParameterSource params(UUID projectId, UUID resourceId) {
        return new MapSqlParameterSource("projectId", projectId).addValue("resourceId", resourceId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> provenance ? (Map<String, Object>) provenance : null;
    }

    private Map<String, Object> parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable provenance", e);
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unwritable provenance", e);
        }
    }
}
