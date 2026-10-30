package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.dto.request.AiImportRequest;
import com.sba301.englishcenter.dto.response.AiImportResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.GeminiDocumentClient;
import com.sba301.englishcenter.service.ExamAiImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAiImportServiceImpl implements ExamAiImportService {

    /** Giới hạn PDF gửi thẳng cho Gemini (inline_data) */
    static final int MAX_PDF_BYTES = 15 * 1024 * 1024;

    private static final List<String> DEFAULT_SECTIONS = List.of("LISTENING", "READING", "WRITING", "SPEAKING", "GENERAL");

    /** Mẫu nhập đề — giống hệt bộ đọc file đề ở FE (examImport.ts) */
    static final String EXAM_INSTRUCTION = """
            You convert an English test document into a strict plain-text import format.
            Output ONLY the converted text, no commentary, no markdown fences.
            The test is for the certificate: %s.

            FORMAT (one item per line):
            [SECTION] <PART> | <title>     open a section. <PART> MUST be exactly one of: %s
            [INSTRUCTIONS] <text>          instructions of the current section
            [PASSAGE] <text>               reading passage / transcript; following lines continue it
            [AUDIO] <url>                  only when the document contains an audio URL (listening sections)
            <n>. <question text>           multiple choice question; then options on the next lines:
            A. <option>
            *B. <option>                   put * before the letter of the ONE correct option
            <n>. [TF] <statement>          true/false question; next line: = True   or   = False
            <n>. [FILL] <text with ___>    gap fill; next line: = answer | other accepted answer
            <n>. [MATCH] <instruction>     matching; next lines, at least 2: - left item = matching right item
            <n>. [ESSAY] <task>            writing or speaking task (graded by a mentor)
            [EXPLAIN] <text>               explanation for the question right above
            Append {<points>} at the end of a question line only when the document gives its points.

            RULES:
            - Keep every question, option, passage and instruction from the document; translate nothing; invent nothing.
            - Number questions 1., 2., 3. ... continuously through the whole output.
            - Map each part of the document to the closest allowed <PART>; use the document's part name as <title>.
            - Put a section's passage before its questions. Never start a passage line with "1." or "A." style
              numbering: write (1) or (A) instead, so it is not read as a question or option.
            - True / False / Not Given (or Yes / No / Not Given) questions are multiple choice with options
              A. True  B. False  C. Not Given (use the document's wording).
            - Correct answers: use the answer key in the document when there is one. Otherwise decide the answer
              from the passage or grammar. For listening questions with no answer key and no transcript, leave
              the options without *.
            - Writing / speaking tasks become [ESSAY] questions, one per task, including the full task text.
            """;

    static final String PRACTICE_INSTRUCTION = """
            You extract English writing and speaking practice tasks from a document.
            Return JSON only: {"prompts":[{"title":"...","description":"...","examType":"WRITING","bankName":"..."}]}
            - One item per task. examType is WRITING or SPEAKING.
            - title: short name of the task (max 120 characters), e.g. "IELTS Writing Task 2 - Technology".
            - description: the full task text exactly as in the document, including any prompt notes or bullet points.
            - bankName: the test / book / set name from the document if there is one, otherwise empty.
            - Do not invent tasks; skip reading / listening questions. The certificate context is: %s.
            """;

    private final GeminiDocumentClient gemini;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public AiImportResponse parse(AiImportRequest request) {
        String pdf = validPdf(request.getFileBase64());
        String text = StringUtils.hasText(request.getText()) ? request.getText().trim() : null;
        if (pdf == null && text == null) {
            throw new AppException(ErrorCode.AI_IMPORT_INVALID_FILE);
        }
        String certificate = StringUtils.hasText(request.getCertificateName()) ? request.getCertificateName().trim()
                : "general English";

        if (request.getTarget() == AiImportRequest.Target.PRACTICE) {
            String raw = gemini.generate(PRACTICE_INSTRUCTION.formatted(certificate), pdf, text, true);
            return AiImportResponse.builder().prompts(practiceDrafts(raw)).build();
        }
        List<String> sections = request.getSections() == null ? List.of()
                : request.getSections().stream().filter(StringUtils::hasText).map(String::trim).toList();
        String allowed = String.join(", ", sections.isEmpty() ? DEFAULT_SECTIONS : sections);
        String content = gemini.generate(EXAM_INSTRUCTION.formatted(certificate, allowed), pdf, text, false);
        log.info("AI đọc file đề {} ({} ký tự kết quả)", request.getFileName(), content.length());
        return AiImportResponse.builder().content(content).build();
    }

    /** PDF base64 hợp lệ và không quá {@link #MAX_PDF_BYTES}; null nếu không gửi */
    static String validPdf(String base64) {
        if (!StringUtils.hasText(base64)) {
            return null;
        }
        String data = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data.trim());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.AI_IMPORT_INVALID_FILE);
        }
        // PDF luôn bắt đầu bằng "%PDF"
        boolean isPdf = bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (!isPdf || bytes.length > MAX_PDF_BYTES) {
            throw new AppException(ErrorCode.AI_IMPORT_INVALID_FILE);
        }
        return data.trim();
    }

    List<AiImportResponse.PracticeDraft> practiceDrafts(String raw) {
        try {
            JsonNode root = json.readTree(raw);
            JsonNode items = root.isArray() ? root : root.path("prompts");
            List<AiImportResponse.PracticeDraft> drafts = new ArrayList<>();
            for (JsonNode item : items) {
                String type = item.path("examType").asText("").trim().toUpperCase(Locale.ROOT);
                String description = item.path("description").asText("").trim();
                if (!StringUtils.hasText(description) || !(type.equals("WRITING") || type.equals("SPEAKING"))) {
                    continue;
                }
                String title = item.path("title").asText("").trim();
                drafts.add(AiImportResponse.PracticeDraft.builder()
                        .title(StringUtils.hasText(title) ? truncate(title, 255) : truncate(description, 120))
                        .description(truncate(description, 5000))
                        .examType(type)
                        .bankName(truncate(item.path("bankName").asText("").trim(), 255))
                        .build());
            }
            return drafts;
        } catch (Exception ex) {
            log.warn("AI practice import response invalid: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_IMPORT_FAILED);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
