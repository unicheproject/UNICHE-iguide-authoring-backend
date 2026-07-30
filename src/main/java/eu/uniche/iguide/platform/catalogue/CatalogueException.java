package eu.uniche.iguide.platform.catalogue;

/**
 * Base class for every failure of a Catalogue call.
 *
 * <p>These are deliberately not {@code ApiException} subclasses. The client is a pure HTTP adapter
 * and knows nothing about this API's status contract; translating an upstream failure into a status
 * this backend returns is the exception handler's decision, made in one place.
 */
public abstract class CatalogueException extends RuntimeException {

    protected CatalogueException(String message) {
        super(message);
    }

    protected CatalogueException(String message, Throwable cause) {
        super(message, cause);
    }
}
