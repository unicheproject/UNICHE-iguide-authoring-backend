package eu.uniche.iguide.platform.catalogue;

/**
 * The Catalogue answered {@code 409}, typically a duplicate project slug. Surfacing this as a
 * conflict rather than a generic failure is what lets the frontend ask the user for another slug.
 */
public class CatalogueConflictException extends CatalogueException {

    public CatalogueConflictException(String message) {
        super(message);
    }
}
