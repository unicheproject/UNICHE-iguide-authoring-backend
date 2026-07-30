package eu.uniche.iguide.platform.catalogue;

/**
 * The Catalogue answered successfully but the body is unusable — absent, unparseable, or missing a
 * field this backend cannot proceed without, such as the canonical Project UUID.
 */
public class CatalogueBadResponseException extends CatalogueException {

    public CatalogueBadResponseException(String message) {
        super(message);
    }

    public CatalogueBadResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
