package eu.uniche.iguide.platform.catalogue;

/** The Catalogue answered {@code 403}: the subject is known but may not perform the operation. */
public class CatalogueForbiddenException extends CatalogueException {

    public CatalogueForbiddenException(String message) {
        super(message);
    }
}
