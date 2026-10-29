package com.sba301.englishcenter.service;

import com.cloudinary.AuthToken;
import com.cloudinary.Cloudinary;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tích hợp Cloudinary cho bài tập (tài liệu đính kèm + bài nộp).
 * <p>
 * Chỉ dùng {@code cloudinary-core} (KHÔNG kéo dependency ngoài) nên không xung đột
 * với HTTP client của Spring Boot:
 * <ul>
 *     <li>{@link #signUploadParams} — sinh chữ ký SHA-1 để FE upload thẳng lên Cloudinary;</li>
 *     <li>{@link #buildAuthenticatedUrl} — dựng URL có thời hạn cho file {@code authenticated};</li>
 *     <li>{@link #buildPublicUrl} — dựng URL công khai cho tài liệu bài tập ({@code upload}).</li>
 * </ul>
 * API secret chỉ nằm ở BE (biến môi trường {@code CLOUDINARY_API_SECRET}).
 */
@Service
@Slf4j
public class CloudinaryService {

    /** Loại tài nguyên Cloudinary. */
    public static final String RESOURCE_IMAGE = "image";
    public static final String RESOURCE_RAW = "raw";
    public static final String RESOURCE_VIDEO = "video";

    /**
     * Delivery type của file bài nộp học viên: {@code authenticated} (private).
     * <p>
     * Bài nộp KHÔNG được public → phải ký {@code type=authenticated} lúc upload và
     * dựng URL có thời hạn khi xem.
     */
    public static final String DELIVERY_AUTHENTICATED = "authenticated";

    /** Delivery type của tài liệu đính kèm của bài tập: {@code upload} (public). */
    public static final String DELIVERY_UPLOAD = "upload";

    /** Thời hạn URL xem file bài nộp (giây). */
    private static final int DEFAULT_URL_TTL_SECONDS = 900;

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;

    /** Khoá dùng để ký URL có thời hạn (nên đặt khác apiSecret). */
    private final String authKey;

    /** Thời hạn URL xem file (giây) — cấu hình được. */
    private final int urlTtlSeconds;

    private final boolean configured;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.auth-key:}") String authKey,
            @Value("${cloudinary.url-ttl-seconds:900}") int urlTtlSeconds) {
        this.cloudName = cloudName == null ? "" : cloudName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.authKey = authKey == null || authKey.isBlank() ? this.apiSecret : authKey.trim();
        this.urlTtlSeconds = urlTtlSeconds > 0 ? urlTtlSeconds : DEFAULT_URL_TTL_SECONDS;
        this.configured = !this.cloudName.isBlank() && !this.apiKey.isBlank() && !this.apiSecret.isBlank();
        if (!configured) {
            log.warn("Cloudinary chưa được cấu hình (CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET). "
                    + "Tính năng upload file của bài tập sẽ báo lỗi cho tới khi cấu hình.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getApiKey() {
        return apiKey;
    }

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
    public String signUploadParams(Map<String, Object> params) {
        requireConfigured();
        Map<String, Object> toSign = new TreeMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            if ("file".equals(key) || "cloud_name".equals(key) || "api_key".equals(key)
                    || "signature".equals(key)) {
                continue;
            }
            if (value instanceof Iterable<?> iterable) {
                StringBuilder joined = new StringBuilder();
                for (Object item : iterable) {
                    if (joined.length() > 0) joined.append(",");
                    joined.append(item);
                }
                toSign.put(key, joined.toString());
            } else {
                toSign.put(key, String.valueOf(value));
            }
        }
        return sha1(toSign.toString() + apiSecret);
    }

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
    public String buildAuthenticatedUrl(String publicId, String resourceType, Integer expiresInSeconds) {
        requireConfigured();
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        int ttl = expiresInSeconds == null || expiresInSeconds <= 0
                ? urlTtlSeconds
                : expiresInSeconds;
        long expiration = (System.currentTimeMillis() / 1000L) + ttl;

        try {
            Cloudinary cloudinary = new Cloudinary(configMap());
            AuthToken token = new AuthToken(authKey).expiration(expiration);
            return cloudinary.url()
                    .resourceType(normalizeResourceType(resourceType))
                    .type(DELIVERY_AUTHENTICATED)
                    .publicId(publicId)
                    .authToken(token)
                    .secure(true)
                    .generate();
        } catch (Exception ex) {
            log.warn("Không dựng được URL có thời hạn cho file {}: {}", publicId, ex.getMessage());
            throw new AppException(ErrorCode.CLOUDINARY_UPLOAD_FAILED);
        }
    }

    /**
     * Dựng URL công khai cho tài liệu đính kèm của bài tập (loại {@code upload}).
     */
    public String buildPublicUrl(String publicId, String resourceType, String format) {
        requireConfigured();
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        String type = normalizeResourceType(resourceType);
        StringBuilder url = new StringBuilder("https://res.cloudinary.com/")
                .append(cloudName).append('/').append(type).append('/')
                .append(DELIVERY_UPLOAD).append('/');
        if (format != null && !format.isBlank() && !"auto".equalsIgnoreCase(format)) {
            url.append('.').append(format);
        }
        url.append('/').append(publicId);
        return url.toString();
    }

    /**
     * Xoá file trên Cloudinary (dùng khi xoá bài tập / bài nộp).
     * <p>
     * Không ném lỗi ra ngoài: việc dọn file là best-effort, lỗi chỉ được log.
     *
     * @param deliveryType {@code upload} (tài liệu bài tập) hoặc {@code authenticated} (bài nộp)
     * @return true nếu xoá thành công (hoặc file không tồn tại)
     */
    public boolean destroy(String publicId, String resourceType, String deliveryType) {
        if (!configured || publicId == null || publicId.isBlank()) {
            return false;
        }
        try {
            Cloudinary cloudinary = new Cloudinary(configMap());
            cloudinary.uploader().destroy(publicId, destroyParams(resourceType, deliveryType));
            return true;
        } catch (Exception ex) {
            log.warn("Không xoá được file {} trên Cloudinary: {}", publicId, ex.getMessage());
            return false;
        }
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private Map<String, Object> configMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", true);
        return config;
    }

    private Map<String, Object> destroyParams(String resourceType, String deliveryType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", normalizeDeliveryType(deliveryType));
        params.put("invalidate", true);
        return params;
    }

    /** Chuẩn hoá delivery type: {@code authenticated} | {@code upload} (mặc định). */
    private String normalizeDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.isBlank()) {
            return DELIVERY_UPLOAD;
        }
        return DELIVERY_AUTHENTICATED.equalsIgnoreCase(deliveryType.trim())
                ? DELIVERY_AUTHENTICATED
                : DELIVERY_UPLOAD;
    }

    private String normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return RESOURCE_RAW;
        }
        return switch (resourceType.trim().toLowerCase()) {
            case RESOURCE_IMAGE -> RESOURCE_IMAGE;
            case RESOURCE_VIDEO -> RESOURCE_VIDEO;
            default -> RESOURCE_RAW;
        };
    }

    private void requireConfigured() {
        if (!configured) {
            throw new AppException(ErrorCode.CLOUDINARY_NOT_CONFIGURED);
        }
    }

    private static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Không hỗ trợ SHA-1", ex);
        }
    }
}
