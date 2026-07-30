package eu.uniche.iguide.platform.web;

import org.springframework.http.HttpStatus;

/**
 * The resource does not exist, or exists in another Project and therefore does not exist for this
 * caller. Denied Project access is reported the same way, so probing cannot distinguish a Project
 * that is absent from one the subject may not see.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String type() {
        return "RESOURCE_NOT_FOUND";
    }
}
