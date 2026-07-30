package eu.uniche.iguide.platform.web;

/**
 * One field-level error.
 *
 * <p>The rejected value is deliberately absent. The default framework representation of a field
 * error includes it, which would leak the contents of any sensitive field that later acquires a
 * validation constraint.
 *
 * @param path    location within the request body, for example
 *                {@code components[2].configuration.categoryId}
 * @param code    stable machine-readable code the frontend may switch on
 * @param message human-readable explanation, carrying no rejected value
 */
public record ErrorItem(String path, String code, String message) {
}
