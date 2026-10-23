package eu.uniche.iguide.poi.api;

import eu.uniche.iguide.asset.application.ResourceAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.category.application.ResourceCategoryUseCase;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.application.PoiRules;
import eu.uniche.iguide.poi.application.UpdatePoiCommand;
import eu.uniche.iguide.poi.application.UpdatePoiUseCase;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE Point of Interest: its update body without {@code version}, plus its Category
 * ({@code categoryIds}, none or one) and its media in order ({@code media}, INLINE excluded). Triggers have their
 * own lifecycle and are not part of it (DECISIONS.md D-027).
 */
@Component
public class PoiWorkingCopyHandler implements WorkingCopyHandler<PoiWorkingCopyHandler.State> {

    public record State(String slug, UUID placeId, String code, String internalName, BigDecimal latitude,
                        BigDecimal longitude, Integer estimatedVisitMinutes, boolean featured,
                        Map<String, PoiTranslationBody> translations, List<UUID> categoryIds,
                        List<ResourceAssetUseCase.Media> media) {
    }

    private final LiveRowLock lock;
    private final PoiRules rules;
    private final UpdatePoiUseCase updatePoi;
    private final ResourceCategoryUseCase categories;
    private final ResourceAssetUseCase media;

    public PoiWorkingCopyHandler(LiveRowLock lock, PoiRules rules, UpdatePoiUseCase updatePoi,
                                 ResourceCategoryUseCase categories, ResourceAssetUseCase media) {
        this.lock = lock;
        this.rules = rules;
        this.updatePoi = updatePoi;
        this.categories = categories;
        this.media = media;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.POI;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        PointOfInterest poi = lock.lock(PointOfInterest.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such point of interest in this project."));
        return new LiveResource(poi.getStatus(), poi.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        updatePoi.write(projectId, rules.requirePoi(projectId, resourceId), principal, new UpdatePoiCommand(
                null, state.slug(), state.placeId(), state.code(), state.internalName(), state.latitude(),
                state.longitude(), state.estimatedVisitMinutes(), state.featured(),
                PoiController.inputs(state.translations())));
        categories.replace(projectId, CategoryScope.POI, resourceId, state.categoryIds());
        media.replace(projectId, AssetResourceType.POI, resourceId, state.media());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return PoiResponse.from(rules.view(rules.requirePoi(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
