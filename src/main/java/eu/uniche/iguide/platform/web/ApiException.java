package eu.uniche.iguide.platform.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class for exceptions that carry their own place in the status mapping. Anything reaching the
 * handler that is not one of these is a defect and is reported as {@code 500}.
 */
public abstract class ApiException extends RuntimeException {

    private final transient List<ErrorItem> errors;

    protected ApiException(String message) {
        this(message, List.of(), null);
    }

    protected ApiException(String message, Throwable cause) {
        this(message, List.of(), cause);
    }

    protected ApiException(String message, List<ErrorItem> errors, Throwable cause) {
        super(message, cause);
        this.errors = List.copyOf(errors);
    }

    public abstract HttpStatus status();

    /** Stable machine-readable classification placed in {@link ErrorEnvelope#type()}. */
    public abstract String type();

    public List<ErrorItem> errors() {
        return errors;
    }
}
