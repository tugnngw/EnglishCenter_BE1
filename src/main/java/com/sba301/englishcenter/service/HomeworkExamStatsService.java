package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.response.HomeworkExamStatsResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thống kê kết quả làm bài của học viên cho bài tập TRẮC NGHIỆM (Nhóm A).
 * <p>
 * Bài trắc nghiệm được hệ thống chấm tự động → mentor KHÔNG thao tác chấm,
 * chỉ xem bảng thống kê để nắm điểm yếu chung của học viên.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkExamStatsService {

    private final CourseContentRepository courseContentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final HomeworkGradingService gradingService;

    /**
     * Bảng thống kê kết quả làm bài của một bài tập trắc nghiệm.
     *
     * @param homeworkContentId bài tập (course_content có content_type = homework)
     */
    @Transactional(readOnly = true)
    public HomeworkExamStatsResponse getStats(UUID homeworkContentId) {
        CourseContent homework = courseContentRepository.findById(homeworkContentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        if (homework.getContentType() != ContentType.HOMEWORK) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Nội dung này không phải bài tập (homework)");
        }
        Exam exam = homework.getExam();
        if (exam == null) {
            throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED,
                    "Bài tập trắc nghiệm chưa được liên kết đề thi");
        }

        // Các bài nộp trắc nghiệm của bài tập này (mới nhất trước).
        List<HomeworkSubmission> submissions =
                submissionRepository
                        .findByHomeworkContent_ContentIdOrderBySubmittedDateDesc(homeworkContentId)
                        .stream()
                        .filter(s -> "multiple_choice".equalsIgnoreCase(s.getSubmissionType()))
                        .toList();

        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(exam.getExamId());
        Map<UUID, Integer> displayNumbers = new LinkedHashMap<>();
        List<ExamQuestion> ordered = ExamMapper.orderQuestions(examQuestions);
        for (int i = 0; i < ordered.size(); i++) {
            displayNumbers.put(ordered.get(i).getQuestion().getQuestionId(), i + 1);
        }

        // Kết quả từng học viên + tổng hợp.
        List<HomeworkExamStatsResponse.HomeworkExamStudentResult> studentResults = new ArrayList<>();
        BigDecimal scoreSum = BigDecimal.ZERO;
        BigDecimal maxScoreSum = BigDecimal.ZERO;
        int passedCount = 0;
        int failedCount = 0;
        int total = 0;

        // Thống kê từng câu (điểm yếu chung).
        Map<UUID, int[]> questionCounts = new LinkedHashMap<>();
        Map<UUID, ExamQuestion> questionById = new LinkedHashMap<>();
        for (ExamQuestion eq : ordered) {
            questionById.put(eq.getQuestion().getQuestionId(), eq);
            questionCounts.put(eq.getQuestion().getQuestionId(), new int[]{0, 0, 0}); // correct, incorrect, unanswered
        }

        for (HomeworkSubmission submission : submissions) {
            ExamAttempt attempt = submission.getExamAttempt();
            if (attempt == null) {
                continue;
            }
            BigDecimal score = attempt.getScore() != null ? attempt.getScore() : BigDecimal.ZERO;
            BigDecimal maxScore = attempt.getMaxScore() != null ? attempt.getMaxScore() : BigDecimal.ZERO;
            BigDecimal percentage = maxScore.signum() > 0
                    ? score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Boolean passed = submission.getPassed();

            User student = submission.getStudent();
            studentResults.add(HomeworkExamStatsResponse.HomeworkExamStudentResult.builder()
                    .studentId(student != null ? student.getUserId() : null)
                    .studentName(student != null ? student.getFullName() : null)
                    .studentEmail(student != null ? student.getEmail() : null)
                    .score(score)
                    .maxScore(maxScore)
                    .percentage(percentage)
                    .passed(passed)
                    .submittedDate(submission.getSubmittedDate())
                    .build());

            scoreSum = scoreSum.add(score);
            maxScoreSum = maxScoreSum.add(maxScore);
            total++;
            if (Boolean.TRUE.equals(passed)) {
                passedCount++;
            } else {
                failedCount++;
            }

            // Cộng thống kê từng câu từ lượt thi.
            for (ExamAnswer answer : examAnswerRepository.findDetailedByAttemptId(attempt.getAttemptId())) {
                int[] counts = questionCounts.get(answer.getQuestion().getQuestionId());
                if (counts == null) {
                    continue;
                }
                Question question = answer.getQuestion();
                boolean answered = isAnswered(question, answer);
                if (!answered) {
                    counts[2]++;
                } else if (Boolean.TRUE.equals(answer.getIsCorrect())) {
                    counts[0]++;
                } else {
                    counts[1]++;
                }
            }
        }

        List<HomeworkExamStatsResponse.HomeworkExamQuestionStat> questionStats = new ArrayList<>();
        for (ExamQuestion eq : ordered) {
            Question question = eq.getQuestion();
            int[] counts = questionCounts.get(question.getQuestionId());
            if (counts == null) {
                continue;
            }
            BigDecimal correctRate = total > 0
                    ? BigDecimal.valueOf(counts[0]).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            questionStats.add(HomeworkExamStatsResponse.HomeworkExamQuestionStat.builder()
                    .questionId(question.getQuestionId())
                    .displayNumber(displayNumbers.get(question.getQuestionId()))
                    .questionText(question.getQuestionText())
                    .questionType(question.getQuestionType() != null ? question.getQuestionType().name() : null)
                    .points(ExamGrader.points(question))
                    .correctCount(counts[0])
                    .incorrectCount(counts[1])
                    .unansweredCount(counts[2])
                    .correctRate(correctRate)
                    .build());
        }

        BigDecimal averageScore = total > 0
                ? scoreSum.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgMax = total > 0
                ? maxScoreSum.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal averagePercentage = avgMax.signum() > 0
                ? averageScore.multiply(BigDecimal.valueOf(100)).divide(avgMax, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return HomeworkExamStatsResponse.builder()
                .homeworkContentId(homeworkContentId)
                .homeworkTitle(homework.getTitle())
                .totalSubmissions(total)
                .averageScore(averageScore)
                .maxScore(avgMax)
                .averagePercentage(averagePercentage)
                .passedCount(passedCount)
                .failedCount(failedCount)
                .studentResults(studentResults)
                .questionStats(questionStats)
                .build();
    }

    /** Câu có được trả lời không (theo loại câu). */
    private boolean isAnswered(Question question, ExamAnswer answer) {
        if (answer == null) {
            return false;
        }
        return switch (question.getQuestionType()) {
            case multiple_choice, true_false -> answer.getSelectedOption() != null;
            case matching -> !ExamGrader.parseMatching(answer.getAnswerText()).isEmpty();
            case fill_blank, essay -> answer.getAnswerText() != null && !answer.getAnswerText().isBlank();
        };
    }
}
