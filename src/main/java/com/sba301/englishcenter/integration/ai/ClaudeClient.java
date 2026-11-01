package com.sba301.englishcenter.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;

/**
 * Gọi Claude (Anthropic Messages API - POST /v1/messages) cho mọi tính năng AI: chấm bài Writing / Speaking
 * và đọc file đề (PDF gửi thẳng dạng document block, hoặc văn bản).
 * Cấu hình: ANTHROPIC_API_KEY, CLAUDE_MODEL (application.properties: ai.claude.*). Không log API key.
 */
@Slf4j
@Component
public class ClaudeClient {

    static final String API_VERSION = "2023-06-01";

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public ClaudeClient(@Value("${ai.claude.base-url:https://api.anthropic.com}") String baseUrl,
                        @Value("${ai.claude.api-key:}") String apiKey,
                        @Value("${ai.claude.model:claude-haiku-5-5}") String model,
                        @Value("${ai.claude.timeout-seconds:300}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // Đọc cả bộ đề dài có thể mất vài phút
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String model() {
        return model;
    }

    /**
     * @param system    yêu cầu cho model (system prompt)
     * @param pdfBase64 PDF mã hoá base64, hoặc null
     * @param text      nội dung gửi kèm (bài làm / văn bản tài liệu), hoặc null
     * @param maxTokens số token tối đa model được trả
     * @param failure   mã lỗi trả về khi gọi AI thất bại (AI_IMPORT_FAILED / AI_EVALUATION_FAILED)
     * @return văn bản model trả về (đã bỏ ```...``` nếu có)
     */
    public String generate(String system, String pdfBase64, String text, int maxTokens, ErrorCode failure) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        }
        List<Map<String, Object>> content = new ArrayList<>();
        if (StringUtils.hasText(pdfBase64)) {
            content.add(Map.of("type", "document",
                    "source", Map.of("type", "base64", "media_type", "application/pdf", "data", pdfBase64)));
        }
        content.add(Map.of("type", "text", "text", StringUtils.hasText(text) ? text : "Read the attached document."));

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", content)));

        String raw;
        try {
            raw = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException ex) {
            log.warn("Claude request failed: {}", ex.getMessage());
            throw new AppException(failure);
        }
        return extractText(raw, failure);
    }

    String extractText(String raw, ErrorCode failure) {
        try {
            JsonNode root = json.readTree(raw);
            StringBuilder out = new StringBuilder();
            root.path("content").forEach(block -> {
                if ("text".equals(block.path("type").asText())) {
                    out.append(block.path("text").asText(""));
                }
            });
            if ("max_tokens".equals(root.path("stop_reason").asText())) {
                log.warn("Claude response was cut at max_tokens");
            }
            String text = stripFences(out.toString());
            if (!StringUtils.hasText(text)) {
                throw new AppException(failure);
            }
            return text;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Claude response invalid: {}", ex.getMessage());
            throw new AppException(failure);
        }
    }

    /** Phòng khi model vẫn bọc ```json ... ``` */
    static String stripFences(String text) {
        return text.trim().replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
    }

    /** Lấy đoạn JSON object đầu tiên (từ '{' đầu tới '}' cuối) phòng khi model nói thêm trước / sau JSON. */
    public static String jsonObjectOf(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }
}
