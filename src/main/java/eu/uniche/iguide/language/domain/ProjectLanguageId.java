package eu.uniche.iguide.language.domain;

import java.io.Serializable;
import java.util.UUID;

/** Composite key of {@link ProjectLanguage}: a language exists once per Project. */
public record ProjectLanguageId(UUID projectId, String languageCode) implements Serializable {

    /** For JPA, which instantiates id classes reflectively. */
    public ProjectLanguageId() {
        this(null, null);
    }
}
