package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.ai.application.AiGatewayStatus;
import eu.uniche.iguide.ai.application.AiGatewayUnavailableException;
import eu.uniche.iguide.ai.application.TranslationGateway;
import eu.uniche.iguide.ai.application.TranslationResult;
import eu.uniche.iguide.asset.application.AssetRules;
import eu.uniche.iguide.asset.application.AssetTranslationInput;
import eu.uniche.iguide.asset.application.AssetView;
import eu.uniche.iguide.asset.application.UpdateAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.translation.application.ProvenanceStore.Subject;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandlers;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "Translate with MuseMind" and "mark reviewed" in the Translation workspace ({@code 12} section 3, {@code 03}
 * section 1; DECISIONS.md D-035, D-036).
 *
 * <p>{@code :translate} sends the default language's texts as the workspace shows them (the Resource's working
 * copy's when it has one) to the AI gateway, writes the result into the target language like a person's
 * {@code PUT .../translations/{lang}} would (D-031, D-032: a DRAFT in place, an ACTIVE Resource into its working
 * copy, an Asset in place), and records {@code AI_GENERATED} provenance, unreviewed. Texts the default language lacks
 * keep the target's own. It covers a Resource's own plain texts, a Route Stop's, a Guide Block's caption and rich
 * text (node by node, {@link RichTextFields}), and an Asset's.
 */
@Service
public class MachineTranslationUseCase {

    private static final String TEXT = "text";
    private static final String TEXT_PREFIX = "text.";

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final ResourceTranslationUseCase translations;
    private final TranslationReview review;
    private final TranslationGateway gateway;
    private final AssetRules assets;
    private final UpdateAssetUseCase updateAsset;
    private final TransactionTemplate transactionTemplate;

