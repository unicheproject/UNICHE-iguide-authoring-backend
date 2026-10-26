package eu.uniche.iguide.ai.application;

import eu.uniche.iguide.platform.web.ApiException;
import org.springframework.http.HttpStatus;

/** The AI gateway is not configured for this deployment or cannot be reached (DECISIONS.md D-035). */
public class AiGatewayUnavailableException extends ApiException {

    public AiGatewayUnavailableException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }

    @Override
    public String type() {
        return "AI_GATEWAY_UNAVAILABLE";
    }
}
