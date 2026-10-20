package eu.uniche.iguide.asset.domain;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
 * An Asset ({@code 03} section 4): the metadata of one stored file. The bytes live in object storage under
 * {@code storageKey}, which is never a public URL. The id is assigned before the upload, since the storage
 * key contains it.
 */
@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name", nullable = false)
    private String internalName;

    @Column(nullable = false)
    private boolean decorative;

    @Column(name = "storage_provider", nullable = false)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String checksum;

    private Integer width;

    private Integer height;

    @Column(name = "duration_seconds")
    private BigDecimal durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

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

    /** A wrapper, so a new Asset with its assigned id is still recognized as new and inserted. */
    @Version
    @Column(nullable = false)
    private Long version;

    public static Asset draft(UUID id, UUID projectId, AssetType type, String internalName, StoredFile file,
                              String subject, Instant now) {
        Asset asset = new Asset();
        asset.id = id;
        asset.projectId = projectId;
        asset.type = type;
        asset.status = ResourceStatus.DRAFT;
        asset.internalName = internalName;
        asset.createdAt = now;
        asset.createdBySubject = subject;
        asset.store(file, subject, now);
        return asset;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public void edit(String internalName, boolean decorative, String subject, Instant now) {
        this.internalName = internalName;
        this.decorative = decorative;
        touch(subject, now);
    }

    /** Points the Asset at newly stored bytes, keeping its id, texts, and bindings. */
    public void store(StoredFile file, String subject, Instant now) {
        this.storageProvider = file.provider();
        this.storageKey = file.key();
        this.originalFilename = file.originalFilename();
        this.mimeType = file.mimeType();
        this.sizeBytes = file.sizeBytes();
        this.checksum = file.checksum();
        this.width = file.width();
        this.height = file.height();
        this.durationSeconds = null;
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

    /** What was written to object storage for one upload. */
    public record StoredFile(String provider, String key, String originalFilename, String mimeType, long sizeBytes,
                             String checksum, Integer width, Integer height) {
    }
}
