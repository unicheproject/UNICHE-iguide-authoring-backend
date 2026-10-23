package eu.uniche.iguide.resource.application;

import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Places the field errors of a child write under the child's position in a larger body, such as
 * {@code stops[2].poiId} in a working-copy payload ({@code 07} section 15: errors name the path).
 */
public final class ErrorPaths {

    private ErrorPaths() {
    }

    public static <T> T within(String prefix, Supplier<T> action) {
        try {
            return action.get();
        } catch (ValidationFailedException e) {
            List<ErrorItem> errors = e.errors().stream()
                    .map(error -> new ErrorItem(join(prefix, error.path()), error.code(), error.message()))
                    .toList();
            throw errors.isEmpty()
                    ? new ValidationFailedException(e.getMessage(), List.of(new ErrorItem(prefix, "INVALID", e.getMessage())))
                    : new ValidationFailedException(e.getMessage(), errors);
        } catch (DuplicateValueException e) {
            String path = e.errors().isEmpty() ? null : e.errors().getFirst().path();
            throw new DuplicateValueException(join(prefix, path), e.getMessage());
        }
    }

    public static void within(String prefix, Runnable action) {
        within(prefix, () -> {
            action.run();
            return null;
        });
    }

    private static String join(String prefix, String path) {
        return path == null || path.isEmpty() ? prefix : prefix + "." + path;
    }
}
