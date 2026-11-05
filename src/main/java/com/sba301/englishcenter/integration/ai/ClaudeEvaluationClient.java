package com.sba301.englishcenter.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.dto.practice.PronunciationIssueData;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chấm bài Writing / Speaking bằng Claude theo 4 tiêu chí, theo thang của chứng chỉ mục tiêu.
 * Speaking: chấm trên bản phiên âm (Claude không nhận file âm thanh) - lỗi phát âm được suy ra từ những từ
 * bị máy nhận dạng sai trong bản ghi.
 * <p>
 * Bài trống / không viết bằng tiếng Anh (VD viết tiếng Việt) -> 0 điểm ngay, không gọi AI (đỡ tốn token).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeEvaluationClient implements AiEvaluationClient {

    private static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final int MAX_TOKENS = 4000;
    // Quá 15% chữ cái có dấu / không phải A-Z (tiếng Việt, ...) thì coi như không viết bằng tiếng Anh
    private static final double MAX_NON_LATIN_RATIO = 0.15;
    static final String MODEL_RULE_BASED = "rule-based";

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
            HARD RULE: if the response is empty, not written in English (e.g. Vietnamese), only copies the task,
            or is completely unrelated to the task, then every criterion band AND band_score MUST be exactly %2$s
            (the minimum of the scale, never a "low but non-zero" score), and feedback must say why.
            Write every comment, the feedback, the weaknesses and the suggestions in Vietnamese.
            Be specific, never generic: each criterion comment must say what lowered the score and quote the candidate's
            exact words (in "...") with the corrected version.
            feedback: 2-4 sentences explaining WHY the candidate got this band_score (which criteria pulled it down).
            weaknesses: 2-5 concrete weaknesses, each quoting the candidate's words.
            suggestions: 2-5 actionable steps to reach the next band.
            %5$s
            Reply with ONLY one JSON object, no markdown, in this exact shape:
            {"band_score": 0,
             "cefr_level": "B1",
             "task_achievement": {"band": 0, "comment": "..."},
             "coherence_cohesion": {"band": 0, "comment": "..."},
             "lexical_resource": {"band": 0, "comment": "..."},
             "grammatical_accuracy": {"band": 0, "comment": "..."},
             "feedback": "...",
             "weaknesses": ["...", "..."],
             "suggestions": ["...", "..."],
             "pronunciation_issues": [{"word": "...", "heard_as": "...", "tip": "..."}]}
            """;

    /** Thêm vào prompt khi chấm Speaking: suy lỗi phát âm từ bản ghi. */
    static final String SPEAKING_RULE = """
            The response is an automatic speech-to-text transcript of the candidate's recording.
            Words that the recogniser turned into wrong, odd or nonsensical words for the context usually mean the
            candidate mispronounced them: list them in pronunciation_issues (word = the word the candidate most likely
            meant, heard_as = what appears in the transcript, tip = how to pronounce it correctly, in Vietnamese,
            e.g. stress, ending sounds /s/ /t/ /d/, /θ/ /ð/, long vs short vowels). Use an empty array if none.
            Also judge fluency from fillers, repetitions and very short / broken sentences in the transcript.
            """;

    private final ClaudeClient claude;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public AiEvaluationResult evaluate(PracticeExamType type, String question, String answer, ScoringScale scale) {
        // Bài trống / không phải tiếng Anh: 0 điểm luôn, không cần gọi AI
        String reason = invalidAnswerReason(answer);
        if (reason != null) {
            return minimumScore(scale, reason);
        }

        String systemPrompt = SYSTEM_PROMPT.formatted(scale.certificateName(), scale.minScore().toPlainString(),
                scale.maxScore().toPlainString(), scale.step().toPlainString(),
                type == PracticeExamType.SPEAKING ? SPEAKING_RULE : "pronunciation_issues must be an empty array.");
        String userPrompt = "Skill: " + scale.certificateName() + " " + type.name() + "\n\nTask / question:\n" + question
                + "\n\nCandidate response" + (type == PracticeExamType.SPEAKING ? " (transcript of the spoken answer)" : "")
                + ":\n" + answer;
        String text = claude.generate(systemPrompt, null, userPrompt, MAX_TOKENS, ErrorCode.AI_EVALUATION_FAILED);
        return parse(text, scale);
    }

    /** @return lý do cho 0 điểm (tiếng Việt), null nếu bài hợp lệ để AI chấm */
    static String invalidAnswerReason(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "Bài làm trống nên không có nội dung để chấm.";
        }
        int letters = 0;
        int nonLatin = 0;
        for (char c : answer.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                    nonLatin++;
                }
            }
        }
        if (letters == 0) {
            return "Bài làm không có chữ tiếng Anh nào nên không chấm được.";
        }
        if ((double) nonLatin / letters > MAX_NON_LATIN_RATIO) {
            return "Bài làm không viết bằng tiếng Anh (có nhiều chữ tiếng Việt / ký tự có dấu). "
                    + "Đề thi tiếng Anh yêu cầu trả lời bằng tiếng Anh nên mọi tiêu chí đều 0 điểm.";
        }
        return null;
    }

    /** Mọi tiêu chí = điểm thấp nhất của thang (IELTS / TOEIC / VSTEP: 0). */
    static AiEvaluationResult minimumScore(ScoringScale scale, String reason) {
        BigDecimal zero = scale.normalize(scale.minScore());
        CriterionScoreData c = new CriterionScoreData(zero, reason);
        return new AiEvaluationResult(zero, c, c, c, c, reason,
                List.of("Đọc kỹ đề và trả lời hoàn toàn bằng tiếng Anh.",
                        "Viết / nói đủ ý theo yêu cầu của đề, kể cả câu ngắn đơn giản vẫn được tính điểm."),
                MODEL_RULE_BASED, null, "A1", List.of(reason), List.of());
    }

    AiEvaluationResult parse(String text, ScoringScale scale) {
        try {
            JsonNode result = json.readTree(ClaudeClient.jsonObjectOf(text));

            CriterionScoreData ta = criterion(result.path("task_achievement"), scale);
            CriterionScoreData cc = criterion(result.path("coherence_cohesion"), scale);
            CriterionScoreData lr = criterion(result.path("lexical_resource"), scale);
            CriterionScoreData ga = criterion(result.path("grammatical_accuracy"), scale);
            BigDecimal overall = scale.normalize(result.path("band_score").isNumber()
                    ? result.path("band_score").decimalValue()
                    : ta.band().add(cc.band()).add(lr.band()).add(ga.band())
                            .divide(new BigDecimal("4"), 4, RoundingMode.HALF_UP));
            String level = result.path("cefr_level").asText("").trim().toUpperCase();

            List<PronunciationIssueData> pronunciation = new ArrayList<>();
            result.path("pronunciation_issues").forEach(p -> {
                String word = p.path("word").asText("").trim();
                if (StringUtils.hasText(word)) {
                    pronunciation.add(new PronunciationIssueData(word, p.path("heard_as").asText("").trim(),
                            p.path("tip").asText("").trim()));
                }
            });
            return new AiEvaluationResult(overall, ta, cc, lr, ga, result.path("feedback").asText("").trim(),
                    texts(result.path("suggestions")), claude.model(), text,
                    CEFR_LEVELS.contains(level) ? level : null, texts(result.path("weaknesses")), pronunciation);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not parse Claude evaluation: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(s -> {
            if (StringUtils.hasText(s.asText())) {
                values.add(s.asText().trim());
            }
        });
        return values;
    }

    private CriterionScoreData criterion(JsonNode node, ScoringScale scale) {
        if (!node.path("band").isNumber()) {
            throw new AppException(ErrorCode.AI_EVALUATION_FAILED);
        }
        return new CriterionScoreData(scale.normalize(node.path("band").decimalValue()),
                node.path("comment").asText("").trim());
    }
}
