package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.surface.application.SurfaceLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import eu.uniche.iguide.trigger.infrastructure.InteractionTriggerRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** Reads triggers: one, or a page filtered by target and status ({@code 06} section 16). */
@Service
public class TriggerQuery {

    private final ProjectAccessGateway accessGateway;
    private final TriggerRules rules;
    private final InteractionTriggerRepository triggers;
    private final TriggerLinks links;
    private final SurfaceLookup surfaces;
    private final ProjectLanguageLookup languages;

    public TriggerQuery(ProjectAccessGateway accessGateway, TriggerRules rules, InteractionTriggerRepository triggers,
                        TriggerLinks links, SurfaceLookup surfaces, ProjectLanguageLookup languages) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.triggers = triggers;
        this.links = links;
        this.surfaces = surfaces;
        this.languages = languages;
    }

    public InteractionTrigger get(UUID projectId, UUID triggerId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.requireTrigger(projectId, triggerId);
    }

    public Page<InteractionTrigger> list(UUID projectId, UnichePrincipal principal, TriggerTargetType targetType,
                                         UUID targetId, ResourceStatus status, Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<InteractionTrigger> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (targetType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetResourceType"), targetType));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetResourceId"), targetId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return triggers.findAll(spec, PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
    }

    /**
     * {@code GET .../qr.png} ({@code 07} section 14): the QR code of the target's absolute runtime URL with
     * {@code ?t={code}}, built from the WEB surface's {@code publicBaseUrl} ({@link TriggerLinks}); {@code 422} for
     * another trigger type or while the address is missing (DECISIONS.md D-029).
     */
    public byte[] qrImage(UUID projectId, UUID triggerId, UnichePrincipal principal) {
        InteractionTrigger trigger = get(projectId, triggerId, principal);
        if (trigger.getTriggerType() != TriggerType.QR_CODE) {
            throw new ValidationFailedException("Only a QR code trigger has a QR image.",
                    List.of(new ErrorItem("triggerType", "NOT_QR_CODE", "Only a QR code trigger has a QR image.")));
        }
        if (surfaces.publicBaseUrl(projectId).isEmpty()) {
            throw new ValidationFailedException("The QR image needs the app's public web address.", List.of(new ErrorItem(
                    "publicBaseUrl", "NO_PUBLIC_BASE_URL", "Enable the WEB surface with its public web address first.")));
        }
        languages.requireDefaultLanguage(projectId);
        String url = links.forProject(projectId).apply(trigger);
        if (url == null) {
            throw new ResourceNotFoundException("The trigger's target no longer exists.");
        }
        return QrImages.png(url);
    }

    /** The runtime URL of each trigger, for responses; the caller has passed the access check. */
    public Function<InteractionTrigger, String> runtimeUrls(UUID projectId) {
        return links.forProject(projectId);
    }
}
