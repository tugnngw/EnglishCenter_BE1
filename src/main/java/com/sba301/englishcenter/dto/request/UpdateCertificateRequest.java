package com.sba301.englishcenter.dto.request;

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
public class UpdateCertificateRequest {

    @Size(max = 255, message = "Tên chứng chỉ không được vượt quá 255 ký tự")
    String certificateName;

    String description;

    /** Danh sách các kỹ năng/phần cập nhật cho chứng chỉ. */
    List<CreateCertificateRequest.PartRequest> parts;
}