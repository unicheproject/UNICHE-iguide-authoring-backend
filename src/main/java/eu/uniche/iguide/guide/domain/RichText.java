package eu.uniche.iguide.guide.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The rich text of a TEXT block in one language (DECISIONS.md D-022): ordered paragraph, heading, and
 * {@code asset} nodes. An {@code asset} node carries only an {@code assetId} and becomes an INLINE binding of
 * the Guide ({@code 03} section 4, DECISIONS.md D-025).
 */
public record RichText(List<Node> blocks) {

    public static final String ASSET = "asset";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(String type, String text, Integer level, String assetId) {
    }

    /** Whether a node holds anything a visitor sees: non-blank text, or an image (DECISIONS.md D-038). */
    public boolean hasContent() {
        return blocks != null && blocks.stream().anyMatch(n -> (n.text() != null && !n.text().isBlank())
                || (ASSET.equals(n.type()) && n.assetId() != null));
    }
}
