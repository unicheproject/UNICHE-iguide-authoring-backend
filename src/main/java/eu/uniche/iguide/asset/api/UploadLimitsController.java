package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.AssetLimits;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/upload-limits} ({@code 07} section 14): the limits the backend enforces, so the CMS
 * shows them before an upload starts. Not Project-scoped; a valid token is enough.
 */
@RestController
public class UploadLimitsController {

    private final AssetLimits limits;

    public UploadLimitsController(AssetLimits limits) {
        this.limits = limits;
    }

    @GetMapping("/api/v1/upload-limits")
    public AssetResponse.UploadLimits get() {
        CurrentPrincipal.require();
        return new AssetResponse.UploadLimits(limits.maxUploadBytes(), limits.perType());
    }
}
