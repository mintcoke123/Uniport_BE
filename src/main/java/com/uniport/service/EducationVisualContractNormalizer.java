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
    private static final Map<Integer, VisualOverride> VISUAL_OVERRIDES = visualOverrides();

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
        VisualOverride override = sourceIdx == null ? null : VISUAL_OVERRIDES.get(sourceIdx);
        if (override != null) {
            return override.apply(cardVisual);
        }
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
            String legacyType = imageTypeOld.trim().toLowerCase(Locale.ROOT);
            return "diagram".equals(legacyType) ? normalized : legacyType;
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
            case "image" -> hasUsableImageUrl(visualNode) ? "raster_asset" : "none";
            case "diagram", "flow", "formula", "comparison", "checklist" -> "component";
            default -> "component";
        };
    }

    private static String resolveVisualKey(String storedValue, String assetId, Integer sourceIdx, String visualType, String templateVisualType) {
        if ("none".equals(visualType)) {
            return null;
        }
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

    private static boolean hasUsableImageUrl(JsonNode visualNode) {
        return visualNode != null
                && visualNode.isObject()
                && visualNode.has("image_url")
                && !visualNode.path("image_url").asText("").isBlank();
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

    private static Map<Integer, VisualOverride> visualOverrides() {
        Map<Integer, VisualOverride> overrides = new LinkedHashMap<>();
        overrides.put(15, component("checklist", checklist("수수료만 보지 않기", "자주 쓸 기능 확인", "국내·해외 투자 습관 맞추기")));
        overrides.put(16, raster("education_day2_account_opening_app"));
        overrides.put(17, raster("education_day2_account_turtle"));
        overrides.put(18, raster("education_day2_hts_mts_workspace"));
        overrides.put(19, component("flow", flow(
                step("MTS로 시작", "접근성과 반복 사용"),
                step("HTS 보조", "깊은 분석이 필요할 때"),
                step("실수 방지", "주문·조회 환경 정리"))));
        overrides.put(20, component("comparison", comparison(
                comparisonSide("코스피", List.of("대형 우량 기업", "상대적 안정성", "제1시장")),
                comparisonSide("코스닥", List.of("성장 기업", "변동성 큼", "IT·바이오·콘텐츠")))));
        overrides.put(23, component("checklist", checklist("자금 조달 통로", "시장의 검증", "정보 접근성", "거래 편의성")));
        overrides.put(24, component("flow", flow(
                step("정규장", "기본 거래 시간"),
                step("동시호가", "가격 결정 구간"),
                step("시간외", "장 종료 뒤 거래"))));
        overrides.put(25, component("flow", flow(
                step("정규장", "기본 거래 시간"),
                step("동시호가", "가격 결정 구간"),
                step("시간외", "장 종료 뒤 거래"))));
        overrides.put(26, component("diagram", diagram(
                node("주식", "기업 지분"),
                node("외환", "통화 교환"),
                node("상품", "원유·금·곡물"),
                node("파생", "미래 가격"))));
        overrides.put(27, component("diagram", diagram(
                node("코스피", "대형주"),
                node("코스닥", "성장주"),
                node("코넥스", "초기 기업"),
                node("ETF", "묶음 투자"))));
        overrides.put(28, component("flow", flow(
                step("통화 교환", "각국 돈의 가격"),
                step("환율 변화", "수출입과 물가 영향"),
                step("자금 흐름", "외국인 수급 연결"))));
        overrides.put(29, component("comparison", comparison(
                comparisonSide("상품시장", List.of("원유·금·곡물", "실물자산")),
                comparisonSide("파생상품", List.of("미래 가격", "위험 헤지")))));
        overrides.put(30, component("flow", flow(
                step("금리", "돈의 가격"),
                step("채권·환율", "자금 이동"),
                step("주식", "시장 반응"))));
        return Map.copyOf(overrides);
    }

    private static VisualOverride component(String templateVisualType, Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put("template_visual_type", templateVisualType);
        return new VisualOverride(templateVisualType, templateVisualType, "component", "template_" + templateVisualType, null, copy);
    }

    private static VisualOverride raster(String assetKey) {
        return new VisualOverride("image", null, "raster_asset", assetKey, assetKey, null);
    }

    private static Map<String, Object> checklist(String... items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", List.of(items).stream().map(item -> Map.<String, Object>of("text", item)).toList());
        return payload;
    }

    private static Map<String, Object> comparison(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("left", left);
        payload.put("right", right);
        return payload;
    }

    @SafeVarargs
    private static Map<String, Object> flow(Map<String, Object>... steps) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("steps", List.of(steps));
        return payload;
    }

    @SafeVarargs
    private static Map<String, Object> diagram(Map<String, Object>... nodes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodes", List.of(nodes));
        return payload;
    }

    private static Map<String, Object> step(String title, String description) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("title", title);
        step.put("description", description);
        return step;
    }

    private static Map<String, Object> node(String title, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("title", title);
        node.put("description", description);
        return node;
    }

    record NormalizedVisual(String imageType,
                            String templateVisualType,
                            String visualType,
                            String visualKey,
                            String assetKey,
                            Object cardVisual,
                            Object payload) {
    }

    private record VisualOverride(String imageType,
                                  String templateVisualType,
                                  String visualType,
                                  String visualKey,
                                  String assetKey,
                                  Map<String, Object> payload) {
        NormalizedVisual apply(JsonNode cardVisual) {
            Object sanitizedCardVisual = sanitizeJsonValue(cardVisual);
            Object normalizedPayload = payload == null ? sanitizedCardVisual : payload;
            return new NormalizedVisual(imageType, templateVisualType, visualType, visualKey, assetKey, sanitizedCardVisual, normalizedPayload);
        }
    }
}
