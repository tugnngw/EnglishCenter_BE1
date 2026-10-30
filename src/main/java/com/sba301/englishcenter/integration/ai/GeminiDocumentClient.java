package com.sba301.englishcenter.integration.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev 3 - Gọi Gemini đọc tài liệu đề thi (PDF gửi thẳng dạng inline_data, hoặc văn bản) và trả về văn bản model sinh ra.
 * Dùng chung cấu hình {@code ai.gemini.*} với phần AI chấm bài.
 */
@Slf4j
@Component
public class GeminiDocumentClient {

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final String thinkingLevel;

    public GeminiDocumentClient(@Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                                @Value("${ai.gemini.api-key:}") String apiKey,
                                @Value("${ai.gemini.model:gemini-2.5-flash}") String model,
                                @Value("${ai.gemini.import-timeout-seconds:300}") long timeoutSeconds,
                                @Value("${ai.gemini.import-thinking-level:low}") String thinkingLevel) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // Đọc cả bộ đề dài lâu hơn chấm một bài
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
        this.thinkingLevel = thinkingLevel;
    }

    /** Đọc tài liệu là việc trích xuất, không cần suy luận sâu: Gemini 3 cho chỉnh mức "thinking" để trả nhanh hơn */
    Map<String, Object> generationConfig(boolean jsonOutput) {
        Map<String, Object> config = new HashMap<>();
        config.put("responseMimeType", jsonOutput ? "application/json" : "text/plain");
        if (model.startsWith("gemini-3") && StringUtils.hasText(thinkingLevel)) {
            config.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
        }
        return config;
    }

    /**
     * @param instruction yêu cầu cho model (systemInstruction)
     * @param pdfBase64   PDF mã hoá base64, hoặc null
     * @param text        văn bản tài liệu, hoặc null
     * @param jsonOutput  true = yêu cầu model trả JSON
     */
    public String generate(String instruction, String pdfBase64, String text, boolean jsonOutput) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        if (StringUtils.hasText(pdfBase64)) {
            parts.add(Map.of("inline_data", Map.of("mime_type", "application/pdf", "data", pdfBase64)));
        }
        if (StringUtils.hasText(text)) {
            parts.add(Map.of("text", "Document content:\n" + text));
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruction))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", generationConfig(jsonOutput));

        String raw;
        try {
            raw = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException ex) {
            log.warn("Gemini document request failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_IMPORT_FAILED);
        }
        return extractText(raw);
    }

    String extractText(String raw) {
        try {
            StringBuilder out = new StringBuilder();
            json.readTree(raw).path("candidates").path(0).path("content").path("parts")
                    .forEach(p -> out.append(p.path("text").asText("")));
            // Phòng khi model vẫn bọc ```...```
            String text = out.toString().trim().replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
            if (!StringUtils.hasText(text)) {
                throw new AppException(ErrorCode.AI_IMPORT_FAILED);
            }
            return text;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Gemini document response invalid: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_IMPORT_FAILED);
        }
    }
}
