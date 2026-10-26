package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.place.application.PlaceReadiness;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.readiness.application.Texts;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Readiness of ACTIVE Points of Interest ({@code 03} section 1, {@code 14} section 2; DECISIONS.md D-033): the title in
 * the default language, translations of the texts the CMS counts, accessibility information in every language.
 */
@Component
public class PoiReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("title", "summary", "description", "instructions",
            "accessibilityInformation");

    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;

    public PoiReadiness(PointOfInterestRepository pois, PoiTranslationRepository translations) {
        this.pois = pois;
        this.translations = translations;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<UUID> active = pois.findByProjectId(context.projectId()).stream()
                .filter(p -> p.getStatus() == ResourceStatus.ACTIVE).map(PointOfInterest::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndPoiIdIn(context.projectId(), active), PoiTranslation::getPoiId,
                PoiTranslation::getLanguageCode, t -> Texts.of("title", t.getTitle(), "summary", t.getSummary(),
                        "description", t.getDescription(), "instructions", t.getInstructions(),
                        "accessibilityInformation", t.getAccessibilityInformation()));
        for (UUID id : active) {
            String title = context.index().title("POI", id);
            Map<String, Map<String, String>> own = texts.getOrDefault(id, Map.of());
            report.texts(context, "POI", id, title, own, FIELDS, "title", List.of("accessibilityInformation"));
            PlaceReadiness.accessibility(context, report, "POI", id, title, own);
        }
    }
}
