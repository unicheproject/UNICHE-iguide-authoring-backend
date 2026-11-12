package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.template.application.StyleValues;
import java.util.Map;
import java.util.UUID;

/** A complete theme as {@code PUT .../theme} replaces it; {@code null} typed values take the Template default. */
public record ThemeInput(StyleValues values,
                         String stylePresetKey,
                         UUID logoAssetId,
                         UUID compactLogoAssetId,
                         UUID faviconAssetId,
                         UUID splashAssetId,
                         Map<String, Object> shell,
                         Long version) {
}
