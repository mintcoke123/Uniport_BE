package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class EducationVisualContractNormalizer {

    private static final Set<String> TEMPLATE_TYPES = Set.of("checklist", "comparison", "flow", "formula", "table", "diagram", "stat");
    private static final Set<String> ASSET_TYPES = Set.of("raster_asset", "chart_asset", "character_raster");

    private EducationVisualContractNormalizer() {
    }

    static NormalizedVisual normalize(String imageType,
                                      String imageTypeOld,
                                      String storedVisualType,
                                      String storedVisualKey,
                                      String storedAssetKey,
                                      String assetId,
                                      Integer sourceIdx,
                                      JsonNode cardVisual,
                                      JsonNode visualPayload,
                                      String title,
                                      String text) {
        String normalizedImageType = normalizeImageType(imageType, imageTypeOld, cardVisual);
        String templateVisualType = resolveTemplateVisualType(normalizedImageType, cardVisual, visualPayload);
        String visualType = resolveVisualType(storedVisualType, normalizedImageType, templateVisualType, cardVisual);
        String visualKey = resolveVisualKey(storedVisualKey, assetId, sourceIdx, visualType, templateVisualType);
        String assetKey = resolveAssetKey(storedAssetKey, assetId, visualType);
        Object cardVisualValue = sanitizeJsonValue(cardVisual);
        Object payload = normalizePayload(templateVisualType, visualPayload == null ? cardVisual : visualPayload, title, text);
        String responseImageType = "component".equals(visualType) && templateVisualType != null ? templateVisualType : normalizedImageType;
        return new NormalizedVisual(responseImageType, templateVisualType, visualType, visualKey, assetKey, cardVisualValue, payload);
    }

    private static String normalizeImageType(String imageType, String imageTypeOld, JsonNode visualNode) {
        String normalized = imageType == null ? "" : imageType.trim().toLowerCase(Locale.ROOT);
        if ("image".equals(normalized) && hasBlankImageUrl(visualNode) && isTemplateType(imageTypeOld)) {
            return imageTypeOld.trim().toLowerCase(Locale.ROOT);
        }
        if ("image".equals(normalized) && hasBlankImageUrl(visualNode)) {
            return "diagram";
        }
        return normalized.isBlank() ? imageType : normalized;
    }

    private static String resolveTemplateVisualType(String imageType, JsonNode cardVisual, JsonNode visualPayload) {
        String payloadType = textField(visualPayload, "template_visual_type");
        if (isTemplateType(payloadType)) {
            return payloadType;
        }
        String normalized = imageType == null ? "" : imageType.trim().toLowerCase(Locale.ROOT);
        if (isTemplateType(normalized)) {
            return normalized;
        }
        if ("chart".equals(normalized)) {
            return "diagram";
        }
        return inferTemplateVisualType(cardVisual).orElse(null);
    }

    private static String resolveVisualType(String storedValue, String imageType, String templateVisualType, JsonNode visualNode) {
        if (templateVisualType != null) {
            return "component";
        }
        if (storedValue != null && !storedValue.isBlank() && !hasBlankImageUrl(visualNode)) {
            return storedValue;
        }
        String normalized = imageType == null ? "" : imageType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "placeholder" -> "none";
            case "image" -> "raster_asset";
            case "diagram", "flow", "formula", "comparison", "checklist" -> "component";
            default -> "component";
        };
    }

    private static String resolveVisualKey(String storedValue, String assetId, Integer sourceIdx, String visualType, String templateVisualType) {
        if ("component".equals(visualType) && templateVisualType != null) {
            return "template_" + templateVisualType;
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        if (assetId != null && !assetId.isBlank()) {
            return assetId;
        }
        return sourceIdx == null ? null : "education_card_" + sourceIdx;
    }

    private static String resolveAssetKey(String storedValue, String assetId, String visualType) {
        if (!ASSET_TYPES.contains(visualType)) {
            return null;
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return assetId;
    }

    private static Optional<String> inferTemplateVisualType(JsonNode visualNode) {
        if (visualNode == null || !visualNode.isObject()) {
            return Optional.empty();
        }
        if (visualNode.has("left") && visualNode.has("right")) {
            return Optional.of("comparison");
        }
        if (visualNode.has("steps")) {
            return Optional.of("flow");
        }
        if (visualNode.has("equation")) {
            return Optional.of("formula");
        }
        if (visualNode.has("headers") || visualNode.has("rows")) {
            return Optional.of("table");
        }
        if (visualNode.has("value") && visualNode.has("label")) {
            return Optional.of("stat");
        }
        if (visualNode.has("items")) {
            return Optional.of("checklist");
        }
        if (visualNode.has("nodes")) {
            return Optional.of("diagram");
        }
        return Optional.empty();
    }

    private static boolean isTemplateType(String value) {
        return value != null && TEMPLATE_TYPES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean hasBlankImageUrl(JsonNode visualNode) {
        return visualNode != null
                && visualNode.isObject()
                && visualNode.has("image_url")
                && visualNode.path("image_url").asText("").isBlank();
    }

    private static Object normalizePayload(String templateVisualType, JsonNode payloadNode, String title, String text) {
        Object sanitized = sanitizeJsonValue(payloadNode);
        if (templateVisualType == null) {
            return sanitized == null ? new LinkedHashMap<String, Object>() : sanitized;
        }

        Map<String, Object> payload = sanitized instanceof Map<?, ?> map
                ? copyStringObjectMap(map)
                : new LinkedHashMap<>();
        payload.put("template_visual_type", templateVisualType);
        if (!hasRenderablePayload(templateVisualType, payload)) {
            addFallbackPayload(templateVisualType, payload, title, text);
        }
        return payload;
    }

    private static boolean hasRenderablePayload(String templateVisualType, Map<String, Object> payload) {
        return switch (templateVisualType) {
            case "checklist" -> hasNonEmptyList(payload, "items");
            case "comparison" -> payload.containsKey("left") && payload.containsKey("right");
            case "flow" -> hasNonEmptyList(payload, "steps");
            case "formula" -> hasTextValue(payload, "equation");
            case "table" -> hasNonEmptyList(payload, "rows");
            case "diagram" -> hasNonEmptyList(payload, "nodes") || hasNonEmptyList(payload, "items");
            case "stat" -> hasTextValue(payload, "value");
            default -> false;
        };
    }

    private static void addFallbackPayload(String templateVisualType, Map<String, Object> payload, String title, String text) {
        List<String> lines = contentLines(text, title);
        switch (templateVisualType) {
            case "checklist" -> payload.put("items", textItemMaps(lines));
            case "comparison" -> {
                int split = Math.max(1, (lines.size() + 1) / 2);
                payload.put("left", comparisonSide("기준 A", lines.subList(0, split)));
                payload.put("right", comparisonSide("기준 B", lines.subList(split, lines.size()).isEmpty()
                        ? List.of(lines.get(0))
                        : lines.subList(split, lines.size())));
            }
            case "flow" -> payload.put("steps", lines.stream().map(line -> Map.<String, Object>of("title", line)).toList());
            case "formula" -> {
                payload.put("name", title);
                payload.put("equation", lines.get(0));
                payload.put("variables", List.of());
            }
            case "table" -> {
                payload.put("headers", List.of("항목", "내용"));
                payload.put("rows", lines.stream().map(line -> List.of(line, "")).toList());
            }
            case "diagram" -> payload.put("items", textItemMaps(lines));
            case "stat" -> {
                payload.put("label", title);
                payload.put("value", lines.get(0));
                payload.put("unit", "");
            }
            default -> {
            }
        }
    }

    private static Map<String, Object> comparisonSide(String label, List<String> items) {
        Map<String, Object> side = new LinkedHashMap<>();
        side.put("label", label);
        side.put("items", items);
        return side;
    }

    private static List<Map<String, Object>> textItemMaps(List<String> lines) {
        return lines.stream().map(line -> Map.<String, Object>of("text", line)).toList();
    }

    private static List<String> contentLines(String text, String fallback) {
        List<String> lines = new ArrayList<>();
        if (text != null) {
            for (String rawLine : text.split("\\R")) {
                String line = rawLine.strip().replaceFirst("^[•\\-*]\\s*", "");
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        if (lines.isEmpty() && fallback != null && !fallback.isBlank()) {
            lines.add(fallback);
        }
        if (lines.isEmpty()) {
            lines.add("핵심 내용");
        }
        return lines;
    }

    private static boolean hasNonEmptyList(Map<String, Object> payload, String key) {
        return payload.get(key) instanceof List<?> list && !list.isEmpty();
    }

    private static boolean hasTextValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null && !value.toString().isBlank();
    }

    private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return copy;
    }

    private static Object sanitizeJsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                if ("image_url".equals(entry.getKey()) && entry.getValue().asText("").isBlank()) {
                    return;
                }
                map.put(entry.getKey(), sanitizeJsonValue(entry.getValue()));
            });
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(sanitizeJsonValue(item)));
            return list;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }

    private static String textField(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    record NormalizedVisual(String imageType,
                            String templateVisualType,
                            String visualType,
                            String visualKey,
                            String assetKey,
                            Object cardVisual,
                            Object payload) {
    }
}
