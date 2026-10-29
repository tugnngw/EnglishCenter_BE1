package com.sba301.englishcenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.dto.request.CreateCertificateRequest;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kiểm tra cấu hình thang điểm của chứng chỉ trước khi lưu.
 * <p>
 * Frontend đã validate trước ở form, nhưng API phải bảo vệ dữ liệu ở phía server
 * vì có thể gọi trực tiếp (Postman, script, app khác).
 *
 * <p>Các quy tắc áp dụng (theo chuẩn khảo thí đã xác minh):
 * <ul>
 *   <li>{@code AVERAGE}: mọi kỹ năng dùng CHUNG thang của chứng chỉ
 *       (min/max phải khớp {@code scoring.minScore}/{@code scoring.maxScore}).</li>
 *   <li>{@code SUM}: tổng các kỹ năng (trừ kỹ năng có {@code excludeFromTotal})
 *       phải khớp {@code scoring.maxScore}.</li>
 *   <li>{@code INDEPENDENT}: mỗi kỹ năng là một thang riêng, không ràng buộc tổng.</li>
 * </ul>
 */
public final class CertificateScoringValidator {

    private static final double EPSILON = 0.001;

    private CertificateScoringValidator() {
    }

    /**
     * Validate toàn bộ cấu hình thang điểm của một chứng chỉ.
     *
     * @param description Mô tả chứng chỉ (chứa JSON {@code {"scoring": {...}}})
     * @param parts       Danh sách kỹ năng
     * @throws AppException nếu cấu hình không hợp lệ
     */
    public static void validate(String description,
                                List<CreateCertificateRequest.PartRequest> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }

        ScoringConfig config = ScoringConfig.from(description);
        if (config == null) {
            // Chứng chỉ chưa cấu hình thang điểm → chỉ kiểm tra dữ liệu cơ bản của từng kỹ năng
            for (CreateCertificateRequest.PartRequest part : parts) {
                PartConfig pc = PartConfig.from(part.getProperties());
                validateBasic(pc, part.getPartName());
            }
            return;
        }

        double total = 0;
        boolean anyIncluded = false;

        for (CreateCertificateRequest.PartRequest part : parts) {
            PartConfig pc = PartConfig.from(part.getProperties());
            validateBasic(pc, part.getPartName());

            switch (config.scaleType) {
                case "AVERAGE" -> {
                    if (Math.abs(pc.maxScore - config.maxScore) > EPSILON) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Thang trung bình: điểm tối đa kỹ năng \"" + part.getPartName()
                                        + "\" phải bằng " + trim(config.maxScore) + " (đang là " + trim(pc.maxScore) + ")");
                    }
                    if (Math.abs(pc.minScore - config.minScore) > EPSILON) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Thang trung bình: điểm tối thiểu kỹ năng \"" + part.getPartName()
                                        + "\" phải bằng " + trim(config.minScore) + " (đang là " + trim(pc.minScore) + ")");
                    }
                }
                case "SUM" -> {
                    if (pc.maxScore > config.maxScore) {
                        throw new AppException(ErrorCode.VALIDATION_ERROR,
                                "Điểm tối đa kỹ năng \"" + part.getPartName()
                                        + "\" không được lớn hơn điểm tối đa chứng chỉ (" + trim(config.maxScore) + ")");
                    }
                    if (!pc.excludeFromTotal) {
                        total += pc.maxScore;
                        anyIncluded = true;
                    }
                }
                default -> {
                    // INDEPENDENT: không ràng buộc tổng
                }
            }
        }

        if ("SUM".equals(config.scaleType) && anyIncluded
                && Math.abs(total - config.maxScore) > EPSILON) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Tổng điểm các kỹ năng là " + trim(total) + " nhưng thang chứng chỉ là "
                            + trim(config.maxScore) + " (thiếu/vượt " + trim(Math.abs(config.maxScore - total)) + ")");
        }
    }

    private static void validateBasic(PartConfig pc, String partName) {
        if (pc.maxScore <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Điểm tối đa kỹ năng \"" + partName + "\" phải là số dương lớn hơn 0");
        }
        if (pc.minScore > pc.maxScore) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Điểm tối thiểu kỹ năng \"" + partName + "\" không được lớn hơn điểm tối đa");
        }
        if (pc.step != null && pc.step <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Bước điểm của kỹ năng \"" + partName + "\" phải lớn hơn 0");
        }
        if (pc.weight != null && (pc.weight < 0 || pc.weight > 100)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Trọng số của kỹ năng \"" + partName + "\" phải nằm trong khoảng 0 – 100 (%)");
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** Cấu hình thang điểm của chứng chỉ, đọc từ JSON trong {@code description}. */
    private record ScoringConfig(String scaleType, double minScore, double maxScore, double step) {

        static ScoringConfig from(String description) {
            if (description == null || description.isBlank()) {
                return null;
            }
            String trimmed = description.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return null;
            }
            JsonNode root = JsonUtil.parse(trimmed);
            if (root == null || !root.has("scoring")) {
                return null;
            }
            JsonNode scoring = root.get("scoring");
            if (scoring == null || !scoring.hasNonNull("maxScore")) {
                return null;
            }
            double max = scoring.get("maxScore").asDouble();
            if (max <= 0) {
                return null;
            }
            String scaleType = scoring.hasNonNull("scaleType")
                    ? scoring.get("scaleType").asText()
                    : "AVERAGE";
            double min = scoring.hasNonNull("minScore") ? scoring.get("minScore").asDouble() : 0;
            double step = scoring.hasNonNull("step") ? scoring.get("step").asDouble() : 1;
            return new ScoringConfig(scaleType, min, max, step);
        }
    }

    /** Cấu hình thang điểm của một kỹ năng, đọc từ JSON trong {@code properties}. */
    private record PartConfig(double minScore, double maxScore, Double step, Double weight,
                              boolean excludeFromTotal) {

        static PartConfig from(String properties) {
            JsonNode node = JsonUtil.parse(properties);
            if (node == null || !node.isObject()) {
                // Không có properties → dùng mặc định rộng để không chặn dữ liệu cũ
                return new PartConfig(0, 0, null, null, false);
            }
            double max = readNumber(node, "maxScore", 0);
            double min = readNumber(node, "minScore", 0);
            Double step = node.hasNonNull("step") ? node.get("step").asDouble() : null;
            Double weight = node.hasNonNull("weight") ? node.get("weight").asDouble() : null;
            boolean exclude = node.hasNonNull("excludeFromTotal") && node.get("excludeFromTotal").asBoolean();
            return new PartConfig(min, max, step, weight, exclude);
        }

        private static double readNumber(JsonNode node, String field, double fallback) {
            if (!node.hasNonNull(field)) {
                return fallback;
            }
            JsonNode value = node.get(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                try {
                    return new BigDecimal(value.asText()).doubleValue();
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }
    }
}
