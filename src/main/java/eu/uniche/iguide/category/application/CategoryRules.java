package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.category.infrastructure.ResourceCategoryRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Category use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class CategoryRules {

    static final Pattern CODE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    static final int MAX_CODE = 200;
    static final int MAX_NAME = 200;

    /** The platform icon set the CMS draws (DECISIONS.md D-024); {@code 03} section 10 names no keys. */
    public static final Set<String> ICONS = Set.of("monument", "building", "statue", "vase", "church", "castle", "tree",
            "mountain", "beach", "view", "food", "shop", "info", "photo", "water", "harbour", "factory", "trail",
            "family", "bike");

    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;
    private final ResourceCategoryRepository bindings;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository poiTranslations;
    private final RouteRepository routes;
    private final RouteTranslationRepository routeTranslations;
    private final ProjectLanguageLookup languages;

    public CategoryRules(CategoryRepository categories, CategoryTranslationRepository translations,
                         ResourceCategoryRepository bindings, PointOfInterestRepository pois,
                         PoiTranslationRepository poiTranslations, RouteRepository routes,
                         RouteTranslationRepository routeTranslations, ProjectLanguageLookup languages) {
        this.categories = categories;
        this.translations = translations;
        this.bindings = bindings;
        this.pois = pois;
        this.poiTranslations = poiTranslations;
        this.routes = routes;
        this.routeTranslations = routeTranslations;
        this.languages = languages;
    }

    public Category requireCategory(UUID projectId, UUID categoryId) {
        return categories.findByProjectIdAndId(projectId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("No such category in this project."));
    }

    public CategoryView view(Category category) {
        return views(category.getProjectId(), List.of(category)).get(0);
    }

    /** Views of several Categories of one Project, loaded with one query per table. */
    public List<CategoryView> views(UUID projectId, List<Category> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Category::getId).toList();
        Map<UUID, List<CategoryTranslation>> texts = translations.findByProjectIdAndCategoryIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(CategoryTranslation::getCategoryId));
        Map<UUID, Long> counts = bindings.findByProjectIdAndCategoryIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(ResourceCategory::getCategoryId, Collectors.counting()));
        return list.stream().map(c -> new CategoryView(c, texts.getOrDefault(c.getId(), List.of()),
                counts.getOrDefault(c.getId(), 0L))).toList();
    }

    /** Adds an error when the code is malformed; throws {@code 409} when another Category uses it. */
    public void checkCode(UUID projectId, String code, UUID ownId, List<ErrorItem> errors) {
        if (code == null || code.length() > MAX_CODE || !CODE.matcher(code).matches()) {
            errors.add(new ErrorItem("code", "INVALID_FORMAT",
                    "Use lowercase Latin letters, digits, and single hyphens, at most " + MAX_CODE + " characters."));
            return;
        }
        boolean taken = ownId == null
                ? categories.existsByProjectIdAndCode(projectId, code)
                : categories.existsByProjectIdAndCodeAndIdNot(projectId, code, ownId);
        if (taken) {
            throw new DuplicateValueException("code", "Another category in this project already uses this code.");
        }
    }

    public String generateCode(UUID projectId, String name) {
        return SlugGenerator.generate(name, "category", code -> categories.existsByProjectIdAndCode(projectId, code));
    }

    public static String checkIcon(String raw, List<ErrorItem> errors) {
        String icon = ResourceRules.trimToNull(raw);
        if (icon != null && !ICONS.contains(icon)) {
            errors.add(new ErrorItem("icon", "INVALID", "Is not one of the built-in icons."));
        }
        return icon;
    }

    /**
     * Two levels at most ({@code 03} section 10): the parent is a top-level Category of the same scope in
     * this Project that is not archived, and a Category with subcategories stays top-level.
     */
    public void checkParent(UUID projectId, UUID parentId, UUID ownId, CategoryScope scope, List<ErrorItem> errors) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(ownId)) {
            errors.add(new ErrorItem("parentCategoryId", "INVALID_REFERENCE", "A category cannot be its own parent."));
            return;
        }
        if (ownId != null && !categories.findByProjectIdAndParentCategoryId(projectId, ownId).isEmpty()) {
            errors.add(new ErrorItem("parentCategoryId", "HAS_SUBCATEGORIES",
                    "This category has subcategories, so it stays top-level."));
            return;
        }
        Optional<Category> parent = categories.findByProjectIdAndId(projectId, parentId);
        if (parent.isEmpty()) {
            errors.add(new ErrorItem("parentCategoryId", "RESOURCE_NOT_FOUND", "No such category in this project."));
        } else if (parent.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("parentCategoryId", "ARCHIVED_REFERENCE", "The parent category is archived."));
        } else if (parent.get().getParentCategoryId() != null) {
            errors.add(new ErrorItem("parentCategoryId", "TOO_DEEP",
                    "Categories have two levels at most; choose a top-level category."));
        } else if (scope != null && parent.get().getScope() != scope) {
            errors.add(new ErrorItem("parentCategoryId", "SCOPE_MISMATCH",
                    "The parent applies to " + parent.get().getScope().label() + "."));
        }
    }

    public static String checkName(String raw, String path, boolean required, List<ErrorItem> errors) {
        String name = ResourceRules.trimToNull(raw);
        if (name == null && required) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required in the default language."));
        } else if (name != null && name.length() > MAX_NAME) {
            errors.add(new ErrorItem(path, "TOO_LONG", "At most " + MAX_NAME + " characters."));
        }
        return name;
    }

    public void checkLanguages(UUID projectId, Set<String> codes, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        codes.stream().filter(code -> !enabled.contains(code)).forEach(code -> errors.add(
                new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
    }

    /** A Category a Resource of {@code scope} may be put in: in this Project, not archived, and of that scope. */
    public void checkAssignable(UUID projectId, UUID categoryId, CategoryScope scope, List<ErrorItem> errors) {
        if (categoryId == null) {
            errors.add(new ErrorItem("categoryId", "REQUIRED", "Choose a category."));
            return;
        }
        Optional<Category> category = categories.findByProjectIdAndId(projectId, categoryId);
        if (category.isEmpty()) {
            errors.add(new ErrorItem("categoryId", "RESOURCE_NOT_FOUND", "No such category in this project."));
        } else if (category.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("categoryId", "ARCHIVED_REFERENCE", "The category is archived."));
        } else if (category.get().getScope() != scope) {
            errors.add(new ErrorItem("categoryId", "SCOPE_MISMATCH",
                    "The category applies to " + category.get().getScope().label() + "."));
        }
    }

    /** The status of a Point of Interest or Route of this Project, empty when it does not exist here. */
    public Optional<ResourceStatus> resourceStatus(UUID projectId, CategoryScope kind, UUID resourceId) {
        return kind == CategoryScope.POI
                ? pois.findByProjectIdAndId(projectId, resourceId).map(PointOfInterest::getStatus)
                : routes.findByProjectIdAndId(projectId, resourceId).map(Route::getStatus);
    }

    /**
     * What keeps a Category in use ({@code 03} section 1): its non-archived subcategories and the
     * non-archived Points of Interest or Routes bound to it, titled in the default language.
     */
    public List<ResourceReference> referencesTo(UUID projectId, Category category) {
        List<ResourceReference> references = new ArrayList<>();
        List<Category> children = categories.findByProjectIdAndParentCategoryIdAndStatusNotOrderBySortOrderAsc(
                projectId, category.getId(), ResourceStatus.ARCHIVED);
        List<UUID> bound = bindings.findByProjectIdAndCategoryId(projectId, category.getId()).stream()
                .map(ResourceCategory::getResourceId).toList();
        if (children.isEmpty() && bound.isEmpty()) {
            return references;
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        if (!children.isEmpty()) {
            Map<UUID, String> names = translations.findByProjectIdAndCategoryIdIn(projectId,
                            children.stream().map(Category::getId).toList()).stream()
                    .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasName())
                    .collect(Collectors.toMap(CategoryTranslation::getCategoryId, CategoryTranslation::getName));
            children.forEach(c -> references.add(new ResourceReference("CATEGORY", c.getId(),
                    names.getOrDefault(c.getId(), c.getCode()), c.getStatus())));
        }
        if (!bound.isEmpty() && category.getScope() == CategoryScope.POI) {
            Map<UUID, String> titles = poiTranslations.findByProjectIdAndPoiIdIn(projectId, bound).stream()
                    .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                    .collect(Collectors.toMap(t -> t.getPoiId(), t -> t.getTitle()));
            pois.findByProjectIdAndIdIn(projectId, bound).stream()
                    .filter(p -> p.getStatus() != ResourceStatus.ARCHIVED)
                    .forEach(p -> references.add(new ResourceReference("POI", p.getId(),
                            titles.getOrDefault(p.getId(), p.getSlug()), p.getStatus())));
        } else if (!bound.isEmpty()) {
            Map<UUID, String> titles = routeTranslations.findByProjectIdAndRouteIdIn(projectId, bound).stream()
                    .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                    .collect(Collectors.toMap(t -> t.getRouteId(), t -> t.getTitle()));
            routes.findAllById(bound).stream()
                    .filter(r -> r.getProjectId().equals(projectId) && r.getStatus() != ResourceStatus.ARCHIVED)
                    .forEach(r -> references.add(new ResourceReference("ROUTE", r.getId(),
                            titles.getOrDefault(r.getId(), r.getSlug()), r.getStatus())));
        }
        return references;
    }
}
