package eu.uniche.iguide.category.api;

import eu.uniche.iguide.category.application.CategoryView;
import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Category with its translations; {@code resourceCount} is the number of Points of Interest or Routes in
 * it. {@code hasWorkingCopy} and {@code workingCopyUpdatedAt} tell whether it has pending changes
 * ({@code 07} section 14).
 */
public record CategoryResponse(UUID id, UUID projectId, String code, ResourceStatus status, CategoryScope scope,
                               UUID parentCategoryId, String icon, int sortOrder, Instant archivedAt,
                               Instant createdAt, Instant updatedAt, long version, boolean hasWorkingCopy,
                               Instant workingCopyUpdatedAt,
                               Map<String, CategoryRequests.CategoryTranslationBody> translations,
                               long resourceCount) {

    /** One Resource in a Category. */
    public record Binding(UUID id, UUID categoryId, CategoryScope resourceType, UUID resourceId) {

        public static Binding from(ResourceCategory b) {
            return new Binding(b.getId(), b.getCategoryId(), b.getResourceType(), b.getResourceId());
        }
    }

    public static CategoryResponse from(CategoryView view) {
        return from(view, null);
    }

    public static CategoryResponse from(CategoryView view, Instant workingCopyUpdatedAt) {
        Category c = view.category();
        Map<String, CategoryRequests.CategoryTranslationBody> texts = view.translations().stream()
                .collect(Collectors.toMap(CategoryTranslation::getLanguageCode,
                        t -> new CategoryRequests.CategoryTranslationBody(t.getName(), t.getDescription()),
                        (a, b) -> a, TreeMap::new));
        return new CategoryResponse(c.getId(), c.getProjectId(), c.getCode(), c.getStatus(), c.getScope(),
                c.getParentCategoryId(), c.getIcon(), c.getSortOrder(), c.getArchivedAt(), c.getCreatedAt(),
                c.getUpdatedAt(), c.getVersion(), workingCopyUpdatedAt != null, workingCopyUpdatedAt, texts, view.resourceCount());
    }
}
