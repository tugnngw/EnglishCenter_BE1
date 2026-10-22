package com.sba301.englishcenter.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại nội dung trong khóa học - ánh xạ với PostgreSQL enum {@code content_type_enum}.
 * Bảng {@code course_content} gộp chung lesson + vocabulary + homework.
 */
@Getter
@RequiredArgsConstructor
public enum ContentType {

    LESSON("lesson"),
    VOCABULARY("vocabulary"),
    HOMEWORK("homework");

    private final String value;
}