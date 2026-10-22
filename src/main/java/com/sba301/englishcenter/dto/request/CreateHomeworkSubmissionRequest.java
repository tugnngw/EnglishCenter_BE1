package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateHomeworkSubmissionRequest {

    @NotNull(message = "ID bài tập không được để trống")
    UUID homeworkContentId;

    String answerContent;

    @Size(max = 500, message = "URL file không được vượt quá 500 ký tự")
    String fileUrl;
}