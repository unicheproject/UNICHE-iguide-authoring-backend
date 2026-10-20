package eu.uniche.iguide.guide.application;

import java.util.Map;

/**
 * The Guide's own editable fields; its blocks are the {@code .../blocks} child collection. A language
 * absent from {@code translations} keeps its text; a blank field clears it.
 */
public record UpdateGuideCommand(Long version, String slug, String internalName, Integer estimatedReadingMinutes,
                                 boolean featured, Map<String, GuideTranslationInput> translations) {
}
