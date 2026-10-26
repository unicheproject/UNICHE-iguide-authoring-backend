package eu.uniche.iguide.translation.application;

import eu.uniche.iguide.translation.application.ProvenanceStore.Subject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What a person's action does to the provenance of a language ({@code 03} section 1; DECISIONS.md D-035, D-036). The
 * provenance that counts is the one of the text the Translation workspace shows: what the working copy carries for
 * the language when it carries anything, else the live row's. Changes to an ACTIVE Resource's text, its own or its
 * stops' or blocks', go into its working copy, so their provenance does too, and reaches the rows only on apply.
 * Assets have no working copy: theirs is always the live row's.
 */
@Component
public class TranslationReview {

    private final ProvenanceStore store;
    private final TransactionTemplate transactionTemplate;

    public TranslationReview(ProvenanceStore store, TransactionTemplate transactionTemplate) {
        this.store = store;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * A person changed the language's text through a translation endpoint: an unreviewed machine translation counts
     * as reviewed by them ({@code 03} section 1). {@code pending}: the change went into the working copy.
     */
    public void edited(UUID projectId, Subject texts, String language, String subject, boolean pending) {
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(status -> {
            if (!pending) {
                Map<String, Object> live = store.live(projectId, texts, language);
                if (ProvenanceStore.unreviewed(live)) {
                    store.writeLive(projectId, texts, language, ProvenanceStore.reviewed(live, subject, now));
                }
                return;
            }
            String key = texts.key(language);
            Map<String, Map<String, Object>> carried = store.pending(projectId, texts.copyType(), texts.copyId());
            Map<String, Object> current = carried.containsKey(key) ? carried.get(key)
                    : store.live(projectId, texts, language);
            if (ProvenanceStore.unreviewed(current)) {
                carried.put(key, ProvenanceStore.reviewed(current, subject, now));
                store.writePending(projectId, texts.copyType(), texts.copyId(), carried);
            }
        });
    }

    /** A person emptied the language: no text is left to carry a machine origin. */
    public void cleared(UUID projectId, Subject texts, String language, boolean pending) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!pending) {
                store.writeLive(projectId, texts, language, null);
                return;
            }
            String key = texts.key(language);
            Map<String, Map<String, Object>> carried = store.pending(projectId, texts.copyType(), texts.copyId());
            if (carried.get(key) != null || store.live(projectId, texts, language) != null) {
                carried.put(key, null);
                store.writePending(projectId, texts.copyType(), texts.copyId(), carried);
            }
        });
    }

    /**
     * {@code :mark-reviewed}: the machine translation the workspace shows is reviewed by {@code subject}; already
     * reviewed stays as it is. {@code null} when the language shows no machine translation.
     */
    public ProvenanceStore.Entry markReviewed(UUID projectId, Subject texts, String language, String subject) {
        Instant now = Instant.now();
        return transactionTemplate.execute(status -> {
            String key = texts.key(language);
            Map<String, Map<String, Object>> carried = texts.copyType() == null ? new LinkedHashMap<>()
                    : store.pending(projectId, texts.copyType(), texts.copyId());
            if (carried.containsKey(key)) {
                Map<String, Object> current = carried.get(key);
                if (!ProvenanceStore.machineGenerated(current)) {
                    return null;
                }
                Map<String, Object> reviewed = ProvenanceStore.reviewed(current, subject, now);
                carried.put(key, reviewed);
                store.writePending(projectId, texts.copyType(), texts.copyId(), carried);
                return texts.entry(language, reviewed, true);
            }
            Map<String, Object> live = store.live(projectId, texts, language);
            if (!ProvenanceStore.machineGenerated(live)) {
                return null;
            }
            Map<String, Object> reviewed = ProvenanceStore.reviewed(live, subject, now);
            store.writeLive(projectId, texts, language, reviewed);
            return texts.entry(language, reviewed, false);
        });
    }

    /** A machine translation was written: on the live row for a DRAFT, carried by the working copy otherwise. */
    public void generated(UUID projectId, Subject texts, String language, Map<String, Object> provenance,
                          boolean pending) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!pending) {
                store.writeLive(projectId, texts, language, provenance);
                return;
            }
            Map<String, Map<String, Object>> carried = store.pending(projectId, texts.copyType(), texts.copyId());
            carried.put(texts.key(language), provenance);
            store.writePending(projectId, texts.copyType(), texts.copyId(), carried);
        });
    }
}
