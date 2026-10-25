package com.sba301.englishcenter.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gọi Google Gemini (generateContent) để chấm bài theo 4 tiêu chí khảo thí (TASK-D5-04).
 * Cấu hình qua biến môi trường: GEMINI_API_KEY, GEMINI_MODEL (application.properties: ai.gemini.*).
 * Không log API key; phản hồi gốc được trả về trong rawResponse để lưu đối soát.
 */
@Slf4j
@Component
public class GeminiEvaluationClient implements AiEvaluationClient {

    private static final BigDecimal MAX_BAND = new BigDecimal("9.0");
    private static final BigDecimal TWO = new BigDecimal("2");

    static final String SYSTEM_PROMPT = """
            You are a certified IELTS examiner. Grade the candidate's response strictly against the official IELTS band descriptors.
            Score exactly 4 criteria, each with a band from 0 to 9 in steps of 0.5:
            - task_achievement: Task Achievement / Task Response (Writing) or Fluency & task fulfilment (Speaking)
            - coherence_cohesion: Coherence and Cohesion
            - lexical_resource: Lexical Resource
            - grammatical_accuracy: Grammatical Range and Accuracy
            band_score is the overall band: the average of the 4 criteria rounded to the nearest 0.5.
            Write every comment, the feedback and the suggestions in Vietnamese, concrete and actionable, quoting the candidate's text where useful.
            Reply with ONLY one JSON object, no markdown, in this exact shape:
            {"band_score": 6.5,
             "task_achievement": {"band": 6.5, "comment": "..."},
             "coherence_cohesion": {"band": 6.0, "comment": "..."},
             "lexical_resource": {"band": 7.0, "comment": "..."},
             "grammatical_accuracy": {"band": 6.5, "comment": "..."},
             "feedback": "...",
             "suggestions": ["...", "..."]}
            If the response is empty, off-topic or not English, give low bands and explain why.
            """;

    private final ObjectMapper json = new ObjectMapper();
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiEvaluationClient(@Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                                  @Value("${ai.gemini.api-key:}") String apiKey,
                                  @Value("${ai.gemini.model:gemini-2.5-flash}") String model,
                                  @Value("${ai.gemini.timeout-seconds:60}") long timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AiEvaluationResult evaluate(PracticeExamType type, String question, String answer) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        }
        String userPrompt = "Skill: IELTS " + type.name() + "\n\nTask / question:\n" + question
                + "\n\nCandidate response" + (type == PracticeExamType.SPEAKING ? " (transcript of the spoken answer)" : "")
                + ":\n" + answer;
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                // Giữ temperature mặc định (Google khuyến nghị cho dòng Gemini 3); chỉ yêu cầu trả JSON
                "generationConfig", Map.of("responseMimeType", "application/json"));

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
            log.warn("Gemini request failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
        return parse(raw);
    }

    AiEvaluationResult parse(String raw) {
        try {
            String text = json.readTree(raw).path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("");
            // Phòng khi model vẫn bọc ```json ... ```
            text = text.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            JsonNode result = json.readTree(text);

            CriterionScoreData ta = criterion(result.path("task_achievement"));
            CriterionScoreData cc = criterion(result.path("coherence_cohesion"));
            CriterionScoreData lr = criterion(result.path("lexical_resource"));
            CriterionScoreData ga = criterion(result.path("grammatical_accuracy"));
            BigDecimal overall = result.path("band_score").isNumber()
                    ? normalize(result.path("band_score").decimalValue())
                    : roundHalf(ta.band().add(cc.band()).add(lr.band()).add(ga.band())
                            .divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));

            List<String> suggestions = new ArrayList<>();
            result.path("suggestions").forEach(s -> {
                if (StringUtils.hasText(s.asText())) {
                    suggestions.add(s.asText().trim());
                }
            });
            return new AiEvaluationResult(overall, ta, cc, lr, ga, result.path("feedback").asText("").trim(),
                    suggestions, model, raw);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not parse Gemini response: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
    }

    private CriterionScoreData criterion(JsonNode node) {
        if (!node.path("band").isNumber()) {
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
        return new CriterionScoreData(normalize(node.path("band").decimalValue()), node.path("comment").asText("").trim());
    }

    // Kẹp trong [0, 9] và làm tròn tới 0.5 gần nhất
    private BigDecimal normalize(BigDecimal band) {
        BigDecimal clamped = band.max(BigDecimal.ZERO).min(MAX_BAND);
        return roundHalf(clamped);
    }

    private BigDecimal roundHalf(BigDecimal value) {
        return value.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO, 1, RoundingMode.UNNECESSARY);
    }
}
