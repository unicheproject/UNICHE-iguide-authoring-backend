package eu.uniche.iguide.ai.infrastructure;

import eu.uniche.iguide.ai.application.AiGatewayStatus;
import eu.uniche.iguide.ai.application.TranslationGateway;
import eu.uniche.iguide.ai.application.TranslationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The dev-box stand-in for the UnicheAIGateway ({@code iguide.ai.gateway.mode=mock}; DECISIONS.md D-035). It does not
 * translate: it prefixes each text with {@code [MuseMind mock · el→en]} so nobody mistakes it for real output, and
 * reports itself as {@code musemind-mock}. The startup check refuses this mode under the {@code prod} profile.
 */
@Component
@ConditionalOnProperty(name = "iguide.ai.gateway.mode", havingValue = "mock")
public class MockTranslationGateway implements TranslationGateway {

    static final String SERVICE_ID = "musemind-mock";

    @Override
    public AiGatewayStatus status() {
        return AiGatewayStatus.MOCK;
    }

    @Override
    public TranslationResult translate(String sourceLanguage, String targetLanguage, Map<String, String> texts) {
        String prefix = "[MuseMind mock · " + sourceLanguage + "→" + targetLanguage + "] ";
        Map<String, String> translated = new LinkedHashMap<>();
        texts.forEach((field, text) -> translated.put(field, prefix + text));
        return new TranslationResult(translated, SERVICE_ID, "mock-" + UUID.randomUUID(), List.of());
    }
}
