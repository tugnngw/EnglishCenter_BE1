package com.sba301.englishcenter.exam;

import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.util.QuestionValidator;
import com.sba301.englishcenter.util.QuestionValidator.OptionData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionValidatorTest {

    private static OptionData ok(String text) {
        return new OptionData(text, true);
    }

    private static OptionData no(String text) {
        return new OptionData(text, false);
    }

    private static void assertInvalid(QuestionType type, List<OptionData> options) {
        assertThatThrownBy(() -> QuestionValidator.validate(type, options))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_QUESTION_OPTIONS);
    }

    @Test
    void validQuestions() {
        assertThatCode(() -> QuestionValidator.validate(QuestionType.MULTIPLE_CHOICE,
                List.of(ok("A"), no("B"), no("C")))).doesNotThrowAnyException();
        assertThatCode(() -> QuestionValidator.validate(QuestionType.TRUE_FALSE,
                List.of(no("True"), ok("False")))).doesNotThrowAnyException();
        assertThatCode(() -> QuestionValidator.validate(QuestionType.FILL_BLANK,
                List.of(ok("went")))).doesNotThrowAnyException();
        assertThatCode(() -> QuestionValidator.validate(QuestionType.MATCHING,
                List.of(ok("dog::chó"), ok("cat::mèo")))).doesNotThrowAnyException();
        assertThatCode(() -> QuestionValidator.validate(QuestionType.ESSAY, List.of())).doesNotThrowAnyException();
    }

    @Test
    void multipleChoice_needsExactlyOneCorrect() {
        assertInvalid(QuestionType.MULTIPLE_CHOICE, List.of(ok("A"), ok("B")));
        assertInvalid(QuestionType.MULTIPLE_CHOICE, List.of(no("A"), no("B")));
        assertInvalid(QuestionType.MULTIPLE_CHOICE, List.of(ok("A")));
        assertInvalid(QuestionType.MULTIPLE_CHOICE, List.of(ok("A"), no("a ")));
    }

    @Test
    void trueFalse_needsTwoOptions() {
        assertInvalid(QuestionType.TRUE_FALSE, List.of(ok("True"), no("False"), no("Not given")));
    }

    @Test
    void fillBlankMatchingEssay_rules() {
        assertInvalid(QuestionType.FILL_BLANK, List.of());
        assertInvalid(QuestionType.MATCHING, List.of(ok("dog-chó"), ok("cat::mèo")));
        assertInvalid(QuestionType.MATCHING, List.of(ok("dog::chó"), ok("Dog::cún")));
        assertInvalid(QuestionType.ESSAY, List.of(ok("x")));
        assertInvalid(QuestionType.MULTIPLE_CHOICE, List.of(ok(" "), no("B")));
    }
}
