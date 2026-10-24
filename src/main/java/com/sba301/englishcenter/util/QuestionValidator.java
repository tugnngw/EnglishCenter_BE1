package com.sba301.englishcenter.util;

import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kiểm tra bộ đáp án theo từng loại câu hỏi (D3-05). Dùng chung khi tạo/sửa câu hỏi và khi publish đề.
 * <ul>
 *     <li>multiple_choice: ít nhất 2 lựa chọn, đúng 1 lựa chọn đúng, không trùng nội dung;</li>
 *     <li>true_false: đúng 2 lựa chọn, đúng 1 lựa chọn đúng;</li>
 *     <li>fill_blank: ít nhất 1 đáp án được chấp nhận;</li>
 *     <li>matching: ít nhất 2 cặp dạng {@code "vế trái::vế phải"}, vế trái không trùng;</li>
 *     <li>essay: không có đáp án (mentor chấm tay).</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuestionValidator {

    public static final String MATCHING_SEPARATOR = "::";

    /** Dữ liệu tối thiểu của một đáp án để kiểm tra (dùng được cho cả request lẫn entity). */
    public record OptionData(String text, boolean correct) {
    }

    public static void validate(QuestionType type, List<OptionData> options) {
        if (type == null) {
            throw new AppException(ErrorCode.INVALID_QUESTION_OPTIONS, "Loại câu hỏi không được để trống");
        }
        List<OptionData> opts = options == null ? List.of() : options;
        for (OptionData option : opts) {
            if (option.text() == null || option.text().isBlank()) {
                throw invalid("Nội dung đáp án không được để trống");
            }
        }
        long correctCount = opts.stream().filter(OptionData::correct).count();

        switch (type) {
            case multiple_choice -> {
                if (opts.size() < 2) {
                    throw invalid("Câu trắc nghiệm cần ít nhất 2 lựa chọn");
                }
                if (correctCount != 1) {
                    throw invalid("Câu trắc nghiệm phải có đúng 1 đáp án đúng");
                }
                assertNoDuplicateText(opts);
            }
            case true_false -> {
                if (opts.size() != 2) {
                    throw invalid("Câu đúng/sai phải có đúng 2 lựa chọn");
                }
                if (correctCount != 1) {
                    throw invalid("Câu đúng/sai phải có đúng 1 đáp án đúng");
                }
                assertNoDuplicateText(opts);
            }
            case fill_blank -> {
                if (opts.isEmpty()) {
                    throw invalid("Câu điền từ cần ít nhất 1 đáp án được chấp nhận");
                }
            }
            case matching -> {
                if (opts.size() < 2) {
                    throw invalid("Câu nối cần ít nhất 2 cặp");
                }
                Set<String> lefts = new HashSet<>();
                for (OptionData option : opts) {
                    String[] pair = splitPair(option.text());
                    if (pair == null) {
                        throw invalid("Cặp nối phải có dạng 'vế trái::vế phải': " + option.text());
                    }
                    if (!lefts.add(normalize(pair[0]))) {
                        throw invalid("Vế trái của câu nối bị trùng: " + pair[0]);
                    }
                }
            }
            case essay -> {
                if (!opts.isEmpty()) {
                    throw invalid("Câu tự luận (Writing/Speaking) không có đáp án lựa chọn");
                }
            }
        }
    }

    /** Tách "trái::phải" thành mảng 2 phần tử, null nếu sai định dạng. */
    public static String[] splitPair(String text) {
        if (text == null) {
            return null;
        }
        int idx = text.indexOf(MATCHING_SEPARATOR);
        if (idx <= 0) {
            return null;
        }
        String left = text.substring(0, idx).trim();
        String right = text.substring(idx + MATCHING_SEPARATOR.length()).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        return new String[]{left, right};
    }

    private static void assertNoDuplicateText(List<OptionData> opts) {
        Set<String> seen = new HashSet<>();
        for (OptionData option : opts) {
            if (!seen.add(normalize(option.text()))) {
                throw invalid("Các lựa chọn bị trùng nội dung: " + option.text());
            }
        }
    }

    private static String normalize(String text) {
        return text.trim().toLowerCase();
    }

    private static AppException invalid(String message) {
        return new AppException(ErrorCode.INVALID_QUESTION_OPTIONS, message);
    }
}
