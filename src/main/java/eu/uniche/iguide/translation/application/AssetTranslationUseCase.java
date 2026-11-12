package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.asset.application.AssetRules;
import eu.uniche.iguide.asset.application.AssetTranslationInput;
import eu.uniche.iguide.asset.application.AssetView;
import eu.uniche.iguide.asset.application.UpdateAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT} and {@code DELETE .../assets/{id}/translations/{lang}} written by a person ({@code 07} section 14,
 * DECISIONS.md D-032): a change to the language's text counts as reviewing an unreviewed machine translation of it,
 * and emptying the language drops its provenance ({@code 03} section 1, D-036). Assets are edited in place.
 */
@Service
public class AssetTranslationUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules assets;
    private final UpdateAssetUseCase updateAsset;
    private final TranslationReview review;
    private final TransactionTemplate transactionTemplate;

    public AssetTranslationUseCase(ProjectAccessGateway accessGateway, AssetRules assets,
                                   UpdateAssetUseCase updateAsset, TranslationReview review,
                                   TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.assets = assets;
        this.updateAsset = updateAsset;
        this.review = review;
        this.transactionTemplate = transactionTemplate;
    }

    /** {@code texts} {@code null} empties the language. */
    public AssetView put(UUID projectId, UUID assetId, UnichePrincipal principal, Long version, String language,
                         AssetTranslationInput texts) {
        accessGateway.requireAccess(projectId, principal);
        List<String> before = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return texts(assets.view(assets.requireAsset(projectId, assetId)), language);
        });
        AssetView written = updateAsset.translate(projectId, assetId, principal, version, language, texts);
        ProvenanceStore.Subject subject = ProvenanceStore.Subject.asset(assetId);
        if (texts == null) {
            review.cleared(projectId, subject, language, false);
        } else if (!Objects.equals(before, texts(written, language))) {
            review.edited(projectId, subject, language, principal.subject(), false);
        }
        return written;
    }

    /** The language's texts, trimmed, blank as absent. */
    private static List<String> texts(AssetView view, String language) {
        AssetTranslation row = view.translations().stream().filter(t -> language.equals(t.getLanguageCode()))
                .findFirst().orElse(null);
        if (row == null) {
            return Arrays.asList(null, null, null, null, null);
        }
        return Arrays.asList(trim(row.getTitle()), trim(row.getCaption()), trim(row.getAlternativeText()),
                trim(row.getCredits()), trim(row.getTranscript()));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
