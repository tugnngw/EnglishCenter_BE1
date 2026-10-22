package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCertificateRequest {

    @NotBlank(message = "Tên chứng chỉ không được để trống")
    @Size(max = 255, message = "Tên chứng chỉ không được vượt quá 255 ký tự")
    String certificateName;

    String description;

    /** Danh sách các kỹ năng/phần của chứng chỉ. */
    List<PartRequest> parts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PartRequest {

        @NotBlank(message = "Tên phần không được để trống")
        @Size(max = 255, message = "Tên phần không được vượt quá 255 ký tự")
        String partName;

        String partDescription;

        /** JSON string hoặc mảng JSON để lưu vào cột properties (JSONB). */
        String properties;
    }
}