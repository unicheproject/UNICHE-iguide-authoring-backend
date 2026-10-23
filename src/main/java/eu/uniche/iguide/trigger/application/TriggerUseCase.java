package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import eu.uniche.iguide.trigger.infrastructure.InteractionTriggerRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates, edits, and moves triggers through their lifecycle ({@code 03} sections 1 and 14). A trigger is
 * edited in place while it is not archived (DECISIONS.md D-026); its type and target never change.
 */
@Service
public class TriggerUseCase {

    private final ProjectAccessGateway accessGateway;
    private final TriggerRules rules;
    private final InteractionTriggerRepository triggers;
    private final TransactionTemplate transactionTemplate;

    public TriggerUseCase(ProjectAccessGateway accessGateway, TriggerRules rules, InteractionTriggerRepository triggers,
                          TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.triggers = triggers;
        this.transactionTemplate = transactionTemplate;
    }

    public InteractionTrigger create(UUID projectId, UnichePrincipal principal, TriggerTargetType targetType, UUID targetId,
                                     TriggerType type, String code, List<Surface> surfaces,
                                     Map<String, Object> configuration) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkTarget(projectId, targetType, targetId, errors);
            TriggerRules.checkType(type, errors);
            ResourceRules.throwIfAny(errors, "The trigger is invalid.");
            InteractionTrigger.Settings settings = rules.normalize(projectId, type, code, surfaces, configuration, null, errors);
            ResourceRules.throwIfAny(errors, "The trigger is invalid.");
            return triggers.saveAndFlush(InteractionTrigger.draft(projectId, type, targetType, targetId, settings,
                    triggers.findMaxSortOrder(projectId, targetType, targetId) + 1, principal.subject(), Instant.now()));
        });
    }

    public InteractionTrigger update(UUID projectId, UUID triggerId, UnichePrincipal principal, Long version, String code,
                                     List<Surface> surfaces, Map<String, Object> configuration) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            InteractionTrigger trigger = rules.requireTrigger(projectId, triggerId);
            ResourceRules.requireCurrentVersion(trigger.getVersion(), version, "trigger");
            if (trigger.getStatus() == ResourceStatus.ARCHIVED) {
                throw new ValidationFailedException("An archived trigger cannot be edited.", List.of(new ErrorItem(
                        "status", "RESOURCE_NOT_EDITABLE", "Restore the trigger before editing it.")));
            }
            List<ErrorItem> errors = new ArrayList<>();
            InteractionTrigger.Settings settings = rules.normalize(projectId, trigger.getTriggerType(), code, surfaces,
                    configuration, triggerId, errors);
            ResourceRules.throwIfAny(errors, "The trigger is invalid.");
            trigger.edit(settings, principal.subject(), Instant.now());
            return triggers.saveAndFlush(trigger);
        });
    }

    /** Nothing refers to a trigger, so every legal transition is allowed; restoring needs a live target. */
    public InteractionTrigger changeStatus(UUID projectId, UUID triggerId, UnichePrincipal principal,
                                           ResourceStatus target, Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            InteractionTrigger trigger = rules.requireTrigger(projectId, triggerId);
            ResourceRules.requireCurrentVersion(trigger.getVersion(), version, "trigger");
            ResourceRules.requireTransition(trigger.getStatus(), target, "trigger");
            if (target != ResourceStatus.ARCHIVED) {
                List<ErrorItem> errors = new ArrayList<>();
                rules.checkTarget(projectId, trigger.getTargetResourceType(), trigger.getTargetResourceId(), errors);
                ResourceRules.throwIfAny(errors, "The trigger's target is not available.");
            }
            trigger.changeStatus(target, principal.subject(), Instant.now());
            return triggers.saveAndFlush(trigger);
        });
    }
}
