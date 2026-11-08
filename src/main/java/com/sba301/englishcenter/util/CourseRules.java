package com.sba301.englishcenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Ràng buộc nghiệp vụ khi admin dựng khóa học và nội dung khóa học.
 * <p>
 * FE đã validate ở form, nhưng API vẫn phải tự bảo vệ dữ liệu (Postman, script...).
 * <ul>
 *   <li>Điểm đầu vào / mục tiêu phải nằm trong thang điểm của chứng chỉ (hoặc của kỹ năng)
 *       và đúng bước điểm (IELTS bước 0.5, TOEIC bước 5...).</li>
 *   <li>Học phí là số nguyên VND, tối đa {@link #MAX_PRICE}.</li>
 *   <li>Trình độ chỉ nhận mã CEFR.</li>
 *   <li>{@code content_data} của bài tập: hình thức nộp, hạn nộp, điểm tối đa, điểm đạt, số lần làm.</li>
 * </ul>
 */
public final class CourseRules {

    public static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    public static final BigDecimal MAX_PRICE = new BigDecimal("100000000");
    public static final int MIN_TITLE_LENGTH = 3;
    public static final int MAX_ORDER = 9999;

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";
    /** Ghi âm / nộp file âm thanh (bài tập Speaking). */
    public static final String TYPE_AUDIO = "audio";
    /** Hình thức nộp được phép: file, trắc nghiệm, ghi âm, nhập văn bản. */
    public static final Set<String> NEW_HOMEWORK_TYPES =
            Set.of(TYPE_FILE, TYPE_MULTIPLE_CHOICE, TYPE_AUDIO, TYPE_TEXT);

    /** Định dạng file âm thanh được phép cho bài tập ghi âm. */
    public static final Set<String> AUDIO_FILE_TYPES =
            Set.of("mp3", "wav", "m4a", "aac", "ogg", "webm", "mp4", "mov");

    private static final BigDecimal MAX_HOMEWORK_SCORE = new BigDecimal("1000");
    private static final int MAX_HOMEWORK_ATTEMPTS = 100;
    private static final int MAX_INSTRUCTIONS_LENGTH = 5000;

    private CourseRules() {
    }

    /** Thang điểm (min, max, bước) đọc từ cấu hình chứng chỉ / kỹ năng. */
    public record Scale(BigDecimal min, BigDecimal max, BigDecimal step) {
    }

    // =====================================================================
    // KHÓA HỌC
    // =====================================================================

    /**
     * Thang điểm áp dụng cho khóa học: ưu tiên thang của kỹ năng (VD TOEIC Listening 5 – 495),
     * sau đó thang chung của chứng chỉ. Chứng chỉ chưa cấu hình thang → null (chỉ kiểm tra ≥ 0).
     */
    public static Scale resolveScale(Certificate certificate, CertificatePart part) {
        Scale scale = part != null ? scaleFromJson(JsonUtil.parse(part.getProperties())) : null;
        if (scale == null && certificate != null) {
            if (certificate.getMaxScore() != null && certificate.getMaxScore().signum() > 0) {
                BigDecimal min = certificate.getMinScore() != null ? certificate.getMinScore() : BigDecimal.ZERO;
                BigDecimal step = certificate.getScoreStep() != null && certificate.getScoreStep().signum() > 0
                        ? certificate.getScoreStep()
                        : new BigDecimal("0.5");
                return new Scale(min, certificate.getMaxScore(), step);
            }
            JsonNode root = JsonUtil.parse(certificate.getDescription());
            scale = root != null && root.isObject() ? scaleFromJson(root.get("scoring")) : null;
        }
        return scale;
    }

    /** Kỹ năng phải thuộc chứng chỉ của khóa học; có kỹ năng thì bắt buộc có chứng chỉ. */
    public static void validatePartBelongsTo(Certificate certificate, CertificatePart part) {
        if (part == null) {
            return;
        }
        if (certificate == null) {
            throw new AppException(ErrorCode.COURSE_CERTIFICATE_REQUIRED);
        }
        if (part.getCertificate() == null
                || !Objects.equals(part.getCertificate().getCertificateId(), certificate.getCertificateId())) {
            throw new AppException(ErrorCode.CERTIFICATE_PART_MISMATCH,
                    "Kỹ năng \"" + part.getPartName() + "\" không thuộc chứng chỉ "
                            + certificate.getCertificateName());
        }
    }

    /** Điểm đầu vào / mục tiêu: cần chứng chỉ, nằm trong thang, đúng bước, đầu vào < mục tiêu. */
    public static void validateScores(Certificate certificate, CertificatePart part,
                                      BigDecimal entryScore, BigDecimal targetScore) {
        if (entryScore == null && targetScore == null) {
            return;
        }
        if (certificate == null) {
            throw new AppException(ErrorCode.COURSE_CERTIFICATE_REQUIRED);
        }
        Scale scale = resolveScale(certificate, part);
        validateScore("Điểm đầu vào", entryScore, scale);
        validateScore("Điểm mục tiêu", targetScore, scale);
        if (entryScore != null && targetScore != null && entryScore.compareTo(targetScore) >= 0) {
            throw new AppException(ErrorCode.COURSE_SCORE_RANGE_INVALID);
        }
    }

    private static void validateScore(String label, BigDecimal value, Scale scale) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0) {
            throw new AppException(ErrorCode.COURSE_SCORE_OUT_OF_SCALE, label + " phải lớn hơn hoặc bằng 0");
        }
        if (scale == null) {
            return;
        }
        if (value.compareTo(scale.min()) < 0 || value.compareTo(scale.max()) > 0) {
            throw new AppException(ErrorCode.COURSE_SCORE_OUT_OF_SCALE,
                    label + " phải nằm trong khoảng " + plain(scale.min()) + " – " + plain(scale.max()));
        }
        BigDecimal offset = value.subtract(scale.min());
        if (offset.remainder(scale.step()).signum() != 0) {
            throw new AppException(ErrorCode.COURSE_SCORE_OUT_OF_SCALE,
                    label + " phải theo bước " + plain(scale.step()) + " (VD " + plain(scale.min()) + ", "
                            + plain(scale.min().add(scale.step())) + ", ...)");
        }
    }

    /** Học phí: số nguyên VND, 0 – 100.000.000. */
    public static void validatePrice(BigDecimal price) {
        if (price == null) {
            return;
        }
        if (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Học phí phải từ 0 đến 100.000.000 VND");
        }
        if (price.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Học phí phải là số nguyên (VND)");
        }
    }

    /** Trình độ: rỗng (không phân cấp) hoặc mã CEFR A1 – C2. */
    public static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }
        String clean = level.trim().toUpperCase();
        if (!CEFR_LEVELS.contains(clean)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Trình độ chỉ nhận A1, A2, B1, B2, C1, C2");
        }
        return clean;
    }

    // =====================================================================
    // NỘI DUNG KHÓA HỌC
    // =====================================================================

    /** Tiêu đề bắt buộc, tối thiểu {@value #MIN_TITLE_LENGTH} ký tự. */
    public static String requireTitle(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.length() < MIN_TITLE_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Tiêu đề phải có ít nhất " + MIN_TITLE_LENGTH + " ký tự");
        }
        return clean;
    }

    /** Thứ tự hiển thị: 0 – {@value #MAX_ORDER}. */
    public static void validateOrder(Integer order) {
        if (order != null && (order < 0 || order > MAX_ORDER)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thứ tự phải từ 0 đến " + MAX_ORDER);
        }
    }

    /** Hình thức nộp đang lưu trong {@code content_data} (null nếu chưa có). */
    public static String readSubmissionType(String contentData) {
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull("submissionType")) {
            return null;
        }
        return node.get("submissionType").asText().trim().toLowerCase();
    }

    /**
     * Kiểm tra + chuẩn hoá {@code content_data} của bài tập.
     *
     * @param contentData     JSON mới (đã normalize)
     * @param previousData    JSON đang lưu (null khi tạo mới)
     * @return JSON đã chuẩn hoá (bỏ các key không dùng tới)
     */
    public static String validateHomeworkData(String contentData, String previousData) {
        JsonNode parsed = JsonUtil.parse(contentData);
        ObjectNode data = parsed != null && parsed.isObject()
                ? (ObjectNode) parsed
                : JsonUtil.mapper().createObjectNode();
        JsonNode previous = JsonUtil.parse(previousData);

        // ---- Hình thức nộp
        String type = data.hasNonNull("submissionType")
                ? data.get("submissionType").asText().trim().toLowerCase()
                : TYPE_FILE;
        if (!NEW_HOMEWORK_TYPES.contains(type)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Hình thức nộp chỉ nhận: file (PDF), audio (ghi âm), text (nhập văn bản) "
                            + "hoặc multiple_choice (trắc nghiệm)");
        }
        data.put("submissionType", type);

        // ---- Bài ghi âm: định dạng file âm thanh + giới hạn riêng
        if (TYPE_AUDIO.equals(type)) {
            // Bài ghi âm không dùng điểm tối đa/rubric kiểu file thường → vẫn cho phép.
            // Định dạng: nếu Admin không cấu hình → dùng danh sách audio mặc định.
            if (!data.has("allowedFileTypes")) {
                ArrayNode defaults = JsonUtil.mapper().createArrayNode();
                AUDIO_FILE_TYPES.stream().sorted().forEach(defaults::add);
                data.set("allowedFileTypes", defaults);
            }
        }

        // ---- Hướng dẫn
        if (data.hasNonNull("instructions") && data.get("instructions").asText().length() > MAX_INSTRUCTIONS_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Hướng dẫn tối đa " + MAX_INSTRUCTIONS_LENGTH + " ký tự");
        }

        // ---- Hạn nộp: yyyy-MM-dd, không được ở quá khứ (trừ khi giữ nguyên hạn cũ)
        String dueDate = text(data, "dueDate");
        if (dueDate != null) {
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(dueDate);
            } catch (DateTimeParseException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Hạn nộp phải có dạng yyyy-MM-dd");
            }
            boolean unchanged = previous != null && dueDate.equals(text(previous, "dueDate"));
            if (!unchanged && parsedDate.isBefore(LocalDate.now())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Hạn nộp không được ở trong quá khứ");
            }
        } else {
            data.remove("dueDate");
        }

        // ---- Điểm tối đa: chỉ dùng cho bài mentor chấm tay; trắc nghiệm lấy thang của đề
        if (TYPE_MULTIPLE_CHOICE.equals(type)) {
            data.remove("maxScore");
        } else {
            BigDecimal maxScore = number(data, "maxScore");
            if (maxScore != null && (maxScore.signum() <= 0 || maxScore.compareTo(MAX_HOMEWORK_SCORE) > 0)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Điểm tối đa phải lớn hơn 0 và không quá 1000");
            }
        }

        // ---- Điểm đạt (%)
        BigDecimal passing = number(data, "passingScore");
        if (passing != null && (passing.signum() < 0 || passing.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Điểm đạt phải từ 0 đến 100 (%)");
        }

        // ---- Số lần làm tối đa (0 / trống = không giới hạn)
        BigDecimal attempts = number(data, "maxAttempts");
        if (attempts != null && (attempts.stripTrailingZeros().scale() > 0
                || attempts.signum() < 0 || attempts.intValue() > MAX_HOMEWORK_ATTEMPTS)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Số lần làm tối đa phải là số nguyên từ 0 đến " + MAX_HOMEWORK_ATTEMPTS);
        }

        // ---- Định dạng file được phép nộp (Admin cấu hình) ----
        // Chuẩn hoá về mảng đuôi file (không dấu chấm, chữ thường, không trùng lặp).
        if (data.has("allowedFileTypes")) {
            JsonNode allowed = data.get("allowedFileTypes");
            List<String> extensions = new ArrayList<>();
            if (allowed.isArray()) {
                for (JsonNode item : allowed) {
                    if (item.isTextual()) {
                        addFileExtension(extensions, item.textValue());
                    }
                }
            } else if (allowed.isTextual()) {
                for (String part : allowed.textValue().split(",")) {
                    addFileExtension(extensions, part);
                }
            }
            if (extensions.isEmpty()) {
                data.remove("allowedFileTypes");
            } else {
                ArrayNode normalized = JsonUtil.mapper().createArrayNode();
                extensions.forEach(normalized::add);
                data.set("allowedFileTypes", normalized);
            }
        }

        // `timeLimitMinutes` không được nơi nào dùng (thời gian làm bài lấy theo đề thi) → bỏ.
        data.remove("timeLimitMinutes");
        return data.toString();
    }

    /** Chuẩn hoá 1 đuôi file: bỏ dấu chấm đầu, hạ chữ thường, bỏ rỗng, không trùng lặp. */
    private static void addFileExtension(List<String> target, String raw) {
        if (raw == null) {
            return;
        }
        String clean = raw.trim().toLowerCase();
        while (clean.startsWith(".")) {
            clean = clean.substring(1);
        }
        // Chỉ nhận chữ + số (pdf, docx, xlsx...).
        if (clean.isEmpty() || !clean.matches("[a-z0-9]+")) {
            return;
        }
        if (!target.contains(clean)) {
            target.add(clean);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Scale scaleFromJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        BigDecimal max = number(node, "maxScore");
        if (max == null || max.signum() <= 0) {
            return null;
        }
        BigDecimal min = number(node, "minScore");
        BigDecimal step = number(node, "step");
        min = min != null ? min : BigDecimal.ZERO;
        step = step != null && step.signum() > 0 ? step : BigDecimal.ONE;
        return min.compareTo(max) < 0 ? new Scale(min, max, step) : null;
    }

    private static BigDecimal number(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            try {
                return new BigDecimal(value.asText().trim());
            } catch (NumberFormatException ex) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Giá trị \"" + field + "\" phải là số");
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
