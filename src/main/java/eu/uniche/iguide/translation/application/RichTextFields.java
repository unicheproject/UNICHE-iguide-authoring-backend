package eu.uniche.iguide.translation.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Guide Block's rich text ({@code {"blocks": [{type, text, level, assetId}]}}, DECISIONS.md D-022) as gateway fields
 * and back (DECISIONS.md D-036): each node with text becomes the field {@code <prefix><i>}, {@code i} its index; the
 * translation keeps every node's type and level, and {@code asset} nodes as they are.
 */
final class RichTextFields {

    private RichTextFields() {
    }

    /** The non-blank node texts of a document as {@code <prefix><i>} fields; empty for anything else. */
    static Map<String, String> flatten(Object document, String prefix) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<?> nodes = nodes(document);
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) instanceof Map<?, ?> node && node.get("text") instanceof String text && !text.isBlank()) {
                fields.put(prefix + i, text);
            }
        }
        return fields;
    }

    /**
     * The document with each node's text replaced by its {@code <prefix><i>} translation; a node without one keeps
     * its own.
     */
    static Map<String, Object> rebuild(Object document, String prefix, Map<String, String> translated) {
        List<Object> nodes = new ArrayList<>();
        List<?> source = nodes(document);
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i) instanceof Map<?, ?> node) {
                Map<String, Object> copy = new LinkedHashMap<>();
                node.forEach((key, value) -> copy.put(String.valueOf(key), value));
                String text = translated.get(prefix + i);
                if (text != null) {
                    copy.put("text", text);
                }
                nodes.add(copy);
            }
        }
        Map<String, Object> rebuilt = new LinkedHashMap<>();
        rebuilt.put("blocks", nodes);
        return rebuilt;
    }

    /**
     * What a person sees of a document, to tell whether a save changed it: each node's type, level, asset, and
     * stripped text; blank-text nodes dropped, as saving drops them. {@code null} for an empty document.
     */
    static List<List<Object>> signature(Object document) {
        List<List<Object>> signature = new ArrayList<>();
        for (Object item : nodes(document)) {
            if (item instanceof Map<?, ?> node) {
                Object text = node.get("text") instanceof String s ? (s.isBlank() ? null : s.strip()) : null;
                if (text == null && node.get("assetId") == null) {
                    continue;
                }
                Object level = node.get("level") instanceof Number n ? n.intValue() : node.get("level");
                signature.add(Arrays.asList(node.get("type"), level, node.get("assetId"), text));
            }
        }
        return signature.isEmpty() ? null : signature;
    }

    static boolean isDocument(Object value) {
        return value instanceof Map<?, ?> map && map.containsKey("blocks");
    }

    private static List<?> nodes(Object document) {
        return document instanceof Map<?, ?> map && map.get("blocks") instanceof List<?> list ? list : List.of();
    }
}
