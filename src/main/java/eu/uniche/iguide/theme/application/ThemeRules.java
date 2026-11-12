package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.template.application.Colors;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.application.TemplateDefinition;
import eu.uniche.iguide.template.domain.ShellKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Theme validation ({@code 04} section 11): colors are {@code #RRGGBB}; fonts, {@code cornerRadius}, and
 * {@code density} are among the Template's {@code themeValues}; the preset is one the Template offers; shell keys
 * and values are in its {@code shellSchema}; logos are images of the same Project; and the effective colors reach
 * the WCAG 2.1 AA contrast ratios. Messages never repeat a submitted value.
 */
@Component
public class ThemeRules {

    static final int MAX_WORDMARK = 100;

    private final AssetRepository assets;

    public ThemeRules(AssetRepository assets) {
        this.assets = assets;
    }

    /** The values with colors normalized to upper case; errors at the field's own path. */
    public StyleValues values(StyleValues requested, TemplateDefinition definition, List<ErrorItem> errors) {
        StyleValues values = requested == null ? new StyleValues(null, null, null, null, null, null, null, null)
                : requested;
        Map<String, String> result = new LinkedHashMap<>();
        values.asMap().forEach((field, raw) -> {
            String value = ResourceRules.trimToNull(raw);
            if (value == null) {
                result.put(field, null);
            } else if (field.endsWith("Color")) {
                String color = Colors.normalize(value);
                if (color == null) {
                    errors.add(new ErrorItem(field, "INVALID_COLOR", "Use a #RRGGBB color."));
                }
                result.put(field, color);
            } else if (!definition.themeValues().allows(field, value)) {
                errors.add(new ErrorItem(field, "VALUE_NOT_ALLOWED", "Is not among the Template's allowed values."));
                result.put(field, null);
            } else {
                result.put(field, value);
            }
        });
        return StyleValues.fromMap(result);
    }

    /** The contrast rules on the effective values ({@code 14} section 2) [ARC_4.3]. */
    public void contrast(StyleValues effective, List<ErrorItem> errors) {
        for (Colors.ContrastFailure failure : Colors.contrastFailures(effective)) {
            errors.add(new ErrorItem(failure.field(), "CONTRAST_TOO_LOW",
                    "Needs a contrast of at least " + failure.minimum() + ":1 against backgroundColor."));
        }
    }

    public void stylePreset(String key, TemplateDefinition definition, List<ErrorItem> errors) {
        if (key != null && !definition.stylePresets().contains(key)) {
            errors.add(new ErrorItem("stylePresetKey", "STYLE_PRESET_NOT_OFFERED",
                    "The Project's Template does not offer this style."));
        }
    }

    /** The complete shell: requested values, the Template's {@code defaultShell} for keys not sent. */
    public Map<String, Object> shell(Map<String, Object> requested, TemplateDefinition definition,
                                     List<ErrorItem> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(ShellKeys.BRAND_WORDMARK, null);
        ShellKeys.CHOSEN.forEach(key -> result.put(key, definition.defaultShell().get(key)));
        if (requested == null) {
            return result;
        }
        requested.forEach((key, value) -> {
            String path = "shell." + key;
            if (ShellKeys.BRAND_WORDMARK.equals(key)) {
                if (value != null && !(value instanceof String)) {
                    errors.add(new ErrorItem(path, "INVALID", "Is text or null."));
                } else {
                    String wordmark = ResourceRules.trimToNull((String) value);
                    if (wordmark != null && wordmark.length() > MAX_WORDMARK) {
                        errors.add(new ErrorItem(path, "TOO_LONG", "At most " + MAX_WORDMARK + " characters."));
                    }
                    result.put(key, wordmark);
                }
            } else if (!definition.shellSchema().containsKey(key)) {
                errors.add(new ErrorItem(path, "UNKNOWN_KEY", "Is not a shell option of the Template."));
            } else if (!(value instanceof String text) || !definition.shellSchema().get(key).contains(text)) {
                errors.add(new ErrorItem(path, "VALUE_NOT_ALLOWED", "Is not among the Template's allowed values."));
            } else {
                result.put(key, text);
            }
        });
        return result;
    }

    /** A logo, favicon, or splash is an image Asset of the same Project ({@code 04} section 11). */
    public void image(UUID projectId, UUID assetId, String path, List<ErrorItem> errors) {
        if (assetId == null) {
            return;
        }
        Asset asset = assets.findByProjectIdAndId(projectId, assetId).orElse(null);
        if (asset == null) {
            errors.add(new ErrorItem(path, "ASSET_NOT_FOUND", "No such Asset in this Project."));
        } else if (asset.getType() != AssetType.IMAGE) {
            errors.add(new ErrorItem(path, "ASSET_NOT_IMAGE", "Choose an image."));
        }
    }
}
