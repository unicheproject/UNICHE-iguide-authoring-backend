package eu.uniche.iguide.trigger.application;

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

    public TriggerQuery(ProjectAccessGateway accessGateway, TriggerRules rules, InteractionTriggerRepository triggers) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.triggers = triggers;
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
     * {@code GET .../qr.png} ({@code 07} section 14): the QR encodes the target's runtime URL built from the
     * {@code WEB} surface's {@code publicBaseUrl}, which does not exist until Surfaces do (GAPS.md G-033).
     */
    public byte[] qrImage(UUID projectId, UUID triggerId, UnichePrincipal principal) {
        InteractionTrigger trigger = get(projectId, triggerId, principal);
        if (trigger.getTriggerType() != TriggerType.QR_CODE) {
            throw new ValidationFailedException("Only a QR code trigger has a QR image.",
                    List.of(new ErrorItem("triggerType", "NOT_QR_CODE", "Only a QR code trigger has a QR image.")));
        }
        throw new ValidationFailedException("The QR image needs the app's public web address.", List.of(new ErrorItem(
                "publicBaseUrl", "NO_PUBLIC_BASE_URL", "Set the public web address of the WEB surface first.")));
    }
}
