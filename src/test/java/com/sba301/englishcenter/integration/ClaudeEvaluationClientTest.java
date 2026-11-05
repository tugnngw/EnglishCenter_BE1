package com.sba301.englishcenter.integration;

import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ClaudeClient;
import com.sba301.englishcenter.integration.ai.ClaudeEvaluationClient;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Gọi Claude qua 1 HTTP server giả trên máy (không cần API key / mạng thật).
 */
class ClaudeEvaluationClientTest {

    private HttpServer server;
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> versionHeader = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    private String start(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            versionHeader.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            path.set(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String claudeReply(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + escaped + "\"}],\"stop_reason\":\"end_turn\"}";
    }

    private static ClaudeEvaluationClient client(String url, String key, String model) {
        return new ClaudeEvaluationClient(new ClaudeClient(url, key, model, 5));
    }

    @Test
    void parsesFencedJsonAndNormalizesBands() throws Exception {
        String result = """
                ```json
                {"band_score": 6.8,
                 "task_achievement": {"band": 7.3, "comment": "Trả lời đủ ý"},
                 "coherence_cohesion": {"band": 6, "comment": "Liên kết ổn"},
                 "lexical_resource": {"band": 10, "comment": "Từ vựng tốt"},
                 "grammatical_accuracy": {"band": -1, "comment": "Sai thì"},
                 "feedback": "Bài khá tốt", "suggestions": ["Dùng câu phức", " "]}
                ```""";
        var client = client(start(200, claudeReply(result)), "test-key", "claude-test");

        AiEvaluationResult r = client.evaluate(PracticeExamType.WRITING, "Describe the chart", "My essay", ScoringScale.IELTS);

        assertEquals("test-key", apiKeyHeader.get());
        assertEquals("2023-06-01", versionHeader.get());
        assertEquals("/v1/messages", path.get());
        assertEquals("7.0", r.bandScore().toPlainString());
        assertEquals("7.5", r.taskAchievement().band().toPlainString());
        assertEquals("9.0", r.lexicalResource().band().toPlainString());
        assertEquals("0.0", r.grammaticalAccuracy().band().toPlainString());
        assertEquals(1, r.suggestions().size());
        assertEquals("claude-test", r.model());
    }

    @Test
    void missingOverallBandIsAverageOfCriteria() throws Exception {
        String result = "Here is the result: {\"task_achievement\":{\"band\":6},\"coherence_cohesion\":{\"band\":6.5},"
                + "\"lexical_resource\":{\"band\":7},\"grammatical_accuracy\":{\"band\":6},\"feedback\":\"ok\"}";
        var client = client(start(200, claudeReply(result)), "k", "m");
        // (6 + 6.5 + 7 + 6) / 4 = 6.375 -> 6.5
        assertEquals("6.5", client.evaluate(PracticeExamType.SPEAKING, "q", "a", ScoringScale.IELTS).bandScore().toPlainString());
    }

    @Test
    void noApiKeyMeansNotConfigured() {
        var client = client("http://127.0.0.1:1", "", "m");
        AppException ex = assertThrows(AppException.class, () -> client.evaluate(PracticeExamType.WRITING, "q", "a", ScoringScale.IELTS));
        assertEquals(ErrorCode.AI_NOT_CONFIGURED, ex.getErrorCode());
    }

    @Test
    void httpErrorOrGarbageMeansEvaluationFailed() throws Exception {
        var failing = client(start(500, "{\"error\":\"boom\"}"), "k", "m");
        assertEquals(ErrorCode.AI_EVALUATION_FAILED,
                assertThrows(AppException.class, () -> failing.evaluate(PracticeExamType.WRITING, "q", "a", ScoringScale.IELTS)).getErrorCode());
        stop();

        var garbage = client(start(200, claudeReply("not json at all")), "k", "m");
        AppException ex = assertThrows(AppException.class, () -> garbage.evaluate(PracticeExamType.WRITING, "q", "a", ScoringScale.IELTS));
        assertEquals(ErrorCode.AI_EVALUATION_FAILED, ex.getErrorCode());
    }

    // ===================== Dev 5: bài không phải tiếng Anh / trống -> 0 điểm, không gọi AI =====================

    @Test
    void vietnameseAnswerScoresZeroWithoutCallingAi() throws Exception {
        var client = client(start(500, "{\"error\":\"should not be called\"}"), "test-key", "claude-test");

        AiEvaluationResult r = client.evaluate(PracticeExamType.WRITING, "Describe the chart",
                "Biểu đồ cho thấy số lượng sinh viên tăng mạnh trong năm qua", ScoringScale.IELTS);

        assertEquals("0.0", r.bandScore().toPlainString());
        assertEquals("0.0", r.taskAchievement().band().toPlainString());
        assertEquals("rule-based", r.model());
        assertEquals(null, path.get());
    }

    @Test
    void blankAnswerScoresZero() throws Exception {
        var client = client(start(500, "{}"), "test-key", "claude-test");

        AiEvaluationResult r = client.evaluate(PracticeExamType.SPEAKING, "Talk about your hobby", "   ", ScoringScale.IELTS);

        assertEquals("0.0", r.bandScore().toPlainString());
        assertEquals(null, path.get());
    }

    @Test
    void parsesWeaknessesAndPronunciationIssues() throws Exception {
        String result = """
                {"band_score": 5, "cefr_level": "B1",
                 "task_achievement": {"band": 5, "comment": "a"},
                 "coherence_cohesion": {"band": 5, "comment": "b"},
                 "lexical_resource": {"band": 5, "comment": "c"},
                 "grammatical_accuracy": {"band": 4.5, "comment": "d"},
                 "feedback": "f", "weaknesses": ["Sai thì \\"I go\\""], "suggestions": ["s"],
                 "pronunciation_issues": [{"word": "think", "heard_as": "sink", "tip": "âm /θ/"}, {"word": " "}]}""";
        var client = client(start(200, claudeReply(result)), "test-key", "claude-test");

        AiEvaluationResult r = client.evaluate(PracticeExamType.SPEAKING, "q", "I sink it is good", ScoringScale.IELTS);

        assertEquals(1, r.weaknesses().size());
        assertEquals(1, r.pronunciationIssues().size());
        assertEquals("sink", r.pronunciationIssues().get(0).heardAs());
    }
}
