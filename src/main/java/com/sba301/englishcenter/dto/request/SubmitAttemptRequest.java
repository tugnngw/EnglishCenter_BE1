package com.sba301.englishcenter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Body tùy chọn khi nộp bài: các câu trả lời cuối cùng chưa kịp autosave.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmitAttemptRequest {

    @Size(max = 500, message = "Tối đa 500 câu trả lời mỗi lần lưu")
    @Valid
    List<AnswerItemRequest> answers;
}
