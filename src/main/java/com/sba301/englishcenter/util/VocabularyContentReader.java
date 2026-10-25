package com.sba301.englishcenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.dto.response.VocabularyWordResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc danh sách từ vựng từ cột JSONB {@code course_content.content_data}.
 * <p>
 * Hỗ trợ CẢ HAI shape đang dùng trong hệ thống:
 * <ul>
 *   <li>shape BỘ   → {@code {"items":[{ "word":"academic", … }, …]}}</li>
 *   <li>shape ĐƠN   → {@code {"word":"academic", "ipa":"…", "meaning":"…"}}</li>
 * </ul>
 * Shape nào không có {@code word} hợp lệ thì bị bỏ qua (không làm hỏng endpoint).
 */
public final class VocabularyContentReader {

    private VocabularyContentReader() {
    }

    /**
     * Đọc toàn bộ từ trong {@code content_data}.
     *
     * @param contentData chuỗi JSONB đã lưu
     * @return danh sách từ (không null, có thể rỗng)
     */
    public static List<VocabularyWordResponse> readWords(String contentData) {
        JsonNode root = JsonUtil.parse(contentData);
        if (root == null || !root.isObject()) {
            return List.of();
        }

        JsonNode items = root.get("items");
        if (items != null && items.isArray()) {
            List<VocabularyWordResponse> words = new ArrayList<>();
            for (JsonNode item : items) {
                VocabularyWordResponse word = toWord(item);
                if (word != null) {
                    words.add(word);
                }
            }
            return words;
        }

        VocabularyWordResponse single = toWord(root);
        return single == null ? List.of() : List.of(single);
    }

    /**
     * {@code true} nếu {@code content_data} theo shape BỘ nhiều từ ({@code items}).
     * Dùng để frontend hiển thị nhãn "Bộ từ" vs "Từ đơn".
     */
    public static boolean isSetShape(String contentData) {
        JsonNode root = JsonUtil.parse(contentData);
        return root != null && root.isObject() && root.get("items") != null
                && root.get("items").isArray();
    }

    /**
     * Chuyển 1 node JSON thành {@link VocabularyWordResponse}.
     * Trả về {@code null} nếu node không phải object hoặc thiếu {@code word}.
     */
    private static VocabularyWordResponse toWord(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }

        String word = text(node, "word");
        if (word.isBlank()) {
            return null;
        }

        // `ipa` là key chuẩn; `phonetic` là alias (màn client đọc key này).
        String ipa = text(node, "ipa");
        if (ipa.isBlank()) {
            ipa = text(node, "phonetic");
        }

        return VocabularyWordResponse.builder()
                .word(word)
                .ipa(ipa.isBlank() ? null : ipa)
                .phonetic(ipa.isBlank() ? null : ipa)
                .meaning(blankToNull(text(node, "meaning")))
                .audioUrl(blankToNull(text(node, "audioUrl")))
                .partOfSpeech(blankToNull(text(node, "partOfSpeech")))
                .example(blankToNull(text(node, "example")))
                .build();
    }

    /** Đọc field dạng chuỗi — trả về chuỗi rỗng nếu thiếu / sai kiểu. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue().trim() : "";
    }

    /** Rút gọn: chuỗi rỗng → null (để response không trả field rỗng). */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
