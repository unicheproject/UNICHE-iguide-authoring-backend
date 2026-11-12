package eu.uniche.iguide.template.application;

import java.util.Map;

/** One style preset file, {@code src/main/resources/style-presets/{key}.json} ({@code 04} sections 9 and 15). */
public record StylePresetFile(String filename,
                              String key,
                              Map<String, String> name,
                              Map<String, String> description,
                              StyleValues values) {
}
