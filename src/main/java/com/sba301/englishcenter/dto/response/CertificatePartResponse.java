package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificatePartResponse {

    UUID partId;
    UUID certificateId;
    String partName;
    String partDescription;
    Integer partOrder;

    /**
     * Nội dung JSONB properties đã parse thành object để frontend dùng trực tiếp.
     * <p>
     * KHỞI ĐỘNG Ở KIỂU {@link JsonNode} (KHÔNG phải {@code Object}): khai báo là
     * {@code Object} sẽ khiến Jackson serialize theo kiểu bean, frontend nhận
     * object meta của JsonNode thay vì dữ liệu thật.
     */
    JsonNode properties;
}