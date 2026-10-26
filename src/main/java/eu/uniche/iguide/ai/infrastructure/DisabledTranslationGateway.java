package eu.uniche.iguide.ai.infrastructure;

import eu.uniche.iguide.ai.application.AiGatewayStatus;
import eu.uniche.iguide.ai.application.AiGatewayUnavailableException;
import eu.uniche.iguide.ai.application.TranslationGateway;
import eu.uniche.iguide.ai.application.TranslationResult;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default adapter ({@code iguide.ai.gateway.mode=disabled}): no gateway, so every call is {@code 503
 * AI_GATEWAY_UNAVAILABLE} and the CMS greys the action (DECISIONS.md D-035).
 */
@Component
@ConditionalOnProperty(name = "iguide.ai.gateway.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledTranslationGateway implements TranslationGateway {

    @Override
    public AiGatewayStatus status() {
        return AiGatewayStatus.DISABLED;
    }

    @Override
    public TranslationResult translate(String sourceLanguage, String targetLanguage, Map<String, String> texts) {
        throw new AiGatewayUnavailableException("Machine translation is not available in this deployment.");
    }
}
