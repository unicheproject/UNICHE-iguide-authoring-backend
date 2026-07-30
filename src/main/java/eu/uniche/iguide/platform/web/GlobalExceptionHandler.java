package eu.uniche.iguide.platform.web;

import eu.uniche.iguide.platform.catalogue.CatalogueBadResponseException;
import eu.uniche.iguide.platform.catalogue.CatalogueConflictException;
import eu.uniche.iguide.platform.catalogue.CatalogueForbiddenException;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.catalogue.CatalogueTimeoutException;
import eu.uniche.iguide.platform.catalogue.CatalogueUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Maps every exception that can reach a controller boundary onto the status contract of this API.
 *
 * <p>{@code 401} never arrives here. The resource server emits it directly, with a
 * {@code WWW-Authenticate: Bearer} header, before dispatch. Neither the correlation filter nor the
 * identity provisioning filter is reachable either, since this advice only sees exceptions thrown
 * during controller execution; each filter handles its own failures.
 *
 * <p>Logging policy: {@code 5xx} at error with the stack trace, {@code 403} at warning as a
 * security-audit trail, and the remaining client errors not at all, being expected traffic. Bearer
 * tokens, secrets, and rejected field values are never logged.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- This backend's own typed failures -------------------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApiException(ApiException exception) {
        if (exception.status().is5xxServerError()) {
            log.error("Request failed with {}", exception.status().value(), exception);
        }
        return respond(exception.status(), exception.type(), exception.getMessage(), exception.errors());
    }

    // --- Catalogue failures ----------------------------------------------------------------------
    //
    // These four rows are the gap the MuseotekBox sister backend leaves open, where a conflict, an
    // outage, and a timeout all surface as an indistinguishable 500. They are mapped explicitly.

    @ExceptionHandler(CatalogueNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueNotFound(CatalogueNotFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CatalogueForbiddenException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueForbidden(CatalogueForbiddenException exception) {
        log.warn("Catalogue denied the operation: {}", exception.getMessage());
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage());
    }

    @ExceptionHandler(CatalogueConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueConflict(CatalogueConflictException exception) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(CatalogueBadResponseException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueBadResponse(CatalogueBadResponseException exception) {
        log.error("Catalogue returned an unusable response", exception);
        return respond(HttpStatus.BAD_GATEWAY, "UPSTREAM_INVALID_RESPONSE",
                "The UNICHE Catalogue returned a response this backend cannot use.");
    }

    @ExceptionHandler(CatalogueUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueUnavailable(CatalogueUnavailableException exception) {
        log.error("Catalogue unavailable, authorization cannot be verified", exception);
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                "The UNICHE Catalogue is unavailable, so access cannot be verified.");
    }

    @ExceptionHandler(CatalogueTimeoutException.class)
    public ResponseEntity<ErrorEnvelope> handleCatalogueTimeout(CatalogueTimeoutException exception) {
        log.error("Catalogue timed out", exception);
        return respond(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT",
                "The UNICHE Catalogue did not respond in time.");
    }

    // --- Framework failures ----------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
    }

    /**
     * Bean validation failures. Only the field path and the validation message are copied; the
     * rejected value that {@link FieldError} also carries is never included.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidBody(MethodArgumentNotValidException exception) {
        List<ErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorItem(field.getField(), codeOf(field), field.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                "The request is not valid.", errors);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return respond(HttpStatus.CONFLICT, "CONFLICT",
                "The resource changed since it was loaded. Reload and reapply the change.");
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorEnvelope> handleMalformedRequest(Exception exception) {
        // The exception message can quote the offending input, so it is not copied into the body.
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request could not be parsed.");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNoHandler(NoHandlerFoundException exception) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "No such endpoint.");
    }

    /**
     * Anything left is a defect in this backend. The message is not disclosed: it may name internal
     * structure or quote a value the caller sent.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed.");
    }

    private static String codeOf(FieldError field) {
        String code = field.getCode();
        return code == null ? "INVALID" : code.toUpperCase();
    }

    private static ResponseEntity<ErrorEnvelope> respond(HttpStatus status,
                                                         String type,
                                                         String title) {
        return respond(status, type, title, List.of());
    }

    private static ResponseEntity<ErrorEnvelope> respond(HttpStatus status,
                                                         String type,
                                                         String title,
                                                         List<ErrorItem> errors) {
        return ResponseEntity.status(status)
                .body(ErrorEnvelope.of(type, title, status.value(), errors));
    }
}
