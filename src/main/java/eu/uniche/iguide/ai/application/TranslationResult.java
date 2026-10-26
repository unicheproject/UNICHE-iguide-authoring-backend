package eu.uniche.iguide.ai.application;

import java.util.List;
import java.util.Map;

/**
 * What the gateway returns for one translation: the texts by field, and what the provenance records about the call
 * ({@code 03} section 1: {@code serviceId}, {@code requestId}, {@code citations}).
 */
public record TranslationResult(Map<String, String> texts, String serviceId, String requestId,
                                List<Map<String, Object>> citations) {

    public TranslationResult {
        texts = Map.copyOf(texts);
        citations = List.copyOf(citations);
    }
}
