package eu.uniche.iguide.platform.web;

import org.springframework.http.HttpStatus;

/**
 * The request collided with the current state: a duplicate slug upstream, or a concurrent edit of the
 * same aggregate. The body carries enough for the frontend to reload and reconcile.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "CONFLICT";
    }
}
