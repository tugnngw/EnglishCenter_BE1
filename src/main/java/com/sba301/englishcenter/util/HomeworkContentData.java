package com.sba301.englishcenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.dto.response.HomeworkRubricCriterion;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc các field số / chuỗi trong {@code course_content.content_data} (JSONB).
 * <p>
 * {@link JsonUtil#readString} chỉ đọc được giá trị textual, trong khi
 * {@code maxScore}, {@code passingScore}, {@code maxAttempts} được frontend ghi
 * dạng NUMBER. Helper này đọc cả hai kiểu nên không phụ thuộc cách FE serialize.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HomeworkContentData {

    /**
     * Đọc field dạng số (chấp nhận cả JSON number và JSON string).
     *
     * @return giá trị số, hoặc null nếu thiếu / không parse được
     */
    public static BigDecimal readNumber(String contentData, String fieldName) {
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.textValue().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Đọc field dạng chuỗi (chấp nhận cả JSON string và JSON number → trả về text).
     */
    public static String readText(String contentData, String fieldName) {
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return value.asText();
        }
        return null;
    }

    /**
     * Đọc danh sách đuôi file được phép nộp ({@code content_data.allowedFileTypes}).
     *
     * <p>Admin có thể cấu hình nhiều định dạng (vd {@code ["pdf", "docx"]}).
     * Thiếu / rỗng / sai định dạng → trả về {@code null} để caller dùng mặc định.
     *
     * @return danh sách đuôi file đã chuẩn hoá (không có dấu chấm, chữ thường), hoặc null
     */
    public static List<String> readAllowedFileTypes(String contentData) {
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull("allowedFileTypes")) {
            return null;
        }
        JsonNode value = node.get("allowedFileTypes");
        List<String> result = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isTextual()) {
                    addExtension(result, item.textValue());
                }
            }
        } else if (value.isTextual()) {
            // Chấp nhận dạng chuỗi "pdf,docx" cho tương thích.
            for (String part : value.textValue().split(",")) {
                addExtension(result, part);
            }
        }
        return result.isEmpty() ? null : List.copyOf(result);
    }

    /**
     * Đọc rubric chấm điểm của bài tập ({@code content_data.rubric}).
     * <p>
     * Mỗi phần tử: {@code { criterion, description?, maxScore? }}.
     * Thiếu / sai định dạng → trả về {@link List#of()} (bài tập không có rubric).
     *
     * @return danh sách tiêu chí chấm (không null)
     */
    public static List<HomeworkRubricCriterion> readRubric(String contentData) {
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull("rubric") || !node.get("rubric").isArray()) {
            return List.of();
        }
        List<HomeworkRubricCriterion> result = new ArrayList<>();
        for (JsonNode item : node.get("rubric")) {
            String criterion = item.path("criterion").asText("").trim();
            if (criterion.isEmpty()) {
                continue;
            }
            String description = item.hasNonNull("description")
                    ? item.get("description").asText("").trim()
                    : null;
            BigDecimal maxScore = null;
            JsonNode maxNode = item.get("maxScore");
            if (maxNode != null && !maxNode.isNull()) {
                if (maxNode.isNumber()) {
                    maxScore = maxNode.decimalValue();
                } else if (maxNode.isTextual()) {
                    try {
                        maxScore = new BigDecimal(maxNode.textValue().trim());
                    } catch (NumberFormatException ex) {
                        maxScore = null;
                    }
                }
            }
            result.add(HomeworkRubricCriterion.builder()
                    .criterion(criterion)
                    .description(description)
                    .maxScore(maxScore)
                    .build());
        }
        return List.copyOf(result);
    }

    /** Chuẩn hoá 1 đuôi file: bỏ dấu chấm đầu, hạ chữ thường, bỏ rỗng. */
    private static void addExtension(List<String> target, String raw) {
        if (raw == null) {
            return;
        }
        String clean = raw.trim().toLowerCase();
        while (clean.startsWith(".")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty()) {
            return;
        }
        if (!target.contains(clean)) {
            target.add(clean);
        }
    }
}
