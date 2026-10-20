package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.ResourceAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.resource.api.OrderRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The media of a Resource ({@code 07} section 14, {@code .../{kind}/{id}/resource-assets}), for
 * {@code pois}, {@code routes}, {@code guides}, and {@code events} (DECISIONS.md D-025).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/{kind}/{resourceId}")
public class ResourceAssetController {

    private static final Map<String, AssetResourceType> KINDS = Map.of("pois", AssetResourceType.POI,
            "routes", AssetResourceType.ROUTE, "guides", AssetResourceType.GUIDE, "events", AssetResourceType.EVENT);

    private final ResourceAssetUseCase bindings;

    public ResourceAssetController(ResourceAssetUseCase bindings) {
        this.bindings = bindings;
    }

    @GetMapping("/resource-assets")
    public List<AssetResponse.Binding> list(@PathVariable UUID projectId, @PathVariable String kind,
                                            @PathVariable UUID resourceId) {
        return bindings.list(projectId, kindOf(kind), resourceId, CurrentPrincipal.require()).stream()
                .map(AssetResponse.Binding::from).toList();
    }

    @PostMapping("/resource-assets")
    public ResponseEntity<AssetResponse.Binding> bind(@PathVariable UUID projectId, @PathVariable String kind,
                                                      @PathVariable UUID resourceId,
                                                      @RequestBody AssetRequests.Bind request) {
        ResourceAsset created = bindings.bind(projectId, kindOf(kind), resourceId, CurrentPrincipal.require(),
                request.assetId(), request.usage(), request.languageCode());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/" + kind + "/" + resourceId
                + "/resource-assets/" + created.getId())).body(AssetResponse.Binding.from(created));
    }

    @DeleteMapping("/resource-assets/{bindingId}")
    public ResponseEntity<Void> unbind(@PathVariable UUID projectId, @PathVariable String kind,
                                       @PathVariable UUID resourceId, @PathVariable UUID bindingId) {
        bindings.unbind(projectId, kindOf(kind), resourceId, bindingId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/resource-assets:order")
    public List<AssetResponse.Binding> reorder(@PathVariable UUID projectId, @PathVariable String kind,
                                               @PathVariable UUID resourceId,
                                               @Valid @RequestBody OrderRequest request) {
        return bindings.reorder(projectId, kindOf(kind), resourceId, CurrentPrincipal.require(), request.ids())
                .stream().map(AssetResponse.Binding::from).toList();
    }

    private static AssetResourceType kindOf(String kind) {
        AssetResourceType type = KINDS.get(kind);
        if (type == null) {
            throw new ResourceNotFoundException("No such endpoint.");
        }
        return type;
    }
}
