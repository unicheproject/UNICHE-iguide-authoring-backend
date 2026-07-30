package eu.uniche.iguide.platform.catalogue;

/**
 * Result of a conditional read.
 *
 * <p>A {@code 304} carries no body, so the caller keeps the representation it already holds. Modelling
 * that explicitly keeps the cache decision in the application layer and out of the HTTP adapter.
 *
 * @param notModified whether the Catalogue answered {@code 304}
 * @param body        the new representation, {@code null} when {@code notModified}
 */
public record CatalogueConditionalResponse<T>(boolean notModified, T body) {

    public static <T> CatalogueConditionalResponse<T> unchanged() {
        return new CatalogueConditionalResponse<>(true, null);
    }

    public static <T> CatalogueConditionalResponse<T> changed(T body) {
        return new CatalogueConditionalResponse<>(false, body);
    }
}
