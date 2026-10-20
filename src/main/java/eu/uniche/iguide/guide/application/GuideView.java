package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import java.util.List;

/** A Guide with its translations, its blocks in order, and its media bindings, INLINE ones included. */
public record GuideView(Guide guide, List<GuideTranslation> translations, List<BlockView> blocks,
                        List<ResourceAsset> media) {
}
