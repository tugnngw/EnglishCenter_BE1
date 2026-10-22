package com.sba301.englishcenter.common.converters;

import com.sba301.englishcenter.common.enums.CourseStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi {@link CourseStatus} <-> giá trị của PostgreSQL enum {@code course_status_enum}.
 * Giá trị lưu trong DB là chữ thường: 'draft', 'active', 'archived'.
 */
@Converter(autoApply = false)
public class CourseStatusConverter implements AttributeConverter<CourseStatus, String> {

    @Override
    public String convertToDatabaseColumn(CourseStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public CourseStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (CourseStatus status : CourseStatus.values()) {
            if (status.getValue().equalsIgnoreCase(dbData)) {
                return status;
            }
        }
        return null;
    }
}