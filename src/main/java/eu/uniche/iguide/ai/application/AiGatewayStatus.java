package eu.uniche.iguide.ai.application;

/**
 * The state of an AI function as {@code GET /api/v1/ai/status} reports it (DECISIONS.md D-035): {@code DISABLED} (no
 * gateway configured), {@code MOCK} (the dev-box stand-in, never in production), {@code AVAILABLE} (a real gateway).
 */
public enum AiGatewayStatus {
    DISABLED,
    MOCK,
    AVAILABLE
}
