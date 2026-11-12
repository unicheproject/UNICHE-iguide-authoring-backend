package eu.uniche.iguide.template.application;

/**
 * A Template or style preset file cannot be seeded: it is invalid, or it changes a version a Project was already
 * initialized from ({@code 04} sections 5 and 15). Thrown at startup, which it stops.
 */
public class TemplateSeedingException extends IllegalStateException {

    public TemplateSeedingException(String message, Throwable cause) {
        super(message, cause);
    }
}
