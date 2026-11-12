package eu.uniche.iguide.translation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RichTextFieldsTest {

    private static final Map<String, Object> DOCUMENT = Map.of("blocks", List.of(
            Map.of("type", "heading", "level", 2, "text", "Αρχή"),
            Map.of("type", "asset", "assetId", "a1"),
            Map.of("type", "paragraph", "text", " "),
            Map.of("type", "paragraph", "text", "Κείμενο")));

    @Test
    void flattensTheNodesWithText() {
        assertThat(RichTextFields.flatten(DOCUMENT, "text.")).containsExactly(
                Map.entry("text.0", "Αρχή"), Map.entry("text.3", "Κείμενο"));
        assertThat(RichTextFields.flatten(null, "text.")).isEmpty();
    }

    @Test
    void rebuildsWithTheSameNodesAndTheTranslatedTexts() {
        Map<String, Object> rebuilt = RichTextFields.rebuild(DOCUMENT, "text.",
                Map.of("text.0", "Start", "text.3", "Text"));
        List<?> nodes = (List<?>) rebuilt.get("blocks");
        assertThat(nodes).hasSize(4);
        assertThat(nodes.get(0)).isEqualTo(Map.of("type", "heading", "level", 2, "text", "Start"));
        assertThat(nodes.get(1)).isEqualTo(Map.of("type", "asset", "assetId", "a1"));
        assertThat(nodes.get(2)).isEqualTo(Map.of("type", "paragraph", "text", " "));
        assertThat(nodes.get(3)).isEqualTo(Map.of("type", "paragraph", "text", "Text"));
    }

    @Test
    void theSignatureIgnoresBlankNodesAndSurroundingSpace() {
        Map<String, Object> same = Map.of("blocks", List.of(
                Map.of("type", "heading", "level", 2L, "text", "Αρχή "),
                Map.of("type", "asset", "assetId", "a1"),
                Map.of("type", "paragraph", "text", "Κείμενο")));
        assertThat(RichTextFields.signature(same)).isEqualTo(RichTextFields.signature(DOCUMENT));
        assertThat(RichTextFields.signature(Map.of("blocks", List.of()))).isNull();
        assertThat(RichTextFields.signature(Map.of("blocks", List.of(Map.of("type", "paragraph", "text", "Other")))))
                .isNotEqualTo(RichTextFields.signature(DOCUMENT));
    }
}
