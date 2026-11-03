package com.sba301.englishcenter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev 3 - Lưu cả bộ đề một lần (sau khi xem trước file Word / AI): các phần thi kèm câu hỏi,
 * và câu không thuộc phần thi. Lưu trong một giao dịch: lỗi ở bất kỳ câu nào thì không lưu gì.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImportExamContentRequest {

    @Builder.Default
    @NotNull
    List<@Valid SectionContent> sections = new ArrayList<>();

    /** Câu không thuộc phần thi nào ({@code sectionId} bỏ qua) */
    @Builder.Default
    @NotNull
    List<@Valid CreateExamQuestionRequest> questions = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SectionContent {

        @Valid
        @NotNull
        CreateExamSectionRequest section;

        /** Câu hỏi của phần thi ({@code sectionId} của từng câu bỏ qua, gán theo phần thi này) */
        @Builder.Default
        @NotNull
        List<@Valid CreateExamQuestionRequest> questions = new ArrayList<>();
    }
}
