package eu.uniche.iguide.ai.application;

import java.util.Map;

/**
 * The port to the UnicheAIGateway's translation function ({@code 12} section 3, ARC_4.6; DECISIONS.md D-035). The
 * backend calls it, never the CMS (AGENTS.md section 4 rule 3). The gateway's API is not specified yet ({@code 09}
 * section 16, {@code 08} Milestone 13), so the only adapters are a labelled mock for the dev box and a disabled one.
 */
public interface TranslationGateway {

    /** What the CMS is told about the translation function: whether to offer it, and whether it is the mock. */
    AiGatewayStatus status();

    /**
     * Translates every text of {@code texts} (field name to text) from one language to another; the result has the
     * same keys. {@link AiGatewayUnavailableException} when the gateway is disabled or cannot be reached.
     */
    TranslationResult translate(String sourceLanguage, String targetLanguage, Map<String, String> texts);
}
