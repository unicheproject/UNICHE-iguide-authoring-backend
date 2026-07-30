package eu.uniche.iguide.platform.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A domain rule rejected the request. The syntax was fine, so this is {@code 422} rather than
 * {@code 400}.
 */
public class ValidationFailedException extends ApiException {

    public ValidationFailedException(String message, List<ErrorItem> errors) {
        super(message, errors, null);
    }

    public ValidationFailedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String type() {
        return "VALIDATION_ERROR";
    }
}
