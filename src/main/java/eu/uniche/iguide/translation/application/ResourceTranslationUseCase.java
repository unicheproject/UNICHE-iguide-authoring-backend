package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.SaveWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandlers;
import eu.uniche.iguide.workingcopy.application.WorkingCopyView;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT} and {@code DELETE .../{kind}/{id}/translations/{lang}} ({@code 07} section 14, {@code 12} sections 3 and
 * 7; DECISIONS.md D-031): one language of a Resource's texts. The kind's working-copy handler validates and writes
 * the whole state with that language replaced, so every rule of a normal save applies. A DRAFT Resource is written
 * in place; an ACTIVE one through its working copy, as the Translation workspace requires.
 */
@Service
public class ResourceTranslationUseCase {

    /** The kinds whose texts are translations; maps and floor plans have none. */
    public static final Set<WorkingCopyResourceType> KINDS = EnumSet.of(WorkingCopyResourceType.PLACE,
            WorkingCopyResourceType.POI, WorkingCopyResourceType.ROUTE, WorkingCopyResourceType.GUIDE,
            WorkingCopyResourceType.EVENT, WorkingCopyResourceType.CATEGORY);

    /** The result: the Resource's read DTO, and the working copy's version when the change went into one. */
    public record Result(Object resource, Long workingCopyVersion) {
    }

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final SaveWorkingCopyUseCase saveWorkingCopy;
    private final TransactionTemplate transactionTemplate;

    public ResourceTranslationUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                      WorkingCopyHandlers handlers, ResourceWorkingCopyRepository copies,
                                      SaveWorkingCopyUseCase saveWorkingCopy, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.handlers = handlers;
        this.copies = copies;
        this.saveWorkingCopy = saveWorkingCopy;
        this.transactionTemplate = transactionTemplate;
    }

    /** Replaces the texts of one language with {@code fields} (the kind's translation fields). */
    public Result put(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal,
                      String language, Long version, Long workingCopyVersion, Map<String, Object> fields) {
        accessGateway.requireAccess(projectId, principal);
        requireLanguage(projectId, language);
        return change(projectId, type, resourceId, principal, version, workingCopyVersion,
                state -> translations(state).put(language, new HashMap<>(fields)));
    }

    /**
     * Empties every text of one language, which then reads as missing; the default language cannot be emptied,
     * since activation needs its title.
     */
    public Result clear(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal,
                        String language, Long version, Long workingCopyVersion) {
        accessGateway.requireAccess(projectId, principal);
        requireLanguage(projectId, language);
        requireNotDefault(projectId, language);
        return change(projectId, type, resourceId, principal, version, workingCopyVersion,
                state -> empty(translations(state), language));
    }

    /**
     * Replaces the texts of one language of a child row: a Route Stop ({@code "stops"}) or a Guide Block
     * ({@code "blocks"}), found by its id in the Resource's state (DECISIONS.md D-032).
     */
    public Result putChild(UUID projectId, WorkingCopyResourceType type, UUID resourceId, String children,
                           UUID childId, UnichePrincipal principal, String language, Long version,
                           Long workingCopyVersion, Map<String, Object> fields) {
        accessGateway.requireAccess(projectId, principal);
        requireLanguage(projectId, language);
        return change(projectId, type, resourceId, principal, version, workingCopyVersion,
                state -> translations(child(state, children, childId)).put(language, new HashMap<>(fields)));
    }

    /** Empties every text of one language of a child row; never the default language. */
    public Result clearChild(UUID projectId, WorkingCopyResourceType type, UUID resourceId, String children,
                             UUID childId, UnichePrincipal principal, String language, Long version,
                             Long workingCopyVersion) {
        accessGateway.requireAccess(projectId, principal);
        requireLanguage(projectId, language);
        requireNotDefault(projectId, language);
        return change(projectId, type, resourceId, principal, version, workingCopyVersion,
                state -> empty(translations(child(state, children, childId)), language));
    }

    private Result change(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal,
                          Long version, Long workingCopyVersion, Consumer<Map<String, Object>> edit) {
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        Object draft = transactionTemplate.execute(status -> {
            LiveResource live = handler.lock(projectId, resourceId);
            if (live.status() == ResourceStatus.ACTIVE) {
                return null;
            }
            ResourceRules.requireCurrentVersion(live.version(), version, type.label());
            ResourceRules.requireDraft(live.status(), type.label());
            Map<String, Object> state = handlers.liveState(handler, projectId, resourceId);
            edit.accept(state);
            handler.write(projectId, resourceId, principal, handlers.read(handler, state));
            return handler.render(projectId, resourceId, null);
        });
        if (draft != null) {
            return new Result(draft, null);
        }

        // ACTIVE: the change goes into the working copy, under the working copy's own version rules.
        Object[] start = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            ResourceWorkingCopy copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                    .orElse(null);
            return copy == null
                    ? new Object[] {handlers.liveState(handler, projectId, resourceId), version, null}
                    : new Object[] {new LinkedHashMap<>(copy.getPayload()), copy.getBaseVersion(), workingCopyVersion};
        });
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) start[0];
        edit.accept(payload);
        WorkingCopyView saved = saveWorkingCopy.execute(projectId, type, resourceId, principal, (Long) start[1],
                (Long) start[2], payload);
        return new Result(saved.resource(), saved.copy().getVersion());
    }

    private void requireNotDefault(UUID projectId, String language) {
        if (language.equals(languages.requireDefaultLanguage(projectId))) {
            throw new ValidationFailedException("The default language cannot be emptied.", List.of(new ErrorItem(
                    "language", "DEFAULT_LANGUAGE", "Change the project's default language first.")));
        }
    }

    /** Sets every text of the language to nothing, so it reads as missing; a language without texts stays so. */
    private static void empty(Map<String, Object> all, String language) {
        if (all.get(language) instanceof Map<?, ?> texts) {
            Map<String, Object> empty = new HashMap<>();
            texts.keySet().forEach(key -> empty.put(String.valueOf(key), null));
            all.put(language, empty);
        }
    }

    /** The child row with that id in the state's list, as a mutable map put back in its place; 404 when absent. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> state, String children, UUID childId) {
        List<Object> items = state.get(children) instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Map<?, ?> item && childId.toString().equals(String.valueOf(item.get("id")))) {
                Map<String, Object> copy = new HashMap<>((Map<String, Object>) item);
                items.set(i, copy);
                state.put(children, items);
                return copy;
            }
        }
        throw new ResourceNotFoundException("No such " + ("stops".equals(children) ? "stop" : "block")
                + " in this " + ("stops".equals(children) ? "route" : "guide") + ".");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> translations(Map<String, Object> state) {
        Map<String, Object> all = state.get("translations") instanceof Map<?, ?> existing
                ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
        state.put("translations", all);
        return all;
    }

    private void requireLanguage(UUID projectId, String language) {
        if (language == null || !languages.enabledCodes(projectId).contains(language)) {
            throw new ValidationFailedException("The language is not one of this project.",
                    List.of(new ErrorItem("language", "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
        }
    }
}
