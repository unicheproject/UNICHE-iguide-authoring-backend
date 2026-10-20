package eu.uniche.iguide.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An Asset bound to a Resource in a role ({@code 03} section 4). The Resource is a generic reference
 * checked by the service. A null {@code languageCode} applies to every language. {@code sortOrder} orders
 * the bindings of one Resource; within a usage it is the order visitors see.
 */
@Entity
@Table(name = "resource_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceAsset {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false)
    private AssetResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AssetUsage usage;

    @Column(name = "language_code", updatable = false)
    private String languageCode;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    public static ResourceAsset of(UUID projectId, AssetResourceType resourceType, UUID resourceId, UUID assetId,
                                   AssetUsage usage, String languageCode, int sortOrder) {
        ResourceAsset binding = new ResourceAsset();
        binding.projectId = projectId;
        binding.resourceType = resourceType;
        binding.resourceId = resourceId;
        binding.assetId = assetId;
        binding.usage = usage;
        binding.languageCode = languageCode;
        binding.sortOrder = sortOrder;
        return binding;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