    public MachineTranslationUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                     WorkingCopyHandlers handlers, ResourceWorkingCopyRepository copies,
                                     ResourceTranslationUseCase translations, TranslationReview review,
                                     TranslationGateway gateway, AssetRules assets, UpdateAssetUseCase updateAsset,
                                     TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.handlers = handlers;
        this.copies = copies;
        this.translations = translations;
        this.review = review;
        this.gateway = gateway;
        this.assets = assets;
        this.updateAsset = updateAsset;
        this.transactionTemplate = transactionTemplate;
    }

    public ResourceTranslationUseCase.Result translate(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                                       UnichePrincipal principal, String language, Long version,
                                                       Long workingCopyVersion) {
        accessGateway.requireAccess(projectId, principal);
        String source = requireTarget(projectId, language);
        requireGateway();
        Map<String, Object> texts = ResourceTranslationUseCase.translations(state(projectId, type, resourceId));
        Map<String, String> sourceTexts = plainTexts(language(texts, source));
        requireSomething(sourceTexts, "translations." + source);

        TranslationResult result = gateway.translate(source, language, sourceTexts);
        Map<String, Object> fields = new HashMap<>(language(texts, language));
        fields.putAll(result.texts());
        ResourceTranslationUseCase.Result written = translations.putGenerated(projectId, type, resourceId, principal,
                language, version, workingCopyVersion, fields);
        record(projectId, Subject.of(type, resourceId), language, source, result, written.workingCopyVersion() != null);
        return written;
    }

    /**
     * A Route Stop's ({@code children} {@code "stops"}) or Guide Block's ({@code "blocks"}) texts, through its Route or
     * Guide: the versions and the response are the parent's, as for {@code PUT .../translations/{lang}} (D-032).
     */
    public ResourceTranslationUseCase.Result translateChild(UUID projectId, WorkingCopyResourceType type,
                                                            UUID resourceId, String children, UUID childId,
                                                            UnichePrincipal principal, String language, Long version,
                                                            Long workingCopyVersion) {
        accessGateway.requireAccess(projectId, principal);
        String source = requireTarget(projectId, language);
        requireGateway();
        Map<String, Object> state = state(projectId, type, resourceId);
        int index = indexOf(state, children, childId);
        Map<String, Object> texts = ResourceTranslationUseCase.translations(
                ResourceTranslationUseCase.child(state, children, childId));
        Map<String, Object> sourceFields = language(texts, source);
        Map<String, String> sourceTexts = plainTexts(sourceFields);
        sourceTexts.putAll(RichTextFields.flatten(sourceFields.get(TEXT), TEXT_PREFIX));
        requireSomething(sourceTexts, children + "[" + index + "].translations." + source);

        TranslationResult result = gateway.translate(source, language, sourceTexts);
        Map<String, Object> fields = new HashMap<>(language(texts, language));
        result.texts().forEach((field, text) -> {
            if (!field.startsWith(TEXT_PREFIX)) {
                fields.put(field, text);
            }
        });
        if (RichTextFields.isDocument(sourceFields.get(TEXT))
                && !RichTextFields.flatten(sourceFields.get(TEXT), TEXT_PREFIX).isEmpty()) {
            fields.put(TEXT, RichTextFields.rebuild(sourceFields.get(TEXT), TEXT_PREFIX, result.texts()));
        }
        ResourceTranslationUseCase.Result written = translations.putChildGenerated(projectId, type, resourceId,
                children, childId, principal, language, version, workingCopyVersion, fields);
        record(projectId, ResourceTranslationUseCase.childSubject(type, resourceId, childId), language, source, result,
                written.workingCopyVersion() != null);
        return written;
    }

    /** An Asset's texts, in place under the Asset's version (no working copy, D-032). */
    public AssetView translateAsset(UUID projectId, UUID assetId, UnichePrincipal principal, String language,
                                    Long version) {
        accessGateway.requireAccess(projectId, principal);
        String source = requireTarget(projectId, language);
        requireGateway();
        AssetView view = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return assets.view(assets.requireAsset(projectId, assetId));
        });
        Map<String, String> sourceTexts = plainTexts(assetTexts(view, source));
        requireSomething(sourceTexts, "translations." + source);

        TranslationResult result = gateway.translate(source, language, sourceTexts);
        Map<String, Object> fields = new HashMap<>(assetTexts(view, language));
        fields.putAll(result.texts());
        AssetView written = updateAsset.translate(projectId, assetId, principal, version, language,
                new AssetTranslationInput(string(fields.get("title")), string(fields.get("caption")),
                        string(fields.get("alternativeText")), string(fields.get("credits")),
                        string(fields.get("transcript"))));
        record(projectId, Subject.asset(assetId), language, source, result, false);
        return written;
    }

    /** 404 when the language shows no machine translation, reviewed or not. */
    public ProvenanceStore.Entry markReviewed(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                              UnichePrincipal principal, String language) {
        accessGateway.requireAccess(projectId, principal);
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        transactionTemplate.executeWithoutResult(status -> handler.lock(projectId, resourceId));
        return reviewed(projectId, Subject.of(type, resourceId), language, principal);
    }

    /** As {@link #markReviewed}, for a stop or block the Route or Guide shows (its working copy's when it has one). */
    public ProvenanceStore.Entry markChildReviewed(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                                   String children, UUID childId, UnichePrincipal principal,
                                                   String language) {
        accessGateway.requireAccess(projectId, principal);
        indexOf(state(projectId, type, resourceId), children, childId);
        return reviewed(projectId, ResourceTranslationUseCase.childSubject(type, resourceId, childId), language,
                principal);
    }

    public ProvenanceStore.Entry markAssetReviewed(UUID projectId, UUID assetId, UnichePrincipal principal,
                                                   String language) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> assets.requireAsset(projectId, assetId));
        return reviewed(projectId, Subject.asset(assetId), language, principal);
    }

    private ProvenanceStore.Entry reviewed(UUID projectId, Subject texts, String language, UnichePrincipal principal) {
        ProvenanceStore.Entry entry = review.markReviewed(projectId, texts, language, principal.subject());
        if (entry == null) {
            throw new ResourceNotFoundException("This language has no machine translation to review.");
        }
        return entry;
    }

    private void record(UUID projectId, Subject texts, String language, String source, TranslationResult result,
                        boolean pending) {
        review.generated(projectId, texts, language, ProvenanceStore.machineTranslation(result.serviceId(),
                result.requestId(), source, result.citations(), Instant.now()), pending);
    }

    private void requireGateway() {
        if (gateway.status() == AiGatewayStatus.DISABLED) {
            throw new AiGatewayUnavailableException("Machine translation is not available in this deployment.");
        }
    }

    private static void requireSomething(Map<String, String> sourceTexts, String path) {
        if (sourceTexts.isEmpty()) {
            throw new ValidationFailedException("The default language has no text to translate.",
                    List.of(new ErrorItem(path, "NOTHING_TO_TRANSLATE", "Write the texts in the default language first.")));
        }
    }

    /** The source language: the Project's default; the target must be another of its languages. */
    private String requireTarget(UUID projectId, String language) {
        if (language == null || !languages.enabledCodes(projectId).contains(language)) {
            throw new ValidationFailedException("The language is not one of this project.",
                    List.of(new ErrorItem("language", "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
        }
        String source = languages.requireDefaultLanguage(projectId);
        if (language.equals(source)) {
            throw new ValidationFailedException("The default language is the source of a translation.",
                    List.of(new ErrorItem("language", "DEFAULT_LANGUAGE", "Choose another language of the project.")));
        }
        return source;
    }

    /** The Resource's state as the workspace shows it: its working copy's, else the live one. */
    private Map<String, Object> state(UUID projectId, WorkingCopyResourceType type, UUID resourceId) {
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            handler.lock(projectId, resourceId);
            ResourceWorkingCopy copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                    .orElse(null);
            return copy == null ? handlers.liveState(handler, projectId, resourceId)
                    : new LinkedHashMap<>(copy.getPayload());
        });
    }

    /** The child's index in the state's list; 404 when the Resource shows no child with that id. */
    private static int indexOf(Map<String, Object> state, String children, UUID childId) {
        List<?> items = state.get(children) instanceof List<?> list ? list : List.of();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Map<?, ?> item && childId.toString().equals(String.valueOf(item.get("id")))) {
                return i;
            }
        }
        throw new ResourceNotFoundException("No such " + ("stops".equals(children) ? "stop" : "block")
                + " in this " + ("stops".equals(children) ? "route" : "guide") + ".");
    }

    private static Map<String, Object> assetTexts(AssetView view, String language) {
        Map<String, Object> texts = new HashMap<>();
        view.translations().stream().filter(t -> language.equals(t.getLanguageCode())).findFirst()
                .ifPresent((AssetTranslation t) -> {
                    texts.put("title", t.getTitle());
                    texts.put("caption", t.getCaption());
                    texts.put("alternativeText", t.getAlternativeText());
                    texts.put("credits", t.getCredits());
                    texts.put("transcript", t.getTranscript());
                });
        return texts;
    }

    /** The non-blank plain-text fields. */
    private static Map<String, String> plainTexts(Map<String, Object> fields) {
        Map<String, String> texts = new LinkedHashMap<>();
        fields.forEach((field, value) -> {
            if (value instanceof String text && !text.isBlank()) {
                texts.put(field, text);
            }
        });
        return texts;
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> language(Map<String, Object> texts, String language) {
        return texts.get(language) instanceof Map<?, ?> fields ? (Map<String, Object>) fields : Map.of();
    }
}
