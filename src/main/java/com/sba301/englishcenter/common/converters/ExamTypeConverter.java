package com.sba301.englishcenter.common.converters;

import com.sba301.englishcenter.common.enums.ExamType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi {@link ExamType} <-> nhãn của PostgreSQL enum {@code exam_type_enum}.
 * Cần converter vì nhãn DB 'final' không thể là tên hằng số Java.
 */
@Converter(autoApply = false)
public class ExamTypeConverter implements AttributeConverter<ExamType, String> {

    @Override
    public String convertToDatabaseColumn(ExamType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ExamType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return ExamType.fromValue(dbData);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
