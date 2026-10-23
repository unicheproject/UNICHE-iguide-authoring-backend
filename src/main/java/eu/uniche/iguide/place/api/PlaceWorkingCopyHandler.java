package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.application.PlaceRules;
import eu.uniche.iguide.place.application.PlaceTranslationInput;
import eu.uniche.iguide.place.application.UpdatePlaceCommand;
import eu.uniche.iguide.place.application.UpdatePlaceUseCase;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceType;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The working copy of an ACTIVE Place: the Place's update body without {@code version} (DECISIONS.md D-027). */
@Component
public class PlaceWorkingCopyHandler implements WorkingCopyHandler<PlaceWorkingCopyHandler.State> {

    public record State(String slug, PlaceType type, PlaceEnvironment environment, UUID parentPlaceId,
                        String internalName, BigDecimal latitude, BigDecimal longitude, boolean featured,
                        Map<String, PlaceTranslationBody> translations) {
    }

    private final LiveRowLock lock;
    private final PlaceRules rules;
    private final UpdatePlaceUseCase updatePlace;

    public PlaceWorkingCopyHandler(LiveRowLock lock, PlaceRules rules, UpdatePlaceUseCase updatePlace) {
        this.lock = lock;
        this.rules = rules;
        this.updatePlace = updatePlace;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.PLACE;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        Place place = lock.lock(Place.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such place in this project."));
        return new LiveResource(place.getStatus(), place.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        Map<String, PlaceTranslationInput> translations = PlaceController.inputs(state.translations());
        updatePlace.write(projectId, rules.requirePlace(projectId, resourceId), principal, new UpdatePlaceCommand(
                null, state.slug(), state.type(), state.environment(), state.parentPlaceId(), state.internalName(),
                state.latitude(), state.longitude(), state.featured(), translations));
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return PlaceResponse.from(rules.view(rules.requirePlace(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
