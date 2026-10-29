package com.sba301.englishcenter.exam;

import com.sba301.englishcenter.dto.request.AiImportRequest;
import com.sba301.englishcenter.dto.response.AiImportResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.GeminiDocumentClient;
import com.sba301.englishcenter.service.impl.ExamAiImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI đọc file đề: kiểm tra file, đưa đúng phần thi của chứng chỉ vào yêu cầu, lọc kết quả đề luyện tập.
 */
class ExamAiImportServiceTest {

    private final GeminiDocumentClient gemini = mock(GeminiDocumentClient.class);
    private final ExamAiImportServiceImpl service = new ExamAiImportServiceImpl(gemini);

    @Test
    void examSendsPdfAndCertificatePartsToAi() {
        String pdf = Base64.getEncoder().encodeToString("%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
        when(gemini.generate(any(), eq(pdf), isNull(), eq(false))).thenReturn("[SECTION] Reading | Part 1\n1. Q?\n*A. a\nB. b");

        AiImportResponse res = service.parse(AiImportRequest.builder()
                .target(AiImportRequest.Target.EXAM).certificateName("IELTS")
                .sections(List.of("Listening", "Reading")).fileBase64(pdf).build());

        assertThat(res.getContent()).startsWith("[SECTION] Reading");
        ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(instruction.capture(), eq(pdf), isNull(), eq(false));
        assertThat(instruction.getValue()).contains("certificate: IELTS").contains("exactly one of: Listening, Reading");
    }

    @Test
    void rejectsMissingOrNonPdfFile() {
        assertThatThrownBy(() -> service.parse(AiImportRequest.builder().target(AiImportRequest.Target.EXAM).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.AI_IMPORT_INVALID_FILE);

        String notPdf = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.parse(AiImportRequest.builder()
                .target(AiImportRequest.Target.EXAM).fileBase64(notPdf).build()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void practiceKeepsOnlyWritingAndSpeakingTasks() {
        when(gemini.generate(any(), isNull(), eq("Task text"), anyBoolean())).thenReturn("""
                {"prompts":[
                  {"title":"Task 2","description":"Some people think...","examType":"writing","bankName":"Cam 18"},
                  {"title":"Part 2","description":"Describe a place","examType":"SPEAKING"},
                  {"title":"Reading","description":"Choose the answer","examType":"READING"},
                  {"title":"Empty","description":"","examType":"WRITING"}
                ]}""");

        AiImportResponse res = service.parse(AiImportRequest.builder()
                .target(AiImportRequest.Target.PRACTICE).text("Task text").build());

        assertThat(res.getPrompts()).extracting(AiImportResponse.PracticeDraft::getExamType)
                .containsExactly("WRITING", "SPEAKING");
        assertThat(res.getPrompts().get(0).getBankName()).isEqualTo("Cam 18");
    }
}
