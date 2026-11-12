package eu.uniche.iguide.platform.catalogue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Reads the Catalogue's {@code date-time} strings leniently.
 *
 * <p>The Catalogue documents its timestamps as {@code date-time} and sends ISO-8601 instants
 * ({@code 2026-08-12T09:30:00Z}). An offset form is accepted too, and a form without an offset is read
 * as UTC, the Catalogue's own zone. Anything else, and an absent value, is {@code null}: a timestamp is
 * shown to authors, never used to decide access, so it must not make a Project unreadable.
 */
public final class CatalogueTimestamps {

    private CatalogueTimestamps() {
    }

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException notWithOffset) {
            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException unparseable) {
                return null;
            }
        }
    }
}
