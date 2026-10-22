package com.sba301.englishcenter.dto.response;

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

    /** Nội dung JSONB properties đã parse thành object để frontend dùng trực tiếp. */
    Object properties;
}