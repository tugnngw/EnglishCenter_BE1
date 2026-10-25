package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Học viên nộp bài viết (Writing) hoặc văn bản phiên âm bài nói (Speaking) — TASK-D5-04.
 */
@Getter
@Setter
public class SubmitPracticeAnswerRequest {

    @NotBlank(message = "Bài làm không được để trống")
    @Size(max = 10000, message = "Bài làm không được vượt quá 10000 ký tự")
    private String content;
}
