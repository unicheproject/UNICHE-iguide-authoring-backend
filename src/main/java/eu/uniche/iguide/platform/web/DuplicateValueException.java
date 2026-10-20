package eu.uniche.iguide.platform.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A value that must be unique in the Project, such as a slug, is already taken ({@code 06} section 12). */
public class DuplicateValueException extends ApiException {

    public DuplicateValueException(String path, String message) {
        super(message, List.of(new ErrorItem(path, "DUPLICATE_VALUE", message)), null);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String type() {
        return "DUPLICATE_VALUE";
    }
}
