package eu.uniche.iguide.platform.catalogue;

/**
 * The Catalogue did not answer within the configured connect or read timeout. Distinct from
 * unavailability because a timeout is worth retrying and an outage is worth reporting.
 */
public class CatalogueTimeoutException extends CatalogueException {

    public CatalogueTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
