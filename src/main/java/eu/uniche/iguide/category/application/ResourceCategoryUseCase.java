package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.category.infrastructure.ResourceCategoryRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Category of a Point of Interest or Route ({@code 07} section 14: {@code .../{kind}/{id}/categories},
 * {@code .../categories/{bindingId}}). A Resource is in at most one Category (DECISIONS.md D-024). These
 * endpoints change the Category of a DRAFT Resource; an ACTIVE one changes it through its working copy, which
 * calls {@link #replace} on apply (DECISIONS.md D-027).
 */
@Service
public class ResourceCategoryUseCase {

    private final ProjectAccessGateway accessGateway;
    private final CategoryRules rules;
    private final ResourceCategoryRepository bindings;
    private final TransactionTemplate transactionTemplate;

    public ResourceCategoryUseCase(ProjectAccessGateway accessGateway, CategoryRules rules,
                                   ResourceCategoryRepository bindings, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.bindings = bindings;
        this.transactionTemplate = transactionTemplate;
    }

    public List<ResourceCategory> list(UUID projectId, CategoryScope kind, UUID resourceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        requireResource(projectId, kind, resourceId);
        return bindings.findByProjectIdAndResourceTypeAndResourceId(projectId, kind, resourceId);
    }

    public ResourceCategory bind(UUID projectId, CategoryScope kind, UUID resourceId, UnichePrincipal principal,
                                 UUID categoryId) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            ResourceRules.requireDraft(requireResource(projectId, kind, resourceId), kindName(kind));
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkAssignable(projectId, categoryId, kind, errors);
            ResourceRules.throwIfAny(errors, "The category cannot be assigned.");
            if (!bindings.findByProjectIdAndResourceTypeAndResourceId(projectId, kind, resourceId).isEmpty()) {
                throw new DuplicateValueException("categoryId",
                        "The " + kindName(kind) + " already has a category; remove it first.");
            }
            return bindings.saveAndFlush(ResourceCategory.of(projectId, categoryId, kind, resourceId));
        });
    }

    public void unbind(UUID projectId, CategoryScope kind, UUID resourceId, UUID bindingId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            ResourceRules.requireDraft(requireResource(projectId, kind, resourceId), kindName(kind));
            bindings.delete(bindings.findByProjectIdAndResourceTypeAndResourceIdAndId(projectId, kind, resourceId,
                    bindingId).orElseThrow(() -> new ResourceNotFoundException("No such category binding.")));
        });
    }

    /**
     * Makes the Resource's Category exactly {@code categoryIds} (none or one), inside the caller's transaction
     * and without a status check: the apply of a working copy. Errors are reported at {@code categoryIds}.
     */
    public void replace(UUID projectId, CategoryScope kind, UUID resourceId, List<UUID> categoryIds) {
        List<UUID> wanted = categoryIds == null ? List.of() : categoryIds;
        if (wanted.size() > 1) {
            throw new ValidationFailedException("A " + kindName(kind) + " is in at most one category.",
                    List.of(new ErrorItem("categoryIds", "TOO_MANY", "Choose at most one category.")));
        }
        List<ResourceCategory> current = bindings.findByProjectIdAndResourceTypeAndResourceId(projectId, kind,
                resourceId);
        if (wanted.isEmpty() ? current.isEmpty()
                : current.size() == 1 && current.getFirst().getCategoryId().equals(wanted.getFirst())) {
            return;
        }
        bindings.deleteAll(current);
        bindings.flush();
        if (!wanted.isEmpty()) {
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkAssignable(projectId, wanted.getFirst(), kind, errors);
            ResourceRules.throwIfAny(errors.stream()
                    .map(e -> new ErrorItem("categoryIds[0]", e.code(), e.message())).toList(),
                    "The category cannot be assigned.");
            bindings.saveAndFlush(ResourceCategory.of(projectId, wanted.getFirst(), kind, resourceId));
        }
    }

    private ResourceStatus requireResource(UUID projectId, CategoryScope kind, UUID resourceId) {
        return rules.resourceStatus(projectId, kind, resourceId).orElseThrow(() ->
                new ResourceNotFoundException("No such " + kindName(kind) + " in this project."));
    }

    private static String kindName(CategoryScope kind) {
        return kind == CategoryScope.POI ? "point of interest" : "route";
    }
}
