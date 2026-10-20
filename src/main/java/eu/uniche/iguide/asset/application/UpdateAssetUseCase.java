package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.asset.infrastructure.AssetTranslationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves an Asset's name, decorative flag, and texts. Unlike other Resources, an ACTIVE Asset is edited in
 * place (DECISIONS.md D-025): it is typically used by ACTIVE content, which blocks its deactivation, so a
 * DRAFT-only rule would leave a missing alternative text impossible to fix until working copies exist. Only
 * an ARCHIVED Asset is read-only. An ACTIVE, non-decorative image keeps its default-language alternative text.
 */
@Service
public class UpdateAssetUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final AssetRepository assets;
    private final AssetTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdateAssetUseCase(ProjectAccessGateway accessGateway, AssetRules rules, AssetRepository assets,
                              AssetTranslationRepository translations, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.assets = assets;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public AssetView execute(UUID projectId, UUID assetId, UnichePrincipal principal, Long version, String rawName,
                             boolean decorative, Map<String, AssetTranslationInput> requested) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Asset asset = rules.requireAsset(projectId, assetId);
            ResourceRules.requireCurrentVersion(asset.getVersion(), version, "asset");
            if (asset.getStatus() == ResourceStatus.ARCHIVED) {
                throw new ValidationFailedException("An archived asset cannot be edited.", List.of(new ErrorItem(
                        "status", "RESOURCE_NOT_EDITABLE", "Restore the asset before editing it.")));
            }
            Map<String, AssetTranslationInput> texts = requested == null ? Map.of() : requested;
            List<ErrorItem> errors = new ArrayList<>();
            String name = ResourceRules.requireName(rawName, "internalName", errors);
            rules.checkLanguages(projectId, texts.keySet(), errors);
            texts.forEach((code, input) -> checkTexts(code, input, errors));
            ResourceRules.throwIfAny(errors, "The asset is invalid.");

            Map<String, AssetTranslation> existing = translations.findByProjectIdAndAssetId(projectId, assetId).stream()
                    .collect(Collectors.toMap(AssetTranslation::getLanguageCode, Function.identity()));
            List<AssetTranslation> toSave = new ArrayList<>();
            texts.forEach((code, input) -> {
                AssetTranslation row = existing.getOrDefault(code, AssetTranslation.of(projectId, assetId, code));
                AssetTranslationInput t = input == null ? new AssetTranslationInput(null, null, null, null, null) : input;
                row.write(ResourceRules.trimToNull(t.title()), ResourceRules.trimToNull(t.caption()),
                        ResourceRules.trimToNull(t.alternativeText()), ResourceRules.trimToNull(t.credits()),
                        ResourceRules.trimToNull(t.transcript()));
                existing.put(code, row);
                toSave.add(row);
            });
            if (asset.getStatus() == ResourceStatus.ACTIVE) {
                ChangeAssetStatusUseCase.requireAlternativeText(asset.getType(), decorative,
                        rules.defaultLanguage(projectId), existing.values());
            }
            translations.saveAll(toSave);
            asset.edit(name, decorative, principal.subject(), Instant.now());
            return rules.view(assets.saveAndFlush(asset));
        });
    }

    private static void checkTexts(String code, AssetTranslationInput input, List<ErrorItem> errors) {
        if (input == null) {
            return;
        }
        String path = "translations." + code + ".";
        checkLength(input.title(), path + "title", AssetRules.MAX_TEXT, errors);
        checkLength(input.caption(), path + "caption", AssetRules.MAX_TEXT, errors);
        checkLength(input.alternativeText(), path + "alternativeText", AssetRules.MAX_TEXT, errors);
        checkLength(input.credits(), path + "credits", AssetRules.MAX_TEXT, errors);
        checkLength(input.transcript(), path + "transcript", AssetRules.MAX_TRANSCRIPT, errors);
    }

    private static void checkLength(String value, String path, int max, List<ErrorItem> errors) {
        if (value != null && value.strip().length() > max) {
            errors.add(new ErrorItem(path, "TOO_LONG", "At most " + max + " characters."));
        }
    }
}
