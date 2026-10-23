package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.web.ApiException;
import eu.uniche.iguide.surface.application.SurfaceLookup;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.trigger.domain.TriggerType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The absolute runtime URL a QR code encodes ({@code 11} section 9, DECISIONS.md D-029):
 * {@code {publicBaseUrl}/{defaultLanguage}/{kind}/{slug}?t={code}}. The default language is used because the
 * runtime redirects any other code to it anyway, and a printed code should open in the Project's main language.
 * Callers have passed the Project access check.
 */
@Component
public class TriggerLinks {

    private final SurfaceLookup surfaces;
    private final ProjectLanguageLookup languages;
    private final TriggerRules rules;

    public TriggerLinks(SurfaceLookup surfaces, ProjectLanguageLookup languages, TriggerRules rules) {
        this.surfaces = surfaces;
        this.languages = languages;
        this.rules = rules;
    }

    /**
     * The runtime URL of each QR code trigger of the Project, {@code null} for other types, or for every trigger
     * while the WEB surface has no address or the Project no default language. Reads the Project's settings once.
     */
    public Function<InteractionTrigger, String> forProject(UUID projectId) {
        Optional<String> base = surfaces.publicBaseUrl(projectId);
        if (base.isEmpty()) {
            return trigger -> null;
        }
        String language;
        try {
            language = languages.requireDefaultLanguage(projectId);
        } catch (ApiException e) {
            return trigger -> null;
        }
        return trigger -> trigger.getTriggerType() != TriggerType.QR_CODE || trigger.getCode() == null ? null
                : rules.targetSlug(projectId, trigger.getTargetResourceType(), trigger.getTargetResourceId())
                        .map(slug -> base.get() + "/" + language + "/" + trigger.getTargetResourceType().runtimePath()
                                + "/" + slug + "?t=" + URLEncoder.encode(trigger.getCode(), StandardCharsets.UTF_8))
                        .orElse(null);
    }
}
