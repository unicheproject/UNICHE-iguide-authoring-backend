package eu.uniche.iguide.asset.application;

/** The texts of an Asset in one language, as sent; blank values are stored as null. */
public record AssetTranslationInput(String title, String caption, String alternativeText, String credits,
                                    String transcript) {
}
