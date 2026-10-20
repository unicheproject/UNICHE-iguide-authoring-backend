package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.TextDirection;

public record LanguageInput(String languageCode, String displayName, TextDirection direction, boolean isDefault) {
}
