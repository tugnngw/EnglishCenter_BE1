package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Giới hạn thời lượng khi tạo / sửa đề theo chứng chỉ + loại đề - FE form tạo đề đọc từ đây thay vì chép lại con số
 * (BE là nơi duy nhất giữ các ngưỡng, xem ExamServiceImpl).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ExamRulesResponse {

    /** Thời lượng ngắn nhất (phút). */
    Integer minDurationMinutes;

    /** Thời lượng dài nhất (phút): không quá thời gian thi thật của chứng chỉ; Quiz tối đa 60 phút. */
    Integer maxDurationMinutes;

    /** Thời gian thi thật của chứng chỉ (phút) - để FE giải thích mức tối đa. */
    Integer fullTestMinutes;
}
