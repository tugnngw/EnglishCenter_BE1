package com.sba301.englishcenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper đọc/ghi các cột JSONB ({@code properties}, {@code content_data}).
 * <p>
 * Cột JSONB được ánh xạ trong entity dưới dạng String (không dùng Hibernate JSON type để
 * giữ đúng cấu hình hiện có của dự án), nên cần parse thủ công khi trả về cho frontend.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parse chuỗi JSON thành JsonNode.
     * Nếu chuỗi rỗng hoặc không phải JSON hợp lệ thì trả về null thay vì ném exception,
     * để một dữ liệu JSONB hỏng không làm sập cả endpoint.
     */
    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            log.warn("Không parse được JSON: {}", json);
            return null;
        }
    }

    /**
     * Đọc một field dạng boolean từ JSON.
     *
     * @param json      Chuỗi JSON
     * @param fieldName Tên field
     * @param defaultValue Giá trị mặc định khi thiếu hoặc không đúng kiểu
     */
    public static boolean readBoolean(String json, String fieldName, boolean defaultValue) {
        JsonNode node = parse(json);
        if (node == null || !node.hasNonNull(fieldName)) {
            return defaultValue;
        }
        JsonNode value = node.get(fieldName);
        return value.isBoolean() ? value.booleanValue() : defaultValue;
    }

    /**
     * Đọc một field dạng string từ JSON.
     */
    public static String readString(String json, String fieldName) {
        JsonNode node = parse(json);
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value.isTextual() ? value.textValue() : null;
    }

    /**
     * Chuẩn hóa chuỗi do client gửi thành JSON hợp lệ trước khi lưu vào cột JSONB.
     * Client có thể gửi một object đã serialize, hoặc gửi mảng JSON.
     *
     * @param raw Chuỗi client gửi lên
     * @return Chuỗi JSON hợp lệ, hoặc null nếu client không gửi gì
     * @throws IllegalArgumentException nếu chuỗi không phải JSON hợp lệ
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Dữ liệu JSON không hợp lệ");
        }
    }

    /**
     * Serialize một giá trị bất kỳ thành chuỗi JSON.
     * <p>
     * Dùng khi service cần TỰ DỰNG nội dung {@code content_data} (vd: danh sách
     * {@code setIds} của nội dung 'vocabulary') mà không muốn ghép chuỗi thủ công.
     *
     * @param value giá trị cần serialize (null → "null")
     * @return chuỗi JSON
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không serialize được JSON", ex);
        }
    }

    /** ObjectMapper dùng chung — cho phép service tạo {@code ObjectNode}. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}