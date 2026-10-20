package eu.uniche.iguide.place.application;

import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Places' share of the translation completeness counts. */
@Component
public class PlaceTitleCounter implements TitledResourceCounter {

    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;

    public PlaceTitleCounter(PlaceRepository places, PlaceTranslationRepository translations) {
        this.places = places;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return places.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countTitled(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
