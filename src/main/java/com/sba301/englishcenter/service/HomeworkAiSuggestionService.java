package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.response.HomeworkAiSuggestionResponse;
import com.sba301.englishcenter.dto.response.HomeworkRubricCriterion;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.impl.ScoringScaleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gợi ý chấm từ AI cho MENTOR (nút "Xem gợi ý chấm từ AI").
 * <p>
 * <b>Khác với {@link HomeworkAiGradingService}:</b> ở đây AI KHÔNG ghi điểm vào
 * hệ thống — AI chỉ chấm thử để mentor tham khảo, mentor vẫn là người quyết
 * định điểm chính thức. Vì vậy không đụng vào {@code score} / {@code passed} /
 * {@code graded_date} của bài nộp.
 * <p>
 * Áp dụng cho bài THỦ CÔNG (text / file / audio) của học viên đã mua gói mentor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkAiSuggestionService {

    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkGradingService gradingService;
    private final AiEvaluationClient aiEvaluationClient;
    private final ScoringScaleResolver scoringScaleResolver;
    private final PlatformTransactionManager transactionManager;

    /**
     * Trả về gợi ý chấm từ AI cho một bài nộp (KHÔNG ghi điểm vào hệ thống).
     *
     * @param submissionId bài nộp cần gợi ý
     * @return gợi ý (điểm, nhận xét, điểm yếu, gợi ý sửa) hoặc null nếu bài không có nội dung
     */
    public HomeworkAiSuggestionResponse suggest(UUID submissionId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Đọc bài nộp (transaction ngắn) — CHỈ mentor được phép xem bài này mới xem được gợi ý.
        HomeworkSubmission snapshot = tx.execute(status -> submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND)));
        if (snapshot == null) {
            return null;
        }

        // Chặn mentor xem gợi ý của bài không thuộc phạm vi được phép.
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        gradingService.assertCanGrade(snapshot, principal);

        CourseContent homework = snapshot.getHomeworkContent();
        String answerText = resolveAnswerText(snapshot);
        if (!StringUtils.hasText(answerText)) {
            return HomeworkAiSuggestionResponse.builder()
                    .suggestedScore(BigDecimal.ZERO)
                    .feedback("Bài làm trống nên AI không chấm được.")
                    .weaknesses(List.of())
                    .suggestions(List.of())
                    .build();
        }

        UUID studentId = snapshot.getStudent() != null
                ? snapshot.getStudent().getUserId()
                : null;
        PracticeExamType skill = resolveSkill(homework);
        ScoringScale scale = scoringScaleResolver.resolve(studentId, skill.name());

        // 2. Gọi AI NGOÀI transaction (AI chậm 10-60s, không giữ khoá dòng DB).
        AiEvaluationResult result =
                aiEvaluationClient.evaluate(skill, buildTask(homework), answerText, scale);

        // 3. Quy đổi điểm AI về thang của bài tập để mentor so sánh với ô điểm.
        BigDecimal maxScore = gradingService.resolveMaxScore(homework);
        BigDecimal suggestedScore = convertToHomeworkScale(result.bandScore(), scale, maxScore);

        return HomeworkAiSuggestionResponse.builder()
                .suggestedScore(suggestedScore)
                .scaleLabel(scale != null ? scale.certificateName() : null)
                .feedback(result.feedback())
                .weaknesses(result.weaknesses() != null ? result.weaknesses() : List.of())
                .suggestions(result.suggestions() != null ? result.suggestions() : List.of())
                .cefrLevel(result.cefrLevel())
                .build();
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    /**
     * Nội dung để AI chấm.
     * <ul>
     *   <li>{@code text} → {@code answer_content} (bài viết);</li>
     *   <li>{@code file} → tên file tài liệu học viên nộp;</li>
     *   <li>{@code audio} → {@code answer_content} là bản phiên âm.</li>
     * </ul>
     */
    private String resolveAnswerText(HomeworkSubmission submission) {
        String answer = submission.getAnswerContent();
        if (StringUtils.hasText(answer)) {
            return answer.trim();
        }
        if ("file".equalsIgnoreCase(submission.getSubmissionType())) {
            String name = submission.getFileOriginalName();
            return name == null || name.isBlank()
                    ? "Học viên nộp một file tài liệu."
                    : "Học viên nộp file: " + name;
        }
        return null;
    }

    /**
     * Kỹ năng của bài tập → quyết định cách AI chấm (Writing hay Speaking).
     */
    private PracticeExamType resolveSkill(CourseContent homework) {
        String configured = homework == null
                ? null
                : HomeworkContentData.readText(homework.getContentData(), "skill");
        if (configured != null) {
            if (configured.trim().toUpperCase().contains("SPEAK")) {
                return PracticeExamType.SPEAKING;
            }
            if (configured.trim().toUpperCase().contains("WRIT")) {
                return PracticeExamType.WRITING;
            }
        }
        String type = homework == null
                ? null
                : HomeworkContentData.readText(homework.getContentData(), "submissionType");
        if (type != null && "audio".equalsIgnoreCase(type.trim())) {
            return PracticeExamType.SPEAKING;
        }
        return PracticeExamType.WRITING;
    }

    /** Đề bài gửi cho AI: tên bài + mô tả + hướng dẫn + rubric của bài tập. */
    private String buildTask(CourseContent homework) {
        if (homework == null) {
            return "Bài tập tự do";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(homework.getTitle())) {
            parts.add("Bài tập: " + homework.getTitle());
        }
        if (StringUtils.hasText(homework.getDescription())) {
            parts.add("Mô tả: " + homework.getDescription());
        }
        String instructions = HomeworkContentData.readText(homework.getContentData(), "instructions");
        if (StringUtils.hasText(instructions)) {
            parts.add("Hướng dẫn: " + instructions);
        }
        String rubric = readRubricText(homework);
        if (StringUtils.hasText(rubric)) {
            parts.add("Tiêu chí chấm:\n" + rubric);
        }
        return parts.isEmpty() ? "Bài tập tự do" : String.join("\n", parts);
    }

    /** Rubric do Admin cấu hình → text để AI chấm theo đúng tiêu chí. */
    private String readRubricText(CourseContent homework) {
        List<HomeworkRubricCriterion> rubric =
                HomeworkContentData.readRubric(homework == null ? null : homework.getContentData());
        if (rubric.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (HomeworkRubricCriterion r : rubric) {
            sb.append("- ").append(r.getCriterion());
            if (StringUtils.hasText(r.getDescription())) {
                sb.append(": ").append(r.getDescription());
            }
            if (r.getMaxScore() != null) {
                sb.append(" (tối đa ").append(r.getMaxScore().stripTrailingZeros().toPlainString())
                        .append(" điểm)");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Quy đổi điểm AI (thang chứng chỉ) sang thang của bài tập (tỉ lệ).
     */
    private BigDecimal convertToHomeworkScale(BigDecimal aiScore, ScoringScale scale, BigDecimal maxScore) {
        if (aiScore == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (maxScore == null || maxScore.signum() <= 0) {
            return aiScore.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal scaleMax = scale != null ? scale.maxScore() : null;
        if (scaleMax == null || scaleMax.signum() <= 0) {
            return aiScore.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal percentage = aiScore.multiply(BigDecimal.valueOf(100))
                .divide(scaleMax, 4, RoundingMode.HALF_UP);
        return percentage.multiply(maxScore)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
