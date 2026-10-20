package eu.uniche.iguide.guide.api;

import eu.uniche.iguide.guide.application.BlockTranslationInput;
import eu.uniche.iguide.guide.application.GuideTranslationInput;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.domain.RichText;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Request bodies of the Guide endpoints. */
public final class GuideRequests {

    private GuideRequests() {
    }

    /** {@code title} is in the default language; {@code slug} is generated when absent. */
    public record Create(String title, String slug) {
    }

    /** The Guide's own fields; its blocks are the {@code .../blocks} child collection. */
    public record Update(@NotNull Long version, String slug, String internalName, Integer estimatedReadingMinutes,
                         boolean featured, Map<String, GuideTranslationBody> translations) {

        Map<String, GuideTranslationInput> translationInputs() {
            return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> new GuideTranslationInput(e.getValue().title(), e.getValue().summary())));
        }
    }

    public record GuideTranslationBody(String title, String summary) {
    }

    /**
     * A block. {@code blockType} is set on creation and ignored afterwards; {@code resourceType} and
     * {@code resourceId} are for REFERENCE blocks only.
     */
    public record Block(GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                        Map<String, BlockTranslationBody> translations) {

        Map<String, BlockTranslationInput> translationInputs() {
            return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue() == null ? new BlockTranslationInput(null, null)
                            : new BlockTranslationInput(e.getValue().text(), e.getValue().caption())));
        }
    }

    /** {@code text} for a TEXT block, {@code caption} for a REFERENCE block. */
    public record BlockTranslationBody(RichText text, String caption) {
    }
}
