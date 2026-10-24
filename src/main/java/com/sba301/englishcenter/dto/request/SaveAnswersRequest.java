package com.sba301.englishcenter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
 * Lưu tạm (autosave) một hoặc nhiều câu trả lời trong lúc làm bài.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveAnswersRequest {

    @NotEmpty(message = "Danh sách câu trả lời không được để trống")
    @Size(max = 500, message = "Tối đa 500 câu trả lời mỗi lần lưu")
    @Valid
    List<AnswerItemRequest> answers;
}
