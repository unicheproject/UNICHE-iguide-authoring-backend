package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Assets, newest first ({@code 06} section 16), filtered by status, type, and
 * {@code q}, a case-insensitive match on the name, the file name, or the title in any language ({@code 12}).
 */
@Service
public class ListAssetsQuery {

    private final ProjectAccessGateway accessGateway;
    private final AssetRepository assets;
    private final AssetRules rules;

    public ListAssetsQuery(ProjectAccessGateway accessGateway, AssetRepository assets, AssetRules rules) {
        this.accessGateway = accessGateway;
        this.assets = assets;
        this.rules = rules;
    }

    public Page<AssetView> execute(UUID projectId, UnichePrincipal principal, ResourceStatus status, AssetType type,
                                   String q, Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<Asset> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> titled = query.subquery(UUID.class);
                var t = titled.from(AssetTranslation.class);
                titled.select(t.get("assetId")).where(
                        cb.equal(t.get("projectId"), projectId),
                        cb.equal(t.get("assetId"), root.get("id")),
                        cb.like(cb.lower(t.get("title")), pattern, '\\'));
                return cb.or(cb.like(cb.lower(root.get("internalName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("originalFilename")), pattern, '\\'), cb.exists(titled));
            });
        }
        Page<Asset> result = assets.findAll(spec, PageRequests.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        return new PageImpl<>(rules.views(projectId, result.getContent()), result.getPageable(),
                result.getTotalElements());
    }
}
