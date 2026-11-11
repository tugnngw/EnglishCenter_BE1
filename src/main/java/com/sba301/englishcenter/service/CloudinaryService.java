package com.sba301.englishcenter.service;

import java.util.Map;

/**
 * Tích hợp Cloudinary cho bài tập (tài liệu đính kèm + bài nộp).
 * <p>
 * <ul>
 *     <li>{@link #signUploadParams} — sinh chữ ký SHA-1 để FE upload thẳng lên Cloudinary;</li>
 *     <li>{@link #buildAuthenticatedUrl} — dựng URL có thời hạn cho file {@code authenticated}.</li>
 * </ul>
 * API secret chỉ nằm ở BE (biến môi trường {@code CLOUDINARY_API_SECRET}).
 */
public interface CloudinaryService {

    /** Loại tài nguyên Cloudinary. */
    String RESOURCE_IMAGE = "image";
    String RESOURCE_RAW = "raw";
    String RESOURCE_VIDEO = "video";

    /**
     * Delivery type của file bài nộp học viên: {@code authenticated} (private).
     * <p>
     * Bài nộp KHÔNG được public → phải ký {@code type=authenticated} lúc upload và
     * dựng URL có thời hạn khi xem.
     */
    String DELIVERY_AUTHENTICATED = "authenticated";

    /** Delivery type của tài liệu đính kèm của bài tập: {@code upload} (public). */
    String DELIVERY_UPLOAD = "upload";

    /**
     * Định dạng đầu ra ép cho file ghi âm (resource_type = video).
     * <p>
     * Cloudinary lưu audio dưới dạng video → trả {@code Content-Type} là
     * {@code video/webm} → thẻ {@code <audio>} không phát được.
     * Ép {@code .mp3} để nhận {@code audio/mpeg}.
     */
    String AUDIO_OUTPUT_FORMAT = "mp3";

    /** Cloudinary đã được cấu hình đầy đủ (cloud name + api key + api secret) hay chưa. */
    boolean isConfigured();

    /** Cloud name (trả về frontend để gọi API Cloudinary). */
    String getCloudName();

    /** API key (trả về frontend để gọi API Cloudinary). */
    String getApiKey();

    /**
     * Sinh chữ ký upload cho frontend.
     * <p>
     * Cloudinary ký SHA-1 của chuỗi: các tham số cần ký (VD {@code timestamp},
     * {@code folder}, {@code public_id}, {@code resource_type}) nối bằng {@code &},
     * sắp xếp theo thứ tự alphabet, cuối cùng nối {@code apiSecret}.
     * <p>
     * Tham số KHÔNG ký: {@code file}, {@code cloud_name}, {@code api_key}, {@code signature}.
     *
     * @param params các tham số sẽ gửi kèm lúc upload (không ký)
     * @return chữ ký hex
     */
    String signUploadParams(Map<String, Object> params);

    /**
     * Dựng URL có thời hạn cho file {@code authenticated} của bài nộp.
     * <p>
     * File bài nộp KHÔNG public → URL chỉ dùng được trong thời gian đặt trước.
     * <p>
     * Delivery type phải khớp với {@code type} đã dùng lúc upload
     * (xem {@link #buildAuthenticatedUrl} và {@code UploadService}).
     *
     * @param publicId          public_id của file
     * @param resourceType      image | raw | video
     * @param expiresInSeconds  số giây URL còn hiệu lực (mặc định 15 phút)
     */
    String buildAuthenticatedUrl(String publicId, String resourceType, Integer expiresInSeconds);

    /**
     * Dựng URL có thời hạn cho file {@code authenticated}, có ép định dạng đầu ra.
     * <p>
     * <b>Vì sao cần ép format:</b> file ghi âm được upload với
     * {@code resource_type=video} (Cloudinary chỉ có {@code audio} dạng
     * {@code video} + {@code audio_codec}). Cloudinary trả {@code Content-Type}
     * là {@code video/webm} hoặc {@code video/mp4} → thẻ {@code <audio>} của
     * trình duyệt KHÔNG phát được (chỉ phát được video). Ép đầu ra
     * {@code .mp3} để {@code Content-Type} là {@code audio/mpeg} → phát được
     * trên mọi trình duyệt.
     * <p>
     * Với {@code raw} / {@code image} thì bỏ qua format: {@code public_id}
     * đã chứa sẵn đuôi file (ví dụ {@code .../abc.pdf}).
     *
     * @param publicId          public_id của file
     * @param resourceType      image | raw | video
     * @param format            định dạng đầu ra (chỉ áp dụng cho video/audio)
     * @param expiresInSeconds  số giây URL còn hiệu lực (mặc định 15 phút)
     */
    String buildAuthenticatedUrl(String publicId, String resourceType, String format,
                                 Integer expiresInSeconds);
}
