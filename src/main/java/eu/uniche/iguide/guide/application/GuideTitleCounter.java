package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Guides' share of the translation completeness counts. */
@Component
public class GuideTitleCounter implements TitledResourceCounter {

    private final GuideRepository guides;
    private final GuideTranslationRepository translations;

    public GuideTitleCounter(GuideRepository guides, GuideTranslationRepository translations) {
        this.guides = guides;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return guides.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countTitled(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
