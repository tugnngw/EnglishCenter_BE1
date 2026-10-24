package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;

import java.util.function.Function;

/**
 * Parse query param dạng chuỗi sang enum của module Exam, chấp nhận cả 'published' lẫn 'PUBLISHED'.
 * Giá trị sai trả 400 thay vì 500.
 */
final class ExamParams {

    private ExamParams() {
    }

    static ExamType examType(String value) {
        return parse(value, ExamType::fromValue, "examType");
    }

    static ExamStatus status(String value) {
        return parse(value, ExamStatus::fromValue, "status");
    }

    static ExamScope scope(String value) {
        return parse(value, ExamScope::fromValue, "examScope");
    }

    static DifficultyLevel difficulty(String value) {
        return parse(value, DifficultyLevel::fromValue, "difficulty");
    }

    static QuestionType questionType(String value) {
        return parse(value, QuestionType::fromValue, "questionType");
    }

    private static <T> T parse(String value, Function<String, T> parser, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Giá trị không hợp lệ cho " + name + ": " + value);
        }
    }
}
