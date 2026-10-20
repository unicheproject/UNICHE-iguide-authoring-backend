package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Points of Interest's share of the translation completeness counts. */
@Component
public class PoiTitleCounter implements TitledResourceCounter {

    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;

    public PoiTitleCounter(PointOfInterestRepository pois, PoiTranslationRepository translations) {
        this.pois = pois;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return pois.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countTitled(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
