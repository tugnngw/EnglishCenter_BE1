package com.sba301.englishcenter.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc / ghi các cột JSONB dạng mảng (learning_paths.items, student_progress, practice_exams.answers) — Dev 5.
 * Thời gian ghi dạng chuỗi ISO (LocalDateTime UTC), bỏ qua field lạ để dữ liệu cũ vẫn đọc được.
 */
public final class JsonbMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonbMapper() {
    }

    /** Chuỗi JSONB mảng -> List (null / rỗng -> list rỗng có thể sửa). */
    public static <T> List<T> readList(String jsonb, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(jsonb)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(MAPPER.readValue(jsonb, type));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
}
