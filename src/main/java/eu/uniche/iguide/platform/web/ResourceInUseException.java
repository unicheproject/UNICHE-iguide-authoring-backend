package eu.uniche.iguide.platform.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Deactivating or archiving a Resource that other rows still reference ({@code 03} section 1). The body
 * lists the references in the shape {@code GET .../references} returns.
 */
public class ResourceInUseException extends ApiException {

    private final transient List<?> references;

    public ResourceInUseException(String message, List<?> references) {
        super(message);
        this.references = List.copyOf(references);
    }

    public List<?> references() {
        return references;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "RESOURCE_IN_USE";
    }
}
