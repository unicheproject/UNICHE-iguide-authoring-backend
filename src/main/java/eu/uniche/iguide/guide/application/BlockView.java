package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import java.util.List;

public record BlockView(GuideBlock block, List<GuideBlockTranslation> translations) {
}
