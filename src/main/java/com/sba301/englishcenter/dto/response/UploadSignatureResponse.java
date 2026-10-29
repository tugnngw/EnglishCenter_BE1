package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.Map;

/**
 * Chữ ký upload Cloudinary cấp cho frontend.
 * <p>
 * Frontend dùng {@code signature} + {@code timestamp} + {@code apiKey} để POST thẳng
 * lên Cloudinary. {@code apiSecret} KHÔNG BAO GIỜ trả về frontend.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadSignatureResponse {

    /** API key (public) — kèm trong form upload. */
    String apiKey;

    /** Chữ ký SHA-1/SHA-256 của payload. */
    String signature;

    /** Unix timestamp (giây) — kèm trong form upload. */
    Long timestamp;

    /** Tên cloud của Cloudinary. */
    String cloudName;

    /**
     * Loại tài nguyên do BE quyết định: {@code image} | {@code raw} | {@code video}.
     * <p>
     * Không để client tự chọn — BE chọn theo quy tắc nghiệp vụ (ảnh → image,
     * PDF/Word → raw, audio/video → video).
     */
    String resourceType;

    /**
     * Delivery type của file: {@code authenticated} (private — bài nộp học viên)
     * hoặc {@code upload} (public — tài liệu đính kèm của bài tập).
     * <p>
     * FE PHẢI gửi kèm {@code type} này khi POST upload, nếu không file sẽ mặc định
     * {@code upload} (public) và không ai xem được bằng URL có thời hạn.
     */
    String deliveryType;

    /** Thư mục đích trên Cloudinary, VD {@code courses/{courseId}/homework}. */
    String folder;

    /** Tham số phụ cần gửi kèm khi upload (VD {@code public_id}). */
    Map<String, Object> params;
}
