package com.sba301.englishcenter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
