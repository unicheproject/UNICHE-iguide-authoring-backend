package eu.uniche.iguide.platform.catalogue;

/**
 * The Catalogue could not be reached, or answered {@code 5xx}. Authorization therefore cannot be
 * verified, and no local data may stand in for it.
 */
public class CatalogueUnavailableException extends CatalogueException {

    public CatalogueUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public CatalogueUnavailableException(String message) {
        super(message);
    }
}
