package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * The {@code categoryId} filter of the POI and Route lists ({@code 07} section 14, DECISIONS.md D-030): the
 * Resources in that Category or in one of its subcategories, or with {@code none} those without a Category. An
 * id of no Category of the Project matches nothing.
 */
public final class CategoryFilter {

    public static final String NONE = "none";

    private CategoryFilter() {
    }

    /** {@code null} when the parameter is absent. */
    public static <T> Specification<T> of(UUID projectId, String rawCategoryId, CategoryScope scope) {
        String value = ResourceRules.trimToNull(rawCategoryId);
        if (value == null) {
            return null;
        }
        if (NONE.equalsIgnoreCase(value)) {
            return (root, query, cb) -> cb.not(cb.exists(bindings(projectId, scope, root.get("id"), query, cb, null)));
        }
        UUID categoryId;
        try {
            categoryId = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationFailedException("The category filter is invalid.", List.of(new ErrorItem("categoryId",
                    "INVALID_FORMAT", "Use a category id, or none for resources without a category.")));
        }
        return (root, query, cb) -> cb.exists(bindings(projectId, scope, root.get("id"), query, cb, categoryId));
    }

    private static Subquery<UUID> bindings(UUID projectId, CategoryScope scope, Expression<Object> resourceId,
                                           CriteriaQuery<?> query, CriteriaBuilder cb, UUID categoryId) {
        Subquery<UUID> bound = query.subquery(UUID.class);
        var rc = bound.from(ResourceCategory.class);
        var where = cb.and(
                cb.equal(rc.get("projectId"), projectId),
                cb.equal(rc.get("resourceType"), scope),
                cb.equal(rc.get("resourceId"), resourceId));
        if (categoryId != null) {
            Subquery<UUID> family = query.subquery(UUID.class);
            var c = family.from(Category.class);
            family.select(c.get("id")).where(
                    cb.equal(c.get("projectId"), projectId),
                    cb.or(cb.equal(c.get("id"), categoryId), cb.equal(c.get("parentCategoryId"), categoryId)));
            where = cb.and(where, rc.get("categoryId").in(family));
        }
        bound.select(rc.get("resourceId")).where(where);
        return bound;
    }
}
