package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.request.UploadSignatureRequest;
import com.sba301.englishcenter.dto.response.UploadSignatureResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cấp chữ ký upload Cloudinary cho bài tập.
 * <p>
 * BE là người quyết định {@code resource_type} và {@code folder} — client KHÔNG
 * tự chọn được, nhờ vậy không thể đẩy file sai loại lên chỗ không phù hợp.
 * <p>
 * Quy tắc phân loại (theo quyết định đã chốt):
 * <ul>
 *     <li>ảnh (png/jpg/jpeg/webp/gif) → {@code image};</li>
 *     <li>PDF / Word / Excel / PowerPoint / txt → {@code raw};</li>
 *     <li>audio / video (mp3/wav/m4a/mp4/mov/webm) → {@code video}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    /** Giới hạn dung lượng theo từng nhóm (byte). */
    private static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;   // 20 MB
    private static final long MAX_AUDIO_VIDEO_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;       // 5 MB

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Set<String> DOCUMENT_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt");
    private static final Set<String> MEDIA_EXTENSIONS =
            Set.of("mp3", "wav", "m4a", "aac", "ogg", "mp4", "mov", "webm", "mkv");

    private final CloudinaryService cloudinary;
    private final CourseContentRepository courseContentRepository;

    /**
     * Cấp chữ ký upload.
     *
     * @param request mục đích + thông tin file
     */
    @Transactional(readOnly = true)
    public UploadSignatureResponse createSignature(UploadSignatureRequest request) {
        if (!cloudinary.isConfigured()) {
            throw new AppException(ErrorCode.CLOUDINARY_NOT_CONFIGURED);
        }

        String purpose = normalizePurpose(request.getPurpose());
        if ("multiple_choice".equalsIgnoreCase(purpose)) {
            // Bài trắc nghiệm không upload file — chặn sớm để tránh rác trên Cloudinary.
            throw new AppException(ErrorCode.HOMEWORK_SUBMISSION_TYPE_MISMATCH,
                    "Bài trắc nghiệm không cần upload file");
        }

        CourseContent homework = null;
        UUID courseId = null;
        UUID homeworkId = request.getHomeworkContentId();

        if (homeworkId != null) {
            homework = courseContentRepository.findById(homeworkId)
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
            if (homework.getContentType() != ContentType.HOMEWORK) {
                throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                        "Nội dung trỏ tới không phải bài tập (homework)");
            }
            courseId = homework.getCourse() != null ? homework.getCourse().getCourseId() : null;
        }

        String extension = resolveExtension(request.getFileFormat(), request.getFileName());
        String resourceType = resolveResourceType(extension);
        long maxBytes = resolveMaxBytes(resourceType);

        Long fileBytes = request.getFileBytes();
        if (fileBytes != null && fileBytes > maxBytes) {
            throw new AppException(ErrorCode.HOMEWORK_FILE_TOO_LARGE,
                    "Dung lượng tối đa cho loại file này là "
                            + formatMb(maxBytes) + " MB (file gửi lên: " + formatMb(fileBytes) + " MB)");
        }

        String folder = buildFolder(purpose, courseId, homeworkId);
        long timestamp = System.currentTimeMillis() / 1000L;

        // Delivery type: bài nộp học viên là PRIVATE (authenticated), tài liệu đính kèm là PUBLIC (upload).
        String deliveryType = resolveDeliveryType(purpose);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("timestamp", timestamp);
        params.put("folder", folder);
        params.put("resource_type", resourceType);
        params.put("type", deliveryType);

        String signature = cloudinary.signUploadParams(params);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("timestamp", timestamp);
        extra.put("folder", folder);
        extra.put("resource_type", resourceType);
        extra.put("type", deliveryType);

        return UploadSignatureResponse.builder()
                .apiKey(cloudinary.getApiKey())
                .signature(signature)
                .timestamp(timestamp)
                .cloudName(cloudinary.getCloudName())
                .resourceType(resourceType)
                .deliveryType(deliveryType)
                .folder(folder)
                .params(extra)
                .build();
    }

    /**
     * Delivery type của file theo mục đích upload.
     * <ul>
     *     <li>{@code submission} / {@code submission_audio} → {@code authenticated} (private,
     *         URL có thời hạn do BE dựng khi xem);</li>
     *     <li>{@code attachment} → {@code upload} (public, tài liệu đính kèm của bài tập).</li>
     * </ul>
     */
    public String resolveDeliveryType(String purpose) {
        if ("submission".equalsIgnoreCase(purpose)
                || "submission_audio".equalsIgnoreCase(purpose)) {
            return CloudinaryService.DELIVERY_AUTHENTICATED;
        }
        return CloudinaryService.DELIVERY_UPLOAD;
    }

    /**
     * Suy ra {@code resource_type} từ đuôi file.
     *
     * @throws AppException nếu định dạng không được phép
     */
    public String resolveResourceType(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new AppException(ErrorCode.HOMEWORK_FILE_TYPE_NOT_ALLOWED,
                    "Không xác định được định dạng file");
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return CloudinaryService.RESOURCE_IMAGE;
        }
        if (MEDIA_EXTENSIONS.contains(ext)) {
            return CloudinaryService.RESOURCE_VIDEO;
        }
        if (DOCUMENT_EXTENSIONS.contains(ext)) {
            return CloudinaryService.RESOURCE_RAW;
        }
        throw new AppException(ErrorCode.HOMEWORK_FILE_TYPE_NOT_ALLOWED,
                "Định dạng ." + ext + " không được phép. "
                        + "Chấp nhận: ảnh, PDF/Word/Excel/PowerPoint, audio/video.");
    }

    /** Giới hạn dung lượng (byte) theo resource_type. */
    public long resolveMaxBytes(String resourceType) {
        return switch (resourceType) {
            case CloudinaryService.RESOURCE_IMAGE -> MAX_IMAGE_BYTES;
            case CloudinaryService.RESOURCE_VIDEO -> MAX_AUDIO_VIDEO_BYTES;
            default -> MAX_DOCUMENT_BYTES;
        };
    }

    /**
     * Thư mục đích trên Cloudinary.
     * <ul>
     *     <li>tài liệu bài tập → {@code courses/{courseId}/homework};</li>
     *     <li>bài nộp học viên → {@code courses/{courseId}/homework/submissions/{homeworkId}/{studentId}}.</li>
     * </ul>
     */
    public String buildFolder(String purpose, UUID courseId, UUID homeworkId) {
        StringBuilder folder = new StringBuilder();
        if (courseId != null) {
            folder.append("courses/").append(courseId).append('/');
        } else {
            folder.append("courses/unassigned/");
        }
        folder.append("homework");
        if ("submission".equals(purpose) || "submission_audio".equals(purpose)) {
            folder.append("/submissions");
            if (homeworkId != null) {
                folder.append('/').append(homeworkId);
            }
            UUID studentId = SecurityUtils.getCurrentUserId().orElse(null);
            if (studentId != null) {
                folder.append('/').append(studentId);
            }
        }
        return folder.toString();
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private String normalizePurpose(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mục đích upload không được để trống");
        }
        String purpose = raw.trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = new LinkedHashSet<>(Set.of(
                "attachment", "submission", "submission_audio", "multiple_choice"));
        if (!allowed.contains(purpose)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Mục đích upload không hợp lệ: " + raw);
        }
        return purpose;
    }

    /** Đuôi file: ưu tiên {@code fileFormat}, fallback từ tên file. */
    private String resolveExtension(String fileFormat, String fileName) {
        if (fileFormat != null && !fileFormat.isBlank()) {
            String ext = fileFormat.trim().toLowerCase(Locale.ROOT);
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isBlank()) {
                return ext;
            }
        }
        if (fileName != null && !fileName.isBlank()) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                return fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static String formatMb(long bytes) {
        return String.valueOf(Math.round(bytes / (1024.0 * 1024.0)));
    }

    /** Đọc {@code submissionType} từ JSONB content_data — dùng chung với service bài tập. */
    public static String readSubmissionType(String contentData) {
        return JsonUtil.readString(contentData, "submissionType");
    }
}
