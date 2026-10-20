package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activating a non-decorative image needs its
 * alternative text in the default language ({@code 14}); deactivation and archival are refused with
 * {@code RESOURCE_IN_USE} while non-archived Resources or Floor Plans use the Asset ({@code 08} Milestone 3).
 */
@Service
public class ChangeAssetStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final AssetRepository assets;
    private final TransactionTemplate transactionTemplate;

    public ChangeAssetStatusUseCase(ProjectAccessGateway accessGateway, AssetRules rules, AssetRepository assets,
                                    TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.assets = assets;
        this.transactionTemplate = transactionTemplate;
    }

    public AssetView execute(UUID projectId, UUID assetId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Asset asset = rules.requireAsset(projectId, assetId);
            ResourceRules.requireCurrentVersion(asset.getVersion(), version, "asset");
            ResourceRules.requireTransition(asset.getStatus(), target, "asset");
            if (target == ResourceStatus.ACTIVE) {
                requireAlternativeText(asset.getType(), asset.isDecorative(), rules.defaultLanguage(projectId),
                        rules.translationsOf(asset));
            }
            if (asset.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = rules.referencesTo(projectId, assetId);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The asset is still used.", references);
                }
            }
            asset.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(assets.saveAndFlush(asset));
        });
    }

    /** A non-decorative {@code IMAGE} that visitors may see needs alternative text in the default language. */
    static void requireAlternativeText(AssetType type, boolean decorative, String defaultLanguage,
                                       Collection<AssetTranslation> translations) {
        if (type != AssetType.IMAGE || decorative) {
            return;
        }
        boolean described = translations.stream()
                .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasAlternativeText());
        if (!described) {
            throw new ValidationFailedException("The image needs a text alternative.", List.of(new ErrorItem(
                    "translations." + defaultLanguage + ".alternativeText", "MISSING_REQUIRED_FIELDS",
                    "Is required in the default language unless the image is decorative.")));
        }
    }
}
