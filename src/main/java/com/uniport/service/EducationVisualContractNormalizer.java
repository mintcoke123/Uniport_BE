package com.uniport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class EducationVisualContractNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TEMPLATE_TYPES = Set.of("checklist", "comparison", "flow", "formula", "table", "diagram", "stat");
    private static final Set<String> ASSET_RENDERERS = Set.of("raster_asset", "chart_asset", "character_raster");
    private static final Map<Integer, ManifestVisual> MANIFEST_BY_IDX = loadManifest();

    private EducationVisualContractNormalizer() {
    }

    static NormalizedVisual normalize(String imageType,
                                      String imageTypeOld,
                                      String storedRendererType,
                                      String storedVisualType,
                                      String storedVisualKey,
                                      String storedComponentKey,
                                      String storedAssetKey,
                                      String storedImageDelivery,
                                      String storedImageUrl,
                                      String assetId,
                                      Integer sourceIdx,
                                      JsonNode cardVisual,
                                      JsonNode visualPayload,
                                      JsonNode renderPolicy,
                                      String title,
                                      String text) {
        ManifestVisual manifestVisual = sourceIdx == null ? null : MANIFEST_BY_IDX.get(sourceIdx);
        if (manifestVisual != null) {
            return manifestVisual.toNormalized(cardVisual);
        }

        String normalizedImageType = normalizeImageType(imageType, imageTypeOld, cardVisual);
        String templateVisualType = resolveTemplateVisualType(normalizedImageType, cardVisual, visualPayload);
        String rendererType = resolveRendererType(storedRendererType, storedVisualType, normalizedImageType, templateVisualType, cardVisual);
        String visualType = resolveVisualType(storedVisualType, rendererType, templateVisualType);
        String visualKey = resolveVisualKey(storedVisualKey, assetId, sourceIdx, rendererType, templateVisualType);
        String componentKey = resolveComponentKey(storedComponentKey, rendererType, visualKey);
        String assetKey = resolveAssetKey(storedAssetKey, assetId, rendererType);
        String imageDelivery = resolveImageDelivery(storedImageDelivery, rendererType);
        String imageUrl = isAssetRenderer(rendererType) ? blankToNull(storedImageUrl) : null;
        Object cardVisualValue = sanitizeJsonValue(cardVisual);
        Object payload = isAssetRenderer(rendererType) || "none".equals(rendererType)
                ? null
                : normalizePayload(templateVisualType, visualPayload == null ? cardVisual : visualPayload, title, text);
        Object renderPolicyValue = sanitizeJsonValue(renderPolicy);
        if (renderPolicyValue == null) {
            renderPolicyValue = defaultRenderPolicy(rendererType);
        }
        String responseImageType = "component".equals(rendererType) && templateVisualType != null
                ? templateVisualType
                : normalizedImageType;

        return new NormalizedVisual(
                responseImageType,
                templateVisualType,
                rendererType,
                visualType,
                visualKey,
                componentKey,
                assetKey,
                imageDelivery,
                imageUrl,
                cardVisualValue,
                payload,
                renderPolicyValue);
    }

    private static Map<Integer, ManifestVisual> loadManifest() {
        try (InputStream inputStream = new ClassPathResource("education/kmp_render_manifest.json").getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            JsonNode cards = root.path("cards");
            if (!cards.isArray()) {
                return Map.of();
            }
            Map<Integer, ManifestVisual> byIdx = new LinkedHashMap<>();
            for (JsonNode card : cards) {
                if (card.hasNonNull("idx")) {
                    byIdx.put(card.path("idx").asInt(), ManifestVisual.from(card));
                }
            }
            return Map.copyOf(byIdx);
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static String normalizeImageType(String imageType, String imageTypeOld, JsonNode visualNode) {
        String normalized = imageType == null ? "" : imageType.trim().toLowerCase(Locale.ROOT);
        if ("image".equals(normalized) && hasBlankImageUrl(visualNode) && isTemplateType(imageTypeOld)) {
            String legacyType = imageTypeOld.trim().toLowerCase(Locale.ROOT);
            return "diagram".equals(legacyType) ? normalized : legacyType;
        }
        return normalized.isBlank() ? imageType : normalized;
    }

    private static String resolveTemplateVisualType(String imageType, JsonNode cardVisual, JsonNode visualPayload) {
        String payloadType = Optional.ofNullable(textField(visualPayload, "type"))
                .orElse(textField(visualPayload, "template_visual_type"));
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

    private static String resolveRendererType(String storedRendererType,
                                              String storedVisualType,
                                              String imageType,
                                              String templateVisualType,
                                              JsonNode visualNode) {
        if (storedRendererType != null && !storedRendererType.isBlank()) {
            return storedRendererType;
        }
        if (templateVisualType != null) {
            return "component";
        }
        if (storedVisualType != null && !storedVisualType.isBlank() && !hasBlankImageUrl(visualNode)) {
            return storedVisualType;
        }
        String normalized = imageType == null ? "" : imageType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "placeholder" -> "none";
            case "image" -> hasUsableImageUrl(visualNode) ? "raster_asset" : "none";
            case "diagram", "flow", "formula", "comparison", "checklist", "table", "stat" -> "component";
            default -> "component";
        };
    }

    private static String resolveVisualType(String storedValue, String rendererType, String templateVisualType) {
        if ("component".equals(rendererType)) {
            return "component";
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return rendererType;
    }

    private static String resolveVisualKey(String storedValue, String assetId, Integer sourceIdx, String rendererType, String templateVisualType) {
        if ("none".equals(rendererType)) {
            return null;
        }
        if ("component".equals(rendererType) && templateVisualType != null) {
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

    private static String resolveComponentKey(String storedValue, String rendererType, String visualKey) {
        if (!"component".equals(rendererType)) {
            return null;
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return visualKey;
    }

    private static String resolveAssetKey(String storedValue, String assetId, String rendererType) {
        if (!isAssetRenderer(rendererType)) {
            return null;
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return assetId;
    }

    private static String resolveImageDelivery(String storedValue, String rendererType) {
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue;
        }
        return isAssetRenderer(rendererType) ? "remote_url" : "none";
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

    private static boolean isAssetRenderer(String value) {
        return value != null && ASSET_RENDERERS.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean hasBlankImageUrl(JsonNode visualNode) {
        return visualNode != null
                && visualNode.isObject()
                && visualNode.has("image_url")
                && visualNode.path("image_url").asText("").isBlank();
    }

    private static boolean hasUsableImageUrl(JsonNode visualNode) {
        return visualNode != null
                && visualNode.isObject()
                && visualNode.has("image_url")
                && !visualNode.path("image_url").asText("").isBlank();
    }

    private static Object normalizePayload(String templateVisualType, JsonNode payloadNode, String title, String text) {
        Object sanitized = sanitizeJsonValue(payloadNode);
        if (templateVisualType == null) {
            return sanitized == null ? null : sanitized;
        }

        Map<String, Object> payload = sanitized instanceof Map<?, ?> map
                ? copyStringObjectMap(map)
                : new LinkedHashMap<>();
        payload.putIfAbsent("type", templateVisualType);
        payload.putIfAbsent("template_visual_type", templateVisualType);
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
                payload.put("equation", lines.getFirst());
                payload.put("variables", List.of());
            }
            case "table" -> {
                payload.put("headers", List.of("항목", "내용"));
                payload.put("rows", lines.stream().map(line -> List.of(line, "")).toList());
            }
            case "diagram" -> payload.put("items", textItemMaps(lines));
            case "stat" -> {
                payload.put("label", title);
                payload.put("value", lines.getFirst());
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

    private static Object defaultRenderPolicy(String rendererType) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("fit", "contain");
        policy.put("allow_crop", false);
        if (isAssetRenderer(rendererType)) {
            policy.put("max_height_dp", 280);
        }
        return policy;
    }

    private static String textField(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    private static String resolveManifestImageUrl(String imageUrl, String publicPath) {
        String configuredBase = resolveConfiguredAssetBaseUrl();
        if (configuredBase == null || configuredBase.isBlank() || publicPath == null || publicPath.isBlank()) {
            return blankToNull(imageUrl);
        }
        String base = configuredBase.replaceFirst("/+$", "");
        String path = publicPath.startsWith("/") ? publicPath : "/" + publicPath;
        if (base.endsWith("/education-assets") && path.startsWith("/education-assets/")) {
            path = path.substring("/education-assets".length());
        }
        return base + path;
    }

    private static String resolveConfiguredAssetBaseUrl() {
        for (String name : List.of("UNIPORT_EDU_ASSET_BASE_URL", "EDUCATION_ASSET_PUBLIC_BASE_URL")) {
            String systemPropertyValue = System.getProperty(name);
            if (systemPropertyValue != null && !systemPropertyValue.isBlank()) {
                return systemPropertyValue;
            }
            String environmentValue = System.getenv(name);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    record NormalizedVisual(String imageType,
                            String templateVisualType,
                            String rendererType,
                            String visualType,
                            String visualKey,
                            String componentKey,
                            String assetKey,
                            String imageDelivery,
                            String imageUrl,
                            Object cardVisual,
                            Object payload,
                            Object renderPolicy) {
    }

    private record ManifestVisual(String rendererType,
                                  String visualType,
                                  String visualKey,
                                  String componentKey,
                                  String assetKey,
                                  String imageDelivery,
                                  String imageUrl,
                                  String publicPath,
                                  Map<String, Object> payload,
                                  Map<String, Object> renderPolicy) {

        static ManifestVisual from(JsonNode node) {
            return new ManifestVisual(
                    textField(node, "renderer_type"),
                    textField(node, "visual_type"),
                    textField(node, "visual_key"),
                    textField(node, "component_key"),
                    textField(node, "asset_key"),
                    textField(node, "image_delivery"),
                    textField(node, "image_url"),
                    textField(node, "public_path"),
                    objectMap(node.get("visual_payload")),
                    objectMap(node.get("render_policy")));
        }

        NormalizedVisual toNormalized(JsonNode cardVisual) {
            String templateVisualType = templateVisualType(payload, componentKey);
            String responseImageType = isAssetRenderer(rendererType) ? rendererType : templateVisualType;
            return new NormalizedVisual(
                    responseImageType,
                    templateVisualType,
                    rendererType,
                    visualType,
                    visualKey,
                    componentKey,
                    assetKey,
                    imageDelivery,
                    resolveManifestImageUrl(imageUrl, publicPath),
                    sanitizeJsonValue(cardVisual),
                    payload,
                    renderPolicy == null ? defaultRenderPolicy(rendererType) : renderPolicy);
        }

        private static Map<String, Object> objectMap(JsonNode node) {
            Object value = sanitizeJsonValue(node);
            if (value instanceof Map<?, ?> map) {
                return copyStringObjectMap(map);
            }
            return null;
        }

        private static String templateVisualType(Map<String, Object> payload, String componentKey) {
            Object type = payload == null ? null : payload.get("type");
            if (type == null && payload != null) {
                type = payload.get("template_visual_type");
            }
            if (type != null && !type.toString().isBlank()) {
                return type.toString();
            }
            if (componentKey != null && componentKey.startsWith("template_")) {
                return componentKey.substring("template_".length());
            }
            return null;
        }
    }
}
