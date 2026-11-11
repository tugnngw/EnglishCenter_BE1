package com.sba301.englishcenter.service.impl;

import com.cloudinary.Cloudinary;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.CloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Triển khai {@link CloudinaryService}.
 * <p>
 * <b>Instance {@link Cloudinary} được inject từ {@code CloudinaryConfig}</b> (bean
 * Spring, tạo 1 lần duy nhất). KHÔNG gọi {@code new Cloudinary(...)} trong service:
 * constructor của SDK dò HTTP adapter trên classpath và ném
 * {@code UnknownError: Can't find Cloudinary platform adapter} nếu thiếu —
 * lỗi này là {@link Error} nên {@code catch (Exception)} không bắt được.
 */
@Service
@Slf4j
public class CloudinaryServiceImpl implements CloudinaryService {

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

    /**
     * Instance Cloudinary dùng chung (bean Spring — xem {@code CloudinaryConfig}).
     * <p>
     * KHÔNG tạo {@code new Cloudinary(...)} bên trong method: constructor của SDK
     * dò HTTP adapter trên classpath và ném {@code UnknownError} nếu không có.
     * Bean được tạo 1 lần duy nhất, có fallback {@code load_strategies=false}.
     */
    private final Cloudinary cloudinary;

    public CloudinaryServiceImpl(
            Cloudinary cloudinary,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.auth-key:}") String authKey,
            @Value("${cloudinary.url-ttl-seconds:900}") int urlTtlSeconds) {
        this.cloudinary = cloudinary;
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

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String getCloudName() {
        return cloudName;
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String signUploadParams(Map<String, Object> params) {
        requireConfigured();
        Map<String, Object> toSign = new TreeMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            // Cloudinary KHÔNG ký các tham số này (resource_type nằm trên URL upload, không nằm trong chữ ký)
            if ("file".equals(key) || "cloud_name".equals(key) || "api_key".equals(key)
                    || "signature".equals(key) || "resource_type".equals(key)) {
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
        // Chuỗi ký đúng chuẩn Cloudinary: "key1=value1&key2=value2" (sắp xếp a-z) + apiSecret.
        // KHÔNG dùng TreeMap.toString() vì nó sinh "{key1=value1, key2=value2}" -> Cloudinary báo Invalid Signature
        StringBuilder stringToSign = new StringBuilder();
        for (Map.Entry<String, Object> entry : toSign.entrySet()) {
            if (stringToSign.length() > 0) {
                stringToSign.append('&');
            }
            stringToSign.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sha1(stringToSign + apiSecret);
    }

    @Override
    public String buildAuthenticatedUrl(String publicId, String resourceType, Integer expiresInSeconds) {
        return buildAuthenticatedUrl(publicId, resourceType, null, expiresInSeconds);
    }

    @Override
    public String buildAuthenticatedUrl(String publicId, String resourceType, String format,
                                        Integer expiresInSeconds) {
        requireConfigured();
        if (publicId == null || publicId.isBlank()) {
            return null;
        }

        String normalizedResourceType = normalizeResourceType(resourceType);
        // Chỉ file media (video/audio) mới cần ép format đầu ra.
        String outputFormat = RESOURCE_VIDEO.equals(normalizedResourceType)
                ? AUDIO_OUTPUT_FORMAT
                : null;

        try {
            com.cloudinary.Url url = cloudinary.url()
                    .resourceType(normalizedResourceType)
                    .type(DELIVERY_AUTHENTICATED)
                    .publicId(publicId);
            if (outputFormat != null) {
                url = url.format(outputFormat);
            }
            return url.signed(true).secure(true).generate();
        } catch (Exception ex) {
            log.warn("Không dựng được URL có thời hạn cho file {}: {}", publicId, ex.getMessage());
            throw new AppException(ErrorCode.CLOUDINARY_UPLOAD_FAILED);
        }
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

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
