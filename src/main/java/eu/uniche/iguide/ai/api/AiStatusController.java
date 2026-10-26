package eu.uniche.iguide.ai.api;

import eu.uniche.iguide.ai.application.AiGatewayStatus;
import eu.uniche.iguide.ai.application.TranslationGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/ai/status} (DECISIONS.md D-035): which AI functions this deployment offers, so the CMS can grey
 * "Translate with MuseMind" with the reason or label the mock. Any signed-in user; it names no Project.
 */
@RestController
public class AiStatusController {

    /** One entry per AI function; translation is the only one so far. */
    public record AiStatusResponse(AiGatewayStatus translation) {
    }

    private final TranslationGateway translation;

    public AiStatusController(TranslationGateway translation) {
        this.translation = translation;
    }

    @GetMapping("/api/v1/ai/status")
    public AiStatusResponse status() {
        return new AiStatusResponse(translation.status());
    }
}
