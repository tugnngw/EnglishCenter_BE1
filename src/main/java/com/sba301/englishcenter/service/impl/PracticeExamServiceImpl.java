package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.PracticeAnswerStatus;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.practice.PracticeAnswerData;
import com.sba301.englishcenter.dto.practice.PracticeEvaluationData;
import com.sba301.englishcenter.dto.request.PracticeExamRequest;
import com.sba301.englishcenter.dto.request.SubmitPracticeAnswerRequest;
import com.sba301.englishcenter.dto.response.PracticeAnswerResponse;
import com.sba301.englishcenter.dto.response.PracticeEvaluationResponse;
import com.sba301.englishcenter.dto.response.PracticeExamResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.PracticeExam;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.PracticeExamRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.service.CourseService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.PracticeExamService;
import com.sba301.englishcenter.specification.PracticeExamSpecification;
import com.sba301.englishcenter.util.JsonbMapper;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PracticeExamServiceImpl implements PracticeExamService {

    private static final TypeReference<List<PracticeAnswerData>> ANSWERS = new TypeReference<>() {
    };
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final PracticeExamRepository practiceExamRepository;
    private final CourseService courseService;
    private final NotificationService notificationService;
    private final AiEvaluationClient aiEvaluationClient;
    private final ScoringScaleResolver scoringScaleResolver;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final CertificateRepository certificateRepository;
    // AI chấm mất 10 - 60 giây: không giữ transaction / khóa dòng trong lúc chờ AI
    private final TransactionTemplate transactionTemplate;

    public PracticeExamServiceImpl(PracticeExamRepository practiceExamRepository, CourseService courseService,
                                   NotificationService notificationService, AiEvaluationClient aiEvaluationClient,
                                   ScoringScaleResolver scoringScaleResolver,
                                   StudentProfileRepository studentProfileRepository, UserRepository userRepository,
                                   ExamAttemptRepository examAttemptRepository,
                                   CertificateRepository certificateRepository,
                                   PlatformTransactionManager transactionManager) {
        this.practiceExamRepository = practiceExamRepository;
        this.courseService = courseService;
        this.notificationService = notificationService;
        this.aiEvaluationClient = aiEvaluationClient;
        this.scoringScaleResolver = scoringScaleResolver;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.certificateRepository = certificateRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ===================== kho đề =====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PracticeExamResponse> getPracticeExams(PracticeExamType type, String keyword, Pageable pageable) {
        return getPracticeExams(type, keyword, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PracticeExamResponse> getPracticeExams(PracticeExamType type, String keyword, UUID certificateId, Pageable pageable) {
        Page<PracticeExam> page = practiceExamRepository.findAll(PracticeExamSpecification.filter(type, keyword, certificateId),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        return PageResponse.<PracticeExamResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(this::toExamResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeExamResponse getPracticeExam(UUID practiceExamId) {
        return toExamResponse(findExam(practiceExamId));
    }

    @Override
    @Transactional
    public PracticeExamResponse create(UUID adminId, PracticeExamRequest request) {
        validateCourse(request.getCourseId());
        Certificate certificate = resolveCertificate(request.getCertificateId());
        PracticeExam exam = practiceExamRepository.save(PracticeExam.builder()
                .createdByUserId(adminId)
                .courseId(request.getCourseId())
                .certificate(certificate)
                .bankName(trimToNull(request.getBankName()))
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .examType(request.getExamType())
                .answers("[]")
                .build());
        log.info("Admin {} created practice exam {}", adminId, exam.getPracticeExamId());
        return toExamResponse(exam);
    }

    @Override
    @Transactional
    public PracticeExamResponse update(UUID practiceExamId, PracticeExamRequest request) {
        PracticeExam exam = findExam(practiceExamId);
        validateCourse(request.getCourseId());
        Certificate certificate = resolveCertificate(request.getCertificateId());
        exam.setCourseId(request.getCourseId());
        exam.setCertificate(certificate);
        exam.setBankName(trimToNull(request.getBankName()));
        exam.setTitle(request.getTitle().trim());
        exam.setDescription(request.getDescription().trim());
        exam.setExamType(request.getExamType());
        return toExamResponse(practiceExamRepository.save(exam));
    }

    @Override
    @Transactional
    public void delete(UUID practiceExamId) {
        PracticeExam exam = practiceExamRepository.findWithLockByPracticeExamId(practiceExamId)
                .orElseThrow(() -> new AppException(ErrorCode.PRACTICE_EXAM_NOT_FOUND));
        if (!answersOf(exam).isEmpty()) {
            throw new AppException(ErrorCode.PRACTICE_EXAM_HAS_ANSWERS);
        }
        practiceExamRepository.delete(exam);
    }

    // ===================== làm bài & AI chấm =====================

    @Override
    @Transactional
    public PracticeAnswerResponse submit(UUID studentId, UUID practiceExamId, SubmitPracticeAnswerRequest request) {
        // Khóa dòng: answers là mảng JSONB, 2 lần nộp cùng lúc không được ghi đè nhau
        PracticeExam exam = practiceExamRepository.findWithLockByPracticeExamId(practiceExamId)
                .orElseThrow(() -> new AppException(ErrorCode.PRACTICE_EXAM_NOT_FOUND));

        // Kiểm tra giới hạn 3 lượt test miễn phí cho USER (Learner)
        User user = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != null && RoleName.USER.name().equalsIgnoreCase(user.getRole().getRoleName())) {
            long practiceAttempts = countPracticeAttempts(studentId);
            long examAttempts = examAttemptRepository.countByStudent_UserId(studentId);
            if (practiceAttempts + examAttempts >= 3) {
                throw new AppException(ErrorCode.PRACTICE_ATTEMPT_LIMIT);
            }
        }

        String content = request.getContent().trim();
        PracticeAnswerData answer = new PracticeAnswerData(UUID.randomUUID(), studentId, content, countWords(content),
                PracticeAnswerStatus.SUBMITTED, UtcTimeUtil.nowUtc(), null);

        List<PracticeAnswerData> answers = answersOf(exam);
        answers.add(answer);
        exam.setAnswers(JsonbMapper.write(answers));
        practiceExamRepository.save(exam);
        log.info("Student {} submitted practice answer {} for exam {}", studentId, answer.answerId(), practiceExamId);
        return toAnswerResponse(exam, answer);
    }

    @Override
    public PracticeEvaluationResponse evaluate(UUID studentId, UUID answerId) {
        // 1. Đọc bài làm (transaction ngắn, chỉ đọc), kiểm tra quyền sở hữu
        Located located = transactionTemplate.execute(status -> locateOwnedAnswer(studentId, answerId));
        if (located.answer().evaluation() != null) {
            return toEvaluationResponse(located.exam(), located.answer());
        }

        // 2. Thang điểm theo chứng chỉ mục tiêu của người học, rồi gọi AI ngoài transaction (10 - 60 giây)
        ScoringScale scale = scoringScaleResolver.resolve(studentId, located.exam().getExamType());
        AiEvaluationResult result = aiEvaluationClient.evaluate(located.exam().getExamType(),
                located.exam().getTitle() + "\n" + located.exam().getDescription(), located.answer().content(), scale);

        // 3. Ghi kết quả: khóa dòng, đọc lại; nếu request khác đã chấm xong trước thì giữ kết quả đó
        return transactionTemplate.execute(status -> {
            PracticeExam exam = practiceExamRepository.findWithLockByPracticeExamId(located.exam().getPracticeExamId())
                    .orElseThrow(() -> new AppException(ErrorCode.PRACTICE_EXAM_NOT_FOUND));
            List<PracticeAnswerData> answers = answersOf(exam);
            int index = indexOf(answers, answerId);
            PracticeAnswerData current = answers.get(index);
            if (current.evaluation() != null) {
                return toEvaluationResponse(exam, current);
            }
            PracticeAnswerData evaluated = current.withEvaluation(new PracticeEvaluationData(UUID.randomUUID(),
                    result.bandScore(), result.taskAchievement(), result.coherenceCohesion(), result.lexicalResource(),
                    result.grammaticalAccuracy(), result.feedback(), result.suggestions(), result.model(),
                    result.rawResponse(), UtcTimeUtil.nowUtc(),
                    scale.certificateName(), scale.minScore(), scale.maxScore(), result.cefrLevel()));
            answers.set(index, evaluated);
            exam.setAnswers(JsonbMapper.write(answers));
            practiceExamRepository.save(exam);
            updateCurrentLevel(studentId, result);

            // Hub thông báo (TASK-D5-02): AI chấm xong -> báo học viên (đẩy realtime sau commit)
            notificationService.notify(studentId, NotificationType.exam_result, "AI đã chấm xong bài luyện tập",
                    "Bài \"" + exam.getTitle() + "\" đạt " + result.bandScore().toPlainString() + "/"
                            + scale.maxScore().toPlainString() + " (" + scale.certificateName() + ")"
                            + (result.cefrLevel() != null ? " — level " + result.cefrLevel() : "") + ".");
            log.info("Practice answer {} evaluated: band {}", answerId, result.bandScore());
            return toEvaluationResponse(exam, evaluated);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeEvaluationResponse getEvaluation(UUID requesterId, boolean isAdmin, UUID evaluationId) {
        String filter = JsonbMapper.write(List.of(Map.of("evaluation", Map.of("evaluationId", evaluationId.toString()))));
        for (PracticeExam exam : practiceExamRepository.findByAnswersContaining(filter)) {
            Optional<PracticeAnswerData> answer = answersOf(exam).stream()
                    .filter(a -> a.evaluation() != null && evaluationId.equals(a.evaluation().evaluationId()))
                    .findFirst();
            // Kết quả của người khác -> coi như không tồn tại
            if (answer.isPresent() && (isAdmin || answer.get().studentId().equals(requesterId))) {
                return toEvaluationResponse(exam, answer.get());
            }
        }
        throw new AppException(ErrorCode.PRACTICE_EVALUATION_NOT_FOUND);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticeAnswerResponse> getMyAnswers(UUID studentId) {
        String filter = JsonbMapper.write(List.of(Map.of("studentId", studentId.toString())));
        return practiceExamRepository.findByAnswersContaining(filter).stream()
                .flatMap(exam -> answersOf(exam).stream()
                        .filter(a -> studentId.equals(a.studentId()))
                        .map(a -> Map.entry(exam, a)))
                .sorted(Comparator.comparing((Map.Entry<PracticeExam, PracticeAnswerData> e) -> e.getValue().submittedAt())
                        .reversed())
                .map(e -> toAnswerResponse(e.getKey(), e.getValue()))
                .toList();
    }

    // ===================== dùng cho Learning Path =====================

    @Override
    @Transactional(readOnly = true)
    public String getTitle(UUID practiceExamId) {
        return findExam(practiceExamId).getTitle();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasReachedBand(UUID studentId, UUID practiceExamId, BigDecimal targetBand) {
        return practiceExamRepository.findById(practiceExamId)
                .map(exam -> answersOf(exam).stream()
                        .filter(a -> studentId.equals(a.studentId()) && a.evaluation() != null)
                        .anyMatch(a -> targetBand == null || a.evaluation().bandScore().compareTo(targetBand) >= 0))
                .orElse(false);
    }

    // ===================== helpers =====================

    private long countPracticeAttempts(UUID studentId) {
        String filter = JsonbMapper.write(List.of(Map.of("studentId", studentId.toString())));
        return practiceExamRepository.findByAnswersContaining(filter).stream()
                .flatMap(exam -> answersOf(exam).stream())
                .filter(a -> studentId.equals(a.studentId()))
                .count();
    }

    /** Lưu trình độ gần nhất vào hồ sơ (tạo hồ sơ nếu Learner chưa có). */
    private void updateCurrentLevel(UUID studentId, AiEvaluationResult result) {
        StudentProfile profile = studentProfileRepository.findById(studentId)
                .orElseGet(() -> StudentProfile.builder().user(userRepository.getReferenceById(studentId)).build());
        profile.setCurrentScore(result.bandScore());
        if (result.cefrLevel() != null) {
            profile.setCurrentLevel(result.cefrLevel());
        }
        profile.setLastUpdated(LocalDateTime.now());
        studentProfileRepository.save(profile);
    }

    private record Located(PracticeExam exam, PracticeAnswerData answer) {
    }

    private Located locateOwnedAnswer(UUID studentId, UUID answerId) {
        String filter = JsonbMapper.write(List.of(Map.of("answerId", answerId.toString())));
        for (PracticeExam exam : practiceExamRepository.findByAnswersContaining(filter)) {
            List<PracticeAnswerData> answers = answersOf(exam);
            PracticeAnswerData answer = answers.get(indexOf(answers, answerId));
            // Bài của người khác -> coi như không tồn tại
            if (answer.studentId().equals(studentId)) {
                return new Located(exam, answer);
            }
        }
        throw new AppException(ErrorCode.PRACTICE_ANSWER_NOT_FOUND);
    }

    private int indexOf(List<PracticeAnswerData> answers, UUID answerId) {
        for (int i = 0; i < answers.size(); i++) {
            if (answerId.equals(answers.get(i).answerId())) {
                return i;
            }
        }
        throw new AppException(ErrorCode.PRACTICE_ANSWER_NOT_FOUND);
    }

    private PracticeExam findExam(UUID practiceExamId) {
        return practiceExamRepository.findById(practiceExamId)
                .orElseThrow(() -> new AppException(ErrorCode.PRACTICE_EXAM_NOT_FOUND));
    }

    private void validateCourse(UUID courseId) {
        if (courseId != null) {
            // Khóa học không tồn tại -> COURSE_NOT_FOUND (Dev 2)
            courseService.getCourseById(courseId);
        }
    }

    private List<PracticeAnswerData> answersOf(PracticeExam exam) {
        return JsonbMapper.readList(exam.getAnswers(), ANSWERS);
    }

    static int countWords(String content) {
        String trimmed = content.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Certificate resolveCertificate(UUID certificateId) {
        if (certificateId == null) {
            return null;
        }
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private PracticeExamResponse toExamResponse(PracticeExam exam) {
        return PracticeExamResponse.builder()
                .practiceExamId(exam.getPracticeExamId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .examType(exam.getExamType().name())
                .bankName(exam.getBankName())
                .courseId(exam.getCourseId())
                .certificateId(exam.getCertificate() != null ? exam.getCertificate().getCertificateId() : null)
                .certificateName(exam.getCertificate() != null ? exam.getCertificate().getName() : null)
                .createdByUserId(exam.getCreatedByUserId())
                .createdAt(UtcTimeUtil.fromServerTime(exam.getCreatedAt()))
                .lastUpdated(UtcTimeUtil.fromServerTime(exam.getLastUpdated()))
                .build();
    }

    private PracticeAnswerResponse toAnswerResponse(PracticeExam exam, PracticeAnswerData answer) {
        return PracticeAnswerResponse.builder()
                .answerId(answer.answerId())
                .practiceExamId(exam.getPracticeExamId())
                .examTitle(exam.getTitle())
                .examType(exam.getExamType().name())
                .status(answer.status().name())
                .wordCount(answer.wordCount())
                .content(answer.content())
                .submittedAt(UtcTimeUtil.toInstant(answer.submittedAt()))
                .evaluationId(answer.evaluation() != null ? answer.evaluation().evaluationId() : null)
                .bandScore(answer.evaluation() != null ? answer.evaluation().bandScore() : null)
                .build();
    }

    private PracticeEvaluationResponse toEvaluationResponse(PracticeExam exam, PracticeAnswerData answer) {
        PracticeEvaluationData e = answer.evaluation();
        return PracticeEvaluationResponse.builder()
                .evaluationId(e.evaluationId())
                .answerId(answer.answerId())
                .practiceExamId(exam.getPracticeExamId())
                .examTitle(exam.getTitle())
                .examType(exam.getExamType().name())
                .bandScore(e.bandScore())
                .certificateName(e.certificateName())
                .minScore(e.minScore())
                .maxScore(e.maxScore())
                .cefrLevel(e.cefrLevel())
                .taskAchievement(e.taskAchievement())
                .coherenceCohesion(e.coherenceCohesion())
                .lexicalResource(e.lexicalResource())
                .grammaticalAccuracy(e.grammaticalAccuracy())
                .feedback(e.feedback())
                .suggestions(e.suggestions())
                .model(e.model())
                .evaluatedAt(UtcTimeUtil.toInstant(e.evaluatedAt()))
                .build();
    }
}
