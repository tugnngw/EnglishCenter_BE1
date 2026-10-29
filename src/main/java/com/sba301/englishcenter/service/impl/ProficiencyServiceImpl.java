package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.ProficiencyResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.service.ProficiencyCalculator;
import com.sba301.englishcenter.service.ProficiencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProficiencyServiceImpl implements ProficiencyService {

    /** Lượt đã nộp (kể cả hết giờ tự nộp); đang làm thì chưa tính. */
    private static final EnumSet<AttemptStatus> FINISHED =
            EnumSet.of(AttemptStatus.SUBMITTED, AttemptStatus.GRADED, AttemptStatus.EXPIRED);

    private final ExamAttemptRepository attemptRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAnswerRepository answerRepository;
    private final ExamMapper examMapper;
    private final ProficiencyCalculator calculator;

    @Override
    @Transactional(readOnly = true)
    public List<ProficiencyResponse> getMyProficiency(UUID certificateId) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return getProficiency(studentId, certificateId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProficiencyResponse> getProficiency(UUID studentId, UUID certificateId) {
        // Lượt mới nhất trước -> lấy lượt đầu tiên của mỗi chứng chỉ
        Set<UUID> seen = new LinkedHashSet<>();
        List<ProficiencyResponse> result = new ArrayList<>();
        for (ExamAttempt attempt : attemptRepository.findFinishedByStudent(studentId, FINISHED)) {
            Exam exam = attempt.getExam();
            if (!isPlacementTest(exam)) {
                continue;
            }
            UUID certId = exam.getCertificate().getCertificateId();
            if ((certificateId != null && !certificateId.equals(certId)) || !seen.add(certId)) {
                continue;
            }
            result.add(assess(attempt));
        }
        return result;
    }

    /** Bài kiểm tra năng lực = đề thi thử (trial) loại placement_test có gắn chứng chỉ. */
    private static boolean isPlacementTest(Exam exam) {
        return exam.getExamScope() == ExamScope.TRIAL
                && exam.getExamType() == ExamType.PLACEMENT_TEST
                && exam.getCertificate() != null;
    }

    private ProficiencyResponse assess(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        List<ExamQuestion> questions = examQuestionRepository.findDetailedByExamId(exam.getExamId());
        Map<UUID, ExamAnswer> answers = answerRepository.findDetailedByAttemptId(attempt.getAttemptId()).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getQuestionId(), Function.identity(), (a, b) -> a));
        // staff = true: luôn có điểm từng phần để tính năng lực (kể cả đề ẩn điểm với học viên)
        AttemptResultResponse detail = examMapper.toResult(attempt, questions, answers, true);

        String certificateName = exam.getCertificate().getCertificateName();
        ProficiencyCalculator.Assessment a = calculator.assess(certificateName, detail.getSectionScores(),
                detail.getAttempt().getPercentage());

        return ProficiencyResponse.builder()
                .certificateId(exam.getCertificate().getCertificateId())
                .certificateName(certificateName)
                .scale(a.scale().name().toLowerCase(Locale.ROOT))
                .examId(exam.getExamId())
                .examTitle(exam.getExamTitle())
                .attemptId(attempt.getAttemptId())
                .takenAt(attempt.getEndTime())
                .status(a.pendingGrading() ? "pending_grading" : "assessed")
                .overall(a.overall())
                .overallMax(a.overallMax())
                .level(a.level())
                .cefr(a.cefr())
                .skills(a.skills())
                .build();
    }
}
