package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.RichText;

/** One language of a block: {@code text} for a TEXT block, {@code caption} for a REFERENCE block. */
public record BlockTranslationInput(RichText text, String caption) {
}
