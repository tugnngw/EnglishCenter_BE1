package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.HomeworkGraderType;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkGradeHistory;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.HomeworkGradeHistoryRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.service.impl.ScoringScaleResolver;
import com.sba301.englishcenter.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chấm bài tập bằng AI cho học viên KHÔNG mua mentor 1-1.
 * <p>
 * Áp dụng cho bài tự luận ({@code text}), bài nộp file ({@code file}) và bài ghi âm
 * ({@code audio}). Bài trắc nghiệm vốn đã tự chấm từ lượt thi nên không đi qua đây.
 * <p>
 * <b>Thang điểm:</b> AI chấm theo thang chứng chỉ (IELTS 0-9, TOEIC 0-200…) rồi hệ
 * thống QUY ĐỔI về thang của bài tập ({@code content_data.maxScore}) để học viên thấy
 * đúng thang đã cấu hình, và tính {@code passed} theo {@code passingScore} của bài tập.
 * <p>
 * AI chấm mất 10 - 60 giây → gọi AI <b>ngoài transaction</b> (không giữ khoá dòng
 * trong lúc chờ), giống {@code ExamAttemptServiceImpl.aiGradeAttempt}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkAiGradingService {

    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkGradeHistoryRepository historyRepository;
    private final HomeworkGradingService gradingService;
    private final HomeworkGraderAssignmentService assignmentService;
    private final AiEvaluationClient aiEvaluationClient;
    private final ScoringScaleResolver scoringScaleResolver;
    private final PlatformTransactionManager transactionManager;

    /**
     * Chấm bài nộp bằng AI (dùng khi học viên không mua mentor 1-1).
     * <p>
     * Trả về {@code true} nếu đã chấm xong, {@code false} nếu bài này có mentor được
     * giao chấm tay (hoặc đã chấm rồi) → không đụng vào.
     *
     * @param submissionId bài nộp cần chấm
     * @param studentId    học viên (dùng để xác định thang điểm chứng chỉ)
     */
    public boolean gradeByAi(UUID submissionId, UUID studentId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Đọc bài nộp + kiểm tra có được giao cho mentor không (transaction ngắn)
        HomeworkSubmission snapshot = tx.execute(status -> {
            HomeworkSubmission submission = submissionRepository.findById(submissionId)
                    .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));
            // Có mentor 1-1 → mentor chấm tay, AI không đụng vào.
            if (assignmentService.isMentorGraded(submission)) {
                return null;
            }
            // Đã chấm rồi (trắc nghiệm tự chấm) → giữ nguyên.
            if (submission.getGradedDate() != null) {
                return null;
            }
            return submission;
        });
        if (snapshot == null) {
            return false;
        }

        CourseContent homework = snapshot.getHomeworkContent();
        String answerText = resolveAnswerText(snapshot);
        if (!StringUtils.hasText(answerText)) {
            // Bài trống (hoặc bài ghi âm chưa có phiên âm) → 0 điểm, không gọi AI (đỡ tốn token).
            return saveAiGrade(snapshot, studentId, null, BigDecimal.ZERO,
                    "Bài làm trống nên không có nội dung để chấm.");
        }

        // 2. Gọi AI ngoài transaction theo thang của chứng chỉ mục tiêu
        PracticeExamType skill = resolveSkill(homework);
        ScoringScale scale = scoringScaleResolver.resolve(studentId, skill.name());
        AiEvaluationResult result = aiEvaluationClient.evaluate(skill, buildTask(homework), answerText, scale);

        // 3. Quy đổi về thang của bài tập rồi ghi điểm
        BigDecimal maxScore = gradingService.resolveMaxScore(homework);
        BigDecimal score = convertToHomeworkScale(result.bandScore(), scale, maxScore);
        String feedback = buildFeedback(result, score, maxScore);
        return saveAiGrade(snapshot, studentId, result, score, feedback);
    }

    /**
     * Ghi điểm AI vào bài nộp + lịch sử chấm (transaction ngắn).
     *
     * @return true nếu ghi thành công
     */
    private boolean saveAiGrade(HomeworkSubmission snapshot, UUID studentId,
                                AiEvaluationResult result, BigDecimal score, String feedback) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return Boolean.TRUE.equals(tx.execute(status -> {
            HomeworkSubmission submission = submissionRepository.findById(snapshot.getSubmissionId())
                    .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));
            // Mentor đã nhận chấm trong lúc AI đang chạy → giữ điểm của mentor.
            if (submission.getGradedDate() != null || assignmentService.isMentorGraded(submission)) {
                return false;
            }
            CourseContent homework = submission.getHomeworkContent();

            BigDecimal oldScore = submission.getScore();
            Boolean oldPassed = submission.getPassed();

            submission.setScore(score);
            submission.setFeedback(feedback);
            submission.setGradedDate(LocalDateTime.now());
            Boolean passed = gradingService.resolvePassed(homework, score);
            submission.setPassed(passed);
            submission.setGradedByType(HomeworkGraderType.ai.name());
            submission = submissionRepository.save(submission);

            // Lịch sử chấm (AI không có user → gradedBy = null).
            historyRepository.save(HomeworkGradeHistory.builder()
                    .submission(submission)
                    .gradedBy(null)
                    .gradedByType(HomeworkGraderType.ai.name())
                    .oldScore(oldScore)
                    .newScore(score)
                    .passed(passed)
                    .feedback(feedback)
                    .gradedAt(LocalDateTime.now())
                    .build());

            log.info("AI chấm bài nộp {} của học viên {}: {} điểm, passed={}",
                    submission.getSubmissionId(), studentId, score, passed);
            return true;
        }));
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    /**
     * Nội dung để AI chấm.
     * <ul>
     *   <li>{@code text} → {@code answer_content};</li>
     *   <li>{@code file} → tên file (BE không tải file về để OCR — học viên nộp file
     *       tài liệu, phần chữ trả lời mới là thứ AI chấm được);</li>
     *   <li>{@code audio} → {@code answer_content} là bản phiên âm; không có thì trả null
     *       (bài 0 điểm, chờ mentor).</li>
     * </ul>
     */
    private String resolveAnswerText(HomeworkSubmission submission) {
        String answer = submission.getAnswerContent();
        if (StringUtils.hasText(answer)) {
            return answer.trim();
        }
        String type = submission.getSubmissionType();
        if ("file".equalsIgnoreCase(type)) {
            String name = submission.getFileOriginalName();
            return name == null || name.isBlank()
                    ? "Học viên nộp một file tài liệu."
                    : "Học viên nộp file: " + name;
        }
        return null;
    }

    /**
     * Kỹ năng của bài tập → quyết định cách AI chấm (Writing hay Speaking).
     * <p>
     * Bài ghi âm → SPEAKING; còn lại → WRITING. Có thể ghi đè bằng
     * {@code content_data.skill}.
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

    /**
     * Đề bài gửi cho AI: tên bài + mô tả + hướng dẫn + rubric của bài tập.
     */
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
        String rubric = readRubric(homework);
        if (StringUtils.hasText(rubric)) {
            parts.add("Tiêu chí chấm:\n" + rubric);
        }
        return parts.isEmpty() ? "Bài tập tự do" : String.join("\n", parts);
    }

    /**
     * Rubric do Admin cấu hình trong {@code content_data.rubric} → chuyển thành text.
     */
    private String readRubric(CourseContent homework) {
        if (homework == null || homework.getContentData() == null) {
            return null;
        }
        JsonNode node = JsonUtil.parse(homework.getContentData());
        if (node == null || !node.hasNonNull("rubric") || !node.get("rubric").isArray()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode item : node.get("rubric")) {
            String criterion = item.path("criterion").asText("").trim();
            String description = item.path("description").asText("").trim();
            if (!StringUtils.hasText(criterion)) {
                continue;
            }
            String line = "- " + criterion;
            if (StringUtils.hasText(description)) {
                line += ": " + description;
            }
            lines.add(line);
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /**
     * Quy đổi điểm AI (thang chứng chỉ) sang thang của bài tập.
     * <p>
     * Bài tập không cấu hình {@code maxScore} → giữ nguyên điểm AI.
     * Có {@code maxScore} → tính theo TỈ LỆ (điểm AI / max của thang chứng chỉ) × {@code maxScore}.
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

    /**
     * Nhận xét gửi học viên: feedback của AI + các điểm yếu + gợi ý.
     */
    private String buildFeedback(AiEvaluationResult result, BigDecimal score, BigDecimal maxScore) {
        StringBuilder sb = new StringBuilder();
        if (result != null && StringUtils.hasText(result.feedback())) {
            sb.append(result.feedback().trim());
        }
        if (result != null && result.weaknesses() != null && !result.weaknesses().isEmpty()) {
            sb.append("\n\nĐiểm yếu:\n");
            result.weaknesses().forEach(w -> sb.append("- ").append(w).append('\n'));
        }
        if (result != null && result.suggestions() != null && !result.suggestions().isEmpty()) {
            sb.append("\nGợi ý:\n");
            result.suggestions().forEach(s -> sb.append("- ").append(s).append('\n'));
        }
        if (sb.length() == 0) {
            sb.append("Bài được AI chấm tự động.");
        }
        sb.append("\n\n(Điểm ").append(score.stripTrailingZeros().toPlainString());
        if (maxScore != null && maxScore.signum() > 0) {
            sb.append('/').append(maxScore.stripTrailingZeros().toPlainString());
        }
        sb.append(" — AI chấm tự động, bạn chưa mua mentor kèm 1-1.)");
        return sb.toString().trim();
    }
}
