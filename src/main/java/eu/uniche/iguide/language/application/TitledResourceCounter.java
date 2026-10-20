package eu.uniche.iguide.language.application;

import java.util.UUID;

/**
 * Implemented by every translated Resource kind, so that changing the default language can require
 * complete titles in the new default ({@code 12} section 4) without this package knowing the kinds.
 */
public interface TitledResourceCounter {

    /** Resources of this kind that are not archived. */
    long countResources(UUID projectId);

    /** Of those, the ones with a non-blank title in {@code languageCode}. */
    long countTitled(UUID projectId, String languageCode);
}
