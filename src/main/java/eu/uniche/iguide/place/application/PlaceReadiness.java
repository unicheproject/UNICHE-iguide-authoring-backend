package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
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
 * Readiness of ACTIVE Places ({@code 03} section 1, {@code 14} section 2; DECISIONS.md D-033): the title in the default
 * language, translations of the texts the CMS counts, and accessibility information in every enabled language.
 */
@Component
public class PlaceReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("title", "summary", "description", "visitorInformation",
            "accessibilityInformation");

    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;

    public PlaceReadiness(PlaceRepository places, PlaceTranslationRepository translations) {
        this.places = places;
        this.translations = translations;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<UUID> active = places.findByProjectId(context.projectId()).stream()
                .filter(p -> p.getStatus() == ResourceStatus.ACTIVE).map(Place::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndPlaceIdIn(context.projectId(), active), PlaceTranslation::getPlaceId,
                PlaceTranslation::getLanguageCode, t -> Texts.of("title", t.getTitle(), "summary", t.getSummary(),
                        "description", t.getDescription(), "visitorInformation", t.getVisitorInformation(),
                        "accessibilityInformation", t.getAccessibilityInformation()));
        for (UUID id : active) {
            String title = context.index().title("PLACE", id);
            Map<String, Map<String, String>> own = texts.getOrDefault(id, Map.of());
            report.texts(context, "PLACE", id, title, own, FIELDS, "title", List.of("accessibilityInformation"));
            accessibility(context, report, "PLACE", id, title, own);
        }
    }

    /** {@code 14} section 2: accessibility information in every enabled language. */
    public static void accessibility(ReadinessContext context, ReadinessReport report, String type, UUID id,
                                     String title, Map<String, Map<String, String>> texts) {
        for (String language : context.languages()) {
            if (ReadinessReport.blank(texts.getOrDefault(language, Map.of()).get("accessibilityInformation"))) {
                report.warn(type, id, title, language, "translations." + language + ".accessibilityInformation",
                        "MISSING_ACCESSIBILITY_INFORMATION");
            }
        }
    }
}
