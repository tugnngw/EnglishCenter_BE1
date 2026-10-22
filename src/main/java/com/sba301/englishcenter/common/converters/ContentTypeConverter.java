package com.sba301.englishcenter.common.converters;

import com.sba301.englishcenter.common.enums.ContentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi {@link ContentType} <-> giá trị của PostgreSQL enum {@code content_type_enum}.
 * Giá trị lưu trong DB là chữ thường: 'lesson', 'vocabulary', 'homework'.
 */
@Converter(autoApply = false)
public class ContentTypeConverter implements AttributeConverter<ContentType, String> {

    @Override
    public String convertToDatabaseColumn(ContentType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ContentType convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (ContentType type : ContentType.values()) {
            if (type.getValue().equalsIgnoreCase(dbData)) {
                return type;
            }
        }
        return null;
    }
}