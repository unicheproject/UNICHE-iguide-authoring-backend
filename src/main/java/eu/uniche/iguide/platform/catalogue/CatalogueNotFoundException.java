package eu.uniche.iguide.platform.catalogue;

/**
 * The Catalogue answered {@code 404}: the resource does not exist, or the subject may not see it.
 * The Catalogue does not distinguish the two, and neither does this backend.
 */
public class CatalogueNotFoundException extends CatalogueException {

    public CatalogueNotFoundException(String message) {
        super(message);
    }
}
