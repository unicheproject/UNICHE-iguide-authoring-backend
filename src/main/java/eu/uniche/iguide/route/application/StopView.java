package eu.uniche.iguide.route.application;

import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import java.util.List;

public record StopView(RouteStop stop, List<RouteStopTranslation> translations) {
}
