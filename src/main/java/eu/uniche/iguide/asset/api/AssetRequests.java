package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.AssetTranslationInput;
import eu.uniche.iguide.asset.domain.AssetUsage;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Request bodies of the Asset endpoints; uploads are multipart and have no body record. */
public final class AssetRequests {

    private AssetRequests() {
    }

    /** {@code translations} are written per language; languages left out keep their texts. */
    public record Update(@NotNull Long version, String internalName, boolean decorative,
                         Map<String, TranslationBody> translations) {

        Map<String, AssetTranslationInput> translationInputs() {
            Map<String, AssetTranslationInput> inputs = new LinkedHashMap<>();
            if (translations != null) {
                translations.forEach((code, t) -> inputs.put(code, t == null ? null : t.toInput()));
            }
            return inputs;
        }
    }

    public record TranslationBody(String title, String caption, String alternativeText, String credits,
                                  String transcript) {

        AssetTranslationInput toInput() {
            return new AssetTranslationInput(title, caption, alternativeText, credits, transcript);
        }
    }

    /** Binds an Asset to a Resource; a null {@code languageCode} applies to every language. */
    public record Bind(UUID assetId, AssetUsage usage, String languageCode) {
    }
}
