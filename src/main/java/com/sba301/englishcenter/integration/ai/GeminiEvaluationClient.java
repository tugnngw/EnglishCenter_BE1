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
import java.util.Set;

/**
 * Gọi Google Gemini (generateContent) để chấm bài theo 4 tiêu chí, theo thang của chứng chỉ mục tiêu (TASK-D5-04).
 * Cấu hình qua biến môi trường: GEMINI_API_KEY, GEMINI_MODEL (application.properties: ai.gemini.*).
 * Không log API key; phản hồi gốc được trả về trong rawResponse để lưu đối soát.
 */
@Slf4j
@Component
public class GeminiEvaluationClient implements AiEvaluationClient {

    private static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");

    /** Prompt theo chứng chỉ: %1$s tên chứng chỉ, %2$s / %3$s / %4$s điểm min / max / bước. */
    static final String SYSTEM_PROMPT = """
            You are a certified %1$s examiner. Grade the candidate's response strictly against the official %1$s scoring criteria.
            Use the %1$s scale for this skill: from %2$s to %3$s in steps of %4$s.
            Score exactly 4 criteria on that scale (map each to the closest official %1$s criterion):
            - task_achievement: Task Achievement / Task Response (Writing) or Fluency & task fulfilment (Speaking)
            - coherence_cohesion: Coherence and Cohesion / Organisation
            - lexical_resource: Vocabulary / Lexical Resource
            - grammatical_accuracy: Grammatical Range and Accuracy
            band_score is the overall score on the same scale, following the official %1$s rules.
            cefr_level is the CEFR level equivalent to band_score: one of A1, A2, B1, B2, C1, C2.
            Write every comment, the feedback and the suggestions in Vietnamese, concrete and actionable, quoting the candidate's text where useful.
            Reply with ONLY one JSON object, no markdown, in this exact shape:
            {"band_score": 0,
             "cefr_level": "B1",
             "task_achievement": {"band": 0, "comment": "..."},
             "coherence_cohesion": {"band": 0, "comment": "..."},
             "lexical_resource": {"band": 0, "comment": "..."},
             "grammatical_accuracy": {"band": 0, "comment": "..."},
             "feedback": "...",
             "suggestions": ["...", "..."]}
            If the response is empty, off-topic or not English, give low scores and explain why.
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
    public AiEvaluationResult evaluate(PracticeExamType type, String question, String answer, ScoringScale scale) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        }
        String systemPrompt = SYSTEM_PROMPT.formatted(scale.certificateName(), scale.minScore().toPlainString(),
                scale.maxScore().toPlainString(), scale.step().toPlainString());
        String userPrompt = "Skill: " + scale.certificateName() + " " + type.name() + "\n\nTask / question:\n" + question
                + "\n\nCandidate response" + (type == PracticeExamType.SPEAKING ? " (transcript of the spoken answer)" : "")
                + ":\n" + answer;
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
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
        return parse(raw, scale);
    }

    AiEvaluationResult parse(String raw, ScoringScale scale) {
        try {
            String text = json.readTree(raw).path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("");
            // Phòng khi model vẫn bọc ```json ... ```
            text = text.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            JsonNode result = json.readTree(text);

            CriterionScoreData ta = criterion(result.path("task_achievement"), scale);
            CriterionScoreData cc = criterion(result.path("coherence_cohesion"), scale);
            CriterionScoreData lr = criterion(result.path("lexical_resource"), scale);
            CriterionScoreData ga = criterion(result.path("grammatical_accuracy"), scale);
            BigDecimal overall = scale.normalize(result.path("band_score").isNumber()
                    ? result.path("band_score").decimalValue()
                    : ta.band().add(cc.band()).add(lr.band()).add(ga.band())
                            .divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
            String level = result.path("cefr_level").asText("").trim().toUpperCase();

            List<String> suggestions = new ArrayList<>();
            result.path("suggestions").forEach(s -> {
                if (StringUtils.hasText(s.asText())) {
                    suggestions.add(s.asText().trim());
                }
            });
            return new AiEvaluationResult(overall, ta, cc, lr, ga, result.path("feedback").asText("").trim(),
                    suggestions, model, raw, CEFR_LEVELS.contains(level) ? level : null);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not parse Gemini response: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
    }

    private CriterionScoreData criterion(JsonNode node, ScoringScale scale) {
        if (!node.path("band").isNumber()) {
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
        return new CriterionScoreData(scale.normalize(node.path("band").decimalValue()),
                node.path("comment").asText("").trim());
    }
}
