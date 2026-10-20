package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.AssetUsage;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.asset.infrastructure.ResourceAssetRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The media of a Point of Interest, Route, Guide, or Event ({@code 07} section 14:
 * {@code .../{kind}/{id}/resource-assets}, {@code .../{bindingId}}, {@code .../resource-assets:order}).
 * Only a DRAFT Resource changes its media until working copies exist. {@code INLINE} rows follow the rich
 * text and are never written here.
 */
@Service
public class ResourceAssetUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final ResourceAssetRepository bindings;
    private final TransactionTemplate transactionTemplate;

    public ResourceAssetUseCase(ProjectAccessGateway accessGateway, AssetRules rules, ResourceAssetRepository bindings,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.bindings = bindings;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ResourceAsset> list(UUID projectId, AssetResourceType kind, UUID resourceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireResource(projectId, kind, resourceId);
        return bindingsOf(projectId, kind, resourceId);
    }

    /**
     * Binds an Asset: of a type the usage accepts, not archived, in a language of the Project or for every
     * language; one Asset per single usage and language; a {@code MODEL} after its {@code MODEL_POSTER}.
     */
    public ResourceAsset bind(UUID projectId, AssetResourceType kind, UUID resourceId, UnichePrincipal principal,
                              UUID assetId, AssetUsage usage, String rawLanguage) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            ResourceRules.requireDraft(rules.requireResource(projectId, kind, resourceId), kind.label());
            List<ErrorItem> errors = new ArrayList<>();
            if (usage == null) {
                errors.add(new ErrorItem("usage", "REQUIRED", "Choose a usage."));
            } else if (usage == AssetUsage.INLINE) {
                errors.add(new ErrorItem("usage", "NOT_ALLOWED", "INLINE media follows the images in rich text."));
            }
            String language = ResourceRules.trimToNull(rawLanguage);
            if (language != null && !rules.enabledLanguages(projectId).contains(language)) {
                errors.add(new ErrorItem("languageCode", "UNKNOWN_LANGUAGE", "Is not a language of this project."));
            }
            rules.checkBindable(projectId, assetId, usage == AssetUsage.INLINE ? null : usage, "assetId", errors);
            ResourceRules.throwIfAny(errors, "The asset cannot be bound.");

            List<ResourceAsset> current = bindingsOf(projectId, kind, resourceId);
            boolean taken = current.stream().anyMatch(b -> b.getUsage() == usage
                    && Objects.equals(b.getLanguageCode(), language)
                    && (usage.isSingle() || b.getAssetId().equals(assetId)));
            if (taken) {
                throw new DuplicateValueException("usage", usage.isSingle()
                        ? "The " + kind.label() + " already has a " + usage + (language == null ? "" : " in " + language)
                        + "; remove it first."
                        : "The asset is already in this " + usage + (language == null ? "" : " in " + language) + ".");
            }
            if (usage == AssetUsage.MODEL && current.stream().noneMatch(b -> b.getUsage() == AssetUsage.MODEL_POSTER
                    && (b.getLanguageCode() == null || Objects.equals(b.getLanguageCode(), language)))) {
                throw new ValidationFailedException("A 3D model needs its poster image.", List.of(new ErrorItem(
                        "usage", "MODEL_POSTER_REQUIRED", "Bind a MODEL_POSTER image before the MODEL.")));
            }
            return bindings.saveAndFlush(ResourceAsset.of(projectId, kind, resourceId, assetId, usage, language,
                    bindings.findMaxSortOrder(projectId, kind, resourceId) + 1));
        });
    }

    public void unbind(UUID projectId, AssetResourceType kind, UUID resourceId, UUID bindingId,
                       UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            ResourceRules.requireDraft(rules.requireResource(projectId, kind, resourceId), kind.label());
            ResourceAsset binding = bindings.findByProjectIdAndResourceTypeAndResourceIdAndId(projectId, kind,
                    resourceId, bindingId).orElseThrow(() -> new ResourceNotFoundException("No such media binding."));
            if (binding.getUsage() == AssetUsage.INLINE) {
                throw new ValidationFailedException("INLINE media follows the rich text.", List.of(new ErrorItem(
                        "usage", "NOT_ALLOWED", "Remove the image from the text instead.")));
            }
            if (binding.getUsage() == AssetUsage.MODEL_POSTER) {
                List<ResourceAsset> rest = bindingsOf(projectId, kind, resourceId).stream()
                        .filter(b -> !b.getId().equals(bindingId)).toList();
                boolean orphaned = rest.stream().anyMatch(model -> model.getUsage() == AssetUsage.MODEL
                        && rest.stream().noneMatch(p -> p.getUsage() == AssetUsage.MODEL_POSTER
                        && (p.getLanguageCode() == null || Objects.equals(p.getLanguageCode(), model.getLanguageCode()))));
                if (orphaned) {
                    throw new ValidationFailedException("A 3D model needs its poster image.", List.of(new ErrorItem(
                            "usage", "MODEL_POSTER_REQUIRED", "Remove the MODEL before its poster.")));
                }
            }
            bindings.delete(binding);
        });
    }

    /**
     * {@code PUT .../resource-assets:order}: every binding id of the Resource except the INLINE ones, which
     * follow the text and stay after the others, in the new order.
     */
    public List<ResourceAsset> reorder(UUID projectId, AssetResourceType kind, UUID resourceId,
                                       UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            ResourceRules.requireDraft(rules.requireResource(projectId, kind, resourceId), kind.label());
            List<ResourceAsset> current = bindingsOf(projectId, kind, resourceId);
            Map<UUID, ResourceAsset> ordered = current.stream().filter(b -> b.getUsage() != AssetUsage.INLINE)
                    .collect(Collectors.toMap(ResourceAsset::getId, Function.identity()));
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != ordered.size() || !new HashSet<>(ids).equals(ordered.keySet())) {
                throw new ValidationFailedException("The order must list every media binding exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER",
                                "List every binding of this " + kind.label() + " except INLINE ones exactly once.")));
            }
            int position = 0;
            for (UUID id : ids) {
                ordered.get(id).moveTo(position++);
            }
            for (ResourceAsset inline : current) {
                if (inline.getUsage() == AssetUsage.INLINE) {
                    inline.moveTo(position++);
                }
            }
            bindings.saveAllAndFlush(current);
            return bindingsOf(projectId, kind, resourceId);
        });
    }

    private List<ResourceAsset> bindingsOf(UUID projectId, AssetResourceType kind, UUID resourceId) {
        return bindings.findByProjectIdAndResourceTypeAndResourceIdOrderBySortOrderAsc(projectId, kind, resourceId);
    }
}
