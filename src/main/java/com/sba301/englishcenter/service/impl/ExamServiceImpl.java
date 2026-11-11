package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.CreateExamRequest;
import com.sba301.englishcenter.dto.request.UpdateExamRequest;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.ExamRulesResponse;
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.QuestionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.CourseExamContentService;
import com.sba301.englishcenter.service.ExamAccessPolicy;
import com.sba301.englishcenter.service.ExamService;
import com.sba301.englishcenter.specification.ExamSpecification;
import com.sba301.englishcenter.util.QuestionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamServiceImpl implements ExamService {

    private final ExamRepository examRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final CertificateRepository certificateRepository;
    private final CertificatePartRepository certificatePartRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final ExamAccessPolicy accessPolicy;
    /** V30: biết đề có câu tự luận và học viên đã mua gói mentor hay chưa. */
    private final CourseExamContentService courseExamContentService;
    private final ExamMapper examMapper;

    // ============================================
    // LIST & DETAIL
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> getPublishedExams(Pageable pageable, String keyword, ExamType examType,
                                                               ExamScope examScope, UUID certificateId,
                                                               UUID courseId, String level,
                                                               DifficultyLevel difficulty, Boolean isFree) {
        Optional<UserPrincipal> principal = SecurityUtils.getCurrentUserPrincipal();
        boolean admin = principal.map(p -> RoleName.ADMIN.name().equals(p.getRoleName())).orElse(false);
        Specification<Exam> spec = ExamSpecification.filterExams(keyword, examType,
                ExamStatus.PUBLISHED, examScope, certificateId, courseId, level, difficulty, isFree);
        if (!admin) {
            // Không trả hết đề đã công khai: đề khóa học chỉ hiện với học viên đã mua khóa đó
            spec = spec.and(ExamSpecification.visibleToStudent(principal.map(UserPrincipal::getUserId).orElse(null)));
        }
        Page<Exam> exams = examRepository.findAll(spec, pageable);
        PageResponse<ExamSummaryResponse> result = toPage(exams, false);
        UUID userId = principal.map(UserPrincipal::getUserId).orElse(null);
        if (!admin && userId != null) {
            markLocks(exams.getContent(), result.getData(), userId);
        }
        return result;
    }

    /** Gắn trạng thái khóa + lý do vào từng đề của trang (mỗi khóa học chỉ truy vấn một lần). */
    private void markLocks(List<Exam> exams, List<ExamSummaryResponse> summaries, UUID userId) {
        ExamAccessPolicy.Session session = accessPolicy.session(userId, LocalDateTime.now());
        for (int i = 0; i < exams.size(); i++) {
            ExamSummaryResponse summary = summaries.get(i);
            Optional<ExamAccessPolicy.Denial> denial = session.evaluate(exams.get(i));
            summary.setAccessible(denial.isEmpty());
            denial.ifPresent(d -> {
                summary.setLockCode(d.code().getCode());
                summary.setLockReason(d.message());
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamSummaryResponse> getExamsForAdmin(Pageable pageable, String keyword, ExamType examType,
                                                              ExamStatus status, ExamScope examScope,
                                                              UUID certificateId, UUID courseId, String level,
                                                              DifficultyLevel difficulty, Boolean isFree) {
        Page<Exam> page = examRepository.findAll(ExamSpecification.filterExams(keyword, examType, status,
                examScope, certificateId, courseId, level, difficulty, isFree), pageable);
        return toPage(page, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamDetailResponse getExamDetail(UUID examId) {
        Exam exam = findExam(examId);
        Optional<UserPrincipal> principal = SecurityUtils.getCurrentUserPrincipal();
        boolean admin = principal.map(p -> RoleName.ADMIN.name().equals(p.getRoleName())).orElse(false);

        if (admin) {
            return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
        }
        // Đề chưa công khai coi như không tồn tại với người ngoài admin
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new AppException(ErrorCode.EXAM_NOT_FOUND);
        }
        UUID userId = principal.map(UserPrincipal::getUserId).orElse(null);
        Optional<ExamAccessPolicy.Denial> denied = accessPolicy.evaluate(userId, exam, LocalDateTime.now());
        Boolean accessible = userId == null ? null : denied.isEmpty();
        String message = denied.map(ExamAccessPolicy.Denial::message).orElse(null);
        // Nội dung câu hỏi chỉ được giao trong lượt thi (có đồng hồ) - xem POST /exams/{id}/attempts
        ExamDetailResponse detail = buildDetail(exam, ExamMapper.View.OUTLINE, accessible, message);
        denied.ifPresent(d -> detail.setAccessCode(d.code().getCode()));
        // V30: đề có phần tự luận + học viên chưa mua gói mentor → vẫn làm được,
        // nhưng phần tự luận sẽ lưu nháp chờ nâng cấp gói. FE dùng để gợi ý trước.
        detail.setRequiresMentorGrading(courseExamContentService.requiresMentorGrading(exam.getExamId()));
        if (userId != null) {
            detail.setHasMentorPlan(courseExamContentService.hasMentorPlan(userId));
        }
        return detail;
    }

    // ============================================
    // ADMIN CRUD
    // ============================================

    @Override
    @Transactional
    public ExamDetailResponse createExam(CreateExamRequest request) {
        UUID adminId = currentUserId();
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String code = blankToNull(request.getExamCode());
        if (code != null && examRepository.existsByExamCode(code)) {
            throw new AppException(ErrorCode.EXAM_CODE_EXISTED);
        }

        Exam exam = Exam.builder()
                .examScope(request.getExamScope())
                .createdByUser(admin)
                .examCode(code)
                .examTitle(request.getExamTitle().trim())
                .durationMinutes(request.getDurationMinutes())
                .build();
        applyScopeTarget(exam, request.getExamScope(), request.getCertificateId(), request.getCourseId());
        applyCommonFields(exam, request.getExamType(), request.getDescription(), request.getPassingScore(),
                request.getMaxAttempts(), request.getShowAnswers(), request.getShowScore(), request.getIsFree(),
                request.getPrice(), request.getDifficultyLevel(), request.getRecommendedLevel(),
                request.getAvailableFrom(), request.getAvailableUntil());
        validateWindow(exam);
        validateTypeAndDescription(exam);
        validateDuration(exam);

        exam = examRepository.save(exam);
        log.info("Admin {} tạo đề thi {} ({})", adminId, exam.getExamId(), exam.getExamScope());
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public ExamDetailResponse updateExam(UUID examId, UpdateExamRequest request) {
        Exam exam = findExam(examId);

        if (request.getExamCode() != null) {
            String code = blankToNull(request.getExamCode());
            if (code != null && examRepository.existsByExamCodeAndExamIdNot(code, examId)) {
                throw new AppException(ErrorCode.EXAM_CODE_EXISTED);
            }
            exam.setExamCode(code);
        }
        if (request.getExamTitle() != null && !request.getExamTitle().isBlank()) {
            exam.setExamTitle(request.getExamTitle().trim());
        }
        if (request.getDurationMinutes() != null) {
            exam.setDurationMinutes(request.getDurationMinutes());
        }
        boolean clearCertificate = Boolean.TRUE.equals(request.getClearCertificate());
        if (request.getCertificateId() != null || request.getCourseId() != null || clearCertificate) {
            UUID oldCertificateId = exam.getCertificate() != null ? exam.getCertificate().getCertificateId() : null;
            UUID oldCourseId = exam.getCourse() != null ? exam.getCourse().getCourseId() : null;
            UUID newCertificateId = clearCertificate ? null
                    : request.getCertificateId() != null ? request.getCertificateId() : oldCertificateId;
            UUID newCourseId = request.getCourseId() != null ? request.getCourseId() : oldCourseId;
            // Gửi lại đúng chứng chỉ / khóa học cũ thì không coi là đổi cấu trúc
            if (!Objects.equals(oldCertificateId, newCertificateId) || !Objects.equals(oldCourseId, newCourseId)) {
                assertStructureEditable(exam);
                applyScopeTarget(exam, exam.getExamScope(), newCertificateId, newCourseId);
                UUID appliedCertificateId = exam.getCertificate() != null
                        ? exam.getCertificate().getCertificateId() : null;
                if (!Objects.equals(oldCertificateId, appliedCertificateId)) {
                    remapCertificateParts(exam, oldCertificateId);
                }
            }
        }
        applyCommonFields(exam, request.getExamType(), request.getDescription(), request.getPassingScore(),
                request.getMaxAttempts(), request.getShowAnswers(), request.getShowScore(), request.getIsFree(),
                request.getPrice(), request.getDifficultyLevel(), request.getRecommendedLevel(),
                request.getAvailableFrom(), request.getAvailableUntil());
        validateWindow(exam);
        // Đề cũ chưa có mô tả vẫn sửa được trường khác; chỉ kiểm tra mô tả khi admin gửi mô tả mới
        validateType(exam);
        if (request.getDescription() != null) {
            validateDescription(exam);
        }
        // Đổi thời lượng hoặc loại đề thì thời lượng phải hợp với loại đề
        if (request.getDurationMinutes() != null || request.getExamType() != null) {
            validateDuration(exam);
        }

        exam = examRepository.save(exam);
        log.info("Cập nhật đề thi {}", examId);
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public void deleteExam(UUID examId) {
        Exam exam = findExam(examId);
        // exam_attempts có FK ON DELETE CASCADE: xóa đề sẽ xóa luôn lịch sử thi của học viên -> chặn
        if (attemptRepository.existsByExam_ExamId(examId)) {
            throw new AppException(ErrorCode.EXAM_HAS_ATTEMPTS,
                    "Đề đã có lượt thi nên không xóa được (giữ lịch sử làm bài). Hãy chọn Lưu trữ để ẩn đề");
        }
        examRepository.delete(exam);
        log.info("Đã xóa đề thi {}", examId);
    }

    @Override
    @Transactional
    public ExamDetailResponse publishExam(UUID examId) {
        Exam exam = findExam(examId);
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(examId);

        if (examQuestions.isEmpty()) {
            throw new AppException(ErrorCode.EXAM_INVALID_FOR_PUBLISH, "Đề thi chưa có câu hỏi nào");
        }
        validateTypeAndDescription(exam);
        validateDuration(exam);
        validateDurationForContent(exam, examQuestions.size());
        for (ExamQuestion eq : examQuestions) {
            Question q = eq.getQuestion();
            try {
                QuestionValidator.validate(q.getQuestionType(), q.getOptions().stream()
                        .map(o -> new QuestionValidator.OptionData(o.getOptionText(),
                                Boolean.TRUE.equals(o.getIsCorrect())))
                        .toList());
            } catch (AppException ex) {
                throw new AppException(ErrorCode.EXAM_INVALID_FOR_PUBLISH,
                        "Câu hỏi " + q.getQuestionId() + " không hợp lệ: " + ex.getMessage());
            }
        }

        exam.setStatus(ExamStatus.PUBLISHED);
        exam.setPublishedDate(LocalDateTime.now());
        exam = examRepository.save(exam);
        log.info("Đã publish đề thi {}", examId);
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public ExamDetailResponse unpublishExam(UUID examId) {
        Exam exam = findExam(examId);
        // Lượt thi đang làm vẫn được làm tiếp đến khi hết giờ; chỉ chặn bắt đầu lượt mới
        exam.setStatus(ExamStatus.DRAFT);
        exam = examRepository.save(exam);
        log.info("Đã unpublish đề thi {}", examId);
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public ExamDetailResponse archiveExam(UUID examId) {
        Exam exam = findExam(examId);
        // Học viên chỉ thấy / bắt đầu được đề published nên đề lưu trữ tự ẩn;
        // lịch sử và kết quả các lượt đã làm vẫn giữ nguyên, lượt đang làm vẫn được nộp đến khi hết giờ
        exam.setStatus(ExamStatus.ARCHIVED);
        exam = examRepository.save(exam);
        log.info("Đã lưu trữ đề thi {}", examId);
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public ExamDetailResponse restoreExam(UUID examId) {
        Exam exam = findExam(examId);
        if (exam.getStatus() != ExamStatus.ARCHIVED) {
            throw new AppException(ErrorCode.EXAM_NOT_ARCHIVED);
        }
        exam.setStatus(ExamStatus.DRAFT);
        exam = examRepository.save(exam);
        log.info("Đã khôi phục đề thi {} về nháp", examId);
        return buildDetail(exam, ExamMapper.View.ADMIN, null, null);
    }

    @Override
    @Transactional
    public ExamDetailResponse cloneExam(UUID examId) {
        Exam source = findExam(examId);
        User admin = userRepository.findById(currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Exam copy = examRepository.save(Exam.builder()
                .examScope(source.getExamScope())
                .createdByUser(admin)
                .certificate(source.getCertificate())
                .course(source.getCourse())
                .examCode(copyCode(source.getExamCode()))
                .examType(source.getExamType())
                .examTitle(source.getExamTitle() + " (bản sao)")
                .description(source.getDescription())
                .durationMinutes(source.getDurationMinutes())
                .passingScore(source.getPassingScore())
                .maxAttempts(source.getMaxAttempts())
                .showAnswers(source.getShowAnswers())
                .showScore(source.getShowScore())
                .isFree(source.getIsFree())
                .price(source.getPrice())
                .difficultyLevel(source.getDifficultyLevel())
                .recommendedLevel(source.getRecommendedLevel())
                .availableFrom(source.getAvailableFrom())
                .availableUntil(source.getAvailableUntil())
                .status(ExamStatus.DRAFT)
                .build());

        Map<UUID, ExamSection> sections = new HashMap<>();
        for (ExamSection s : sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(examId)) {
            sections.put(s.getSectionId(), sectionRepository.save(ExamSection.builder()
                    .exam(copy)
                    .certificatePart(s.getCertificatePart())
                    .sectionType(s.getSectionType())
                    .title(s.getTitle())
                    .instructions(s.getInstructions())
                    .passageText(s.getPassageText())
                    .audioUrl(s.getAudioUrl())
                    .sectionOrder(s.getSectionOrder())
                    .durationMinutes(s.getDurationMinutes())
                    .build()));
        }

        // Câu hỏi được chép mới (kể cả đáp án) để sửa bản sao không ảnh hưởng đề gốc / lịch sử thi cũ
        List<ExamQuestion> questions = examQuestionRepository.findDetailedByExamId(examId);
        for (ExamQuestion eq : questions) {
            Question q = eq.getQuestion();
            Question newQuestion = Question.builder()
                    .createdByUser(admin)
                    .certificate(q.getCertificate())
                    .certificatePart(q.getCertificatePart())
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .difficultyLevel(q.getDifficultyLevel())
                    .topic(q.getTopic())
                    .points(q.getPoints())
                    .explanation(q.getExplanation())
                    .audioUrl(q.getAudioUrl())
                    .imageUrl(q.getImageUrl())
                    .build();
            for (QuestionOption o : q.getOptions()) {
                newQuestion.getOptions().add(QuestionOption.builder()
                        .question(newQuestion)
                        .optionText(o.getOptionText())
                        .isCorrect(o.getIsCorrect())
                        .optionOrder(o.getOptionOrder())
                        .explanation(o.getExplanation())
                        .build());
            }
            examQuestionRepository.save(ExamQuestion.builder()
                    .exam(copy)
                    .section(eq.getSection() != null ? sections.get(eq.getSection().getSectionId()) : null)
                    .question(questionRepository.save(newQuestion))
                    .questionOrder(eq.getQuestionOrder())
                    .timeLimitSeconds(eq.getTimeLimitSeconds())
                    .build());
        }

        copy.setTotalQuestions(questions.size());
        copy.setTotalParts(sections.size());
        copy = examRepository.save(copy);
        log.info("Admin {} nhân bản đề {} thành {}", admin.getUserId(), examId, copy.getExamId());
        return buildDetail(copy, ExamMapper.View.ADMIN, null, null);
    }

    /** Mã đề của bản sao: MA-COPY, MA-COPY-2… (mã đề là duy nhất) */
    private String copyCode(String code) {
        if (code == null) {
            return null;
        }
        String base = code + "-COPY";
        String candidate = base;
        for (int i = 2; examRepository.existsByExamCode(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }

    // ============================================
    // HELPERS (package-private cho ExamStructureServiceImpl dùng lại)
    // ============================================

    /** Chỉ sửa cấu trúc (section, câu hỏi, đáp án, phạm vi) khi đề là draft và chưa có lượt thi. */
    static void assertStructureEditable(Exam exam, ExamAttemptRepository attemptRepository) {
        if (exam.getStatus() != ExamStatus.DRAFT) {
            throw new AppException(ErrorCode.EXAM_NOT_EDITABLE);
        }
        if (attemptRepository.existsByExam_ExamId(exam.getExamId())) {
            throw new AppException(ErrorCode.EXAM_HAS_ATTEMPTS);
        }
    }

    private void assertStructureEditable(Exam exam) {
        assertStructureEditable(exam, attemptRepository);
    }

    private ExamDetailResponse buildDetail(Exam exam, ExamMapper.View view, Boolean accessible, String message) {
        ExamDetailResponse detail = examMapper.toDetail(exam,
                sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(exam.getExamId()),
                exam.getExamId() == null ? List.of() : examQuestionRepository.findDetailedByExamId(exam.getExamId()),
                view, accessible, message);
        if (view == ExamMapper.View.ADMIN) {
            List<String> warnings = accessPolicy.courseSetupWarnings(exam);
            detail.setWarnings(warnings.isEmpty() ? null : warnings);
        }
        return detail;
    }

    /**
     * Đổi chứng chỉ của đề (VD lỡ gắn IELTS cho đề TOEIC): section và câu hỏi đang trỏ tới phần thi
     * của chứng chỉ cũ → chuyển sang phần thi cùng kỹ năng của chứng chỉ mới, không có thì bỏ liên kết
     * (FE tự lấy thang theo kỹ năng của section).
     */
    private void remapCertificateParts(Exam exam, UUID oldCertificateId) {
        Certificate certificate = exam.getCertificate();
        List<CertificatePart> parts = certificate == null ? List.of()
                : certificatePartRepository.findByCertificate_CertificateIdOrderByPartOrderAsc(
                certificate.getCertificateId());

        List<ExamSection> sections =
                sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(exam.getExamId());
        Map<UUID, CertificatePart> partBySection = new HashMap<>();
        for (ExamSection section : sections) {
            CertificatePart part = matchPartBySkill(parts, section.getSectionType());
            section.setCertificatePart(part);
            partBySection.put(section.getSectionId(), part);
        }
        sectionRepository.saveAll(sections);

        // Câu hỏi tạo kèm đề (đang gắn chứng chỉ cũ) → gắn theo chứng chỉ mới để lọc ngân hàng câu hỏi cho đúng;
        // câu lấy từ ngân hàng của chứng chỉ khác thì giữ nguyên
        for (ExamQuestion eq : examQuestionRepository.findDetailedByExamId(exam.getExamId())) {
            Question question = eq.getQuestion();
            UUID questionCertificateId = question.getCertificate() != null
                    ? question.getCertificate().getCertificateId() : null;
            if (!Objects.equals(questionCertificateId, oldCertificateId)) {
                continue;
            }
            question.setCertificate(certificate);
            question.setCertificatePart(eq.getSection() != null
                    ? partBySection.get(eq.getSection().getSectionId()) : null);
        }
        log.info("Đề {} đổi chứng chỉ → gắn lại phần thi cho {} section", exam.getExamId(), sections.size());
    }

    /** Phần thi của chứng chỉ có tên chứa kỹ năng của section (Listening / Reading / Writing / Speaking). */
    private static CertificatePart matchPartBySkill(List<CertificatePart> parts, String sectionType) {
        String type = sectionType == null ? "" : sectionType.toUpperCase(Locale.ROOT);
        String key = type.contains("LISTEN") ? "LISTEN"
                : type.contains("READ") ? "READ"
                : type.contains("WRIT") ? "WRIT"
                : type.contains("SPEAK") ? "SPEAK"
                : null;
        if (key == null) {
            return null;
        }
        return parts.stream()
                .filter(p -> p.getPartName() != null && p.getPartName().toUpperCase(Locale.ROOT).contains(key))
                .findFirst()
                .orElse(null);
    }

    private void applyScopeTarget(Exam exam, ExamScope scope, UUID certificateId, UUID courseId) {
        if (scope == ExamScope.TRIAL) {
            if (certificateId == null) {
                throw new AppException(ErrorCode.INVALID_EXAM_SCOPE, "Đề thi thử (trial) bắt buộc có certificateId");
            }
            Certificate certificate = certificateRepository.findById(certificateId)
                    .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
            exam.setCertificate(certificate);
            exam.setCourse(null);
        } else if (scope == ExamScope.COURSE) {
            if (courseId == null) {
                throw new AppException(ErrorCode.INVALID_EXAM_SCOPE, "Đề thi khóa học (course) bắt buộc có courseId");
            }
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
            exam.setCourse(course);
            // Đề khóa học có thể gắn thêm chứng chỉ của khóa để lọc
            exam.setCertificate(certificateId != null
                    ? certificateRepository.findById(certificateId)
                    .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND))
                    : course.getCertificate());
        } else if (scope == ExamScope.PRACTICE) {
            // Đề luyện tập AI: gắn chứng chỉ không bắt buộc (không gắn = dùng cho mọi mục tiêu), không thuộc khóa học
            exam.setCertificate(certificateId != null
                    ? certificateRepository.findById(certificateId)
                    .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND))
                    : null);
            exam.setCourse(null);
        } else {
            throw new AppException(ErrorCode.INVALID_EXAM_SCOPE);
        }
    }

    /** Mô tả tối thiểu của đề thi thử / đề khóa học: phải nói rõ đề gồm gì, không ghi vài ba chữ. */
    static final int MIN_DESCRIPTION_LENGTH = 30;

    private void validateTypeAndDescription(Exam exam) {
        validateType(exam);
        validateDescription(exam);
    }

    /**
     * Đề thi thử / kiểm tra đầu vào (trial) đã bỏ - thi thử đầy đủ nằm ở Luyện tập AI (practice).
     * Đề ở đây là đề đầu ra của khóa học: Quiz / giữa kỳ / cuối kỳ, bắt buộc thuộc một khóa (course).
     */
    private void validateType(Exam exam) {
        ExamType type = exam.getExamType();
        if (exam.getExamScope() == ExamScope.TRIAL) {
            throw new AppException(ErrorCode.EXAM_TYPE_SCOPE_MISMATCH,
                    "Đề kiểm tra đầu vào / thi thử không còn dùng: đề phải thuộc một khóa học (Quiz / Giữa kỳ / "
                            + "Cuối kỳ). Thi thử đầy đủ tạo ở Luyện tập AI");
        }
        if (exam.getExamScope() == ExamScope.COURSE && (type == null || type == ExamType.PLACEMENT_TEST)) {
            throw new AppException(ErrorCode.EXAM_TYPE_SCOPE_MISMATCH,
                    "Đề của khóa học phải là Quiz, Giữa kỳ hoặc Cuối kỳ");
        }
    }

    /** Đề ngắn nhất (phút) - dưới mức này học viên không kịp đọc đề. */
    static final int MIN_DURATION_MINUTES = 15;

    /** Quiz là bài kiểm tra ngắn sau mỗi unit - tối đa 60 phút (và không quá thời gian thi thật của chứng chỉ). */
    static final int MAX_QUIZ_MINUTES = 60;

    /** Khi công khai: tối thiểu 30 giây cho mỗi câu hỏi. */
    static final int MIN_SECONDS_PER_QUESTION = 30;

    /** Chứng chỉ chưa có preset (CEFR, thang 100, tùy chỉnh). */
    static final int DEFAULT_FULL_TEST_MINUTES = 180;

    /**
     * Thời gian thi thật của từng chứng chỉ (phút), theo preset trong certificates.description.scoring.
     * Giữa kỳ / Cuối kỳ không dài hơn đề thi thật. FE lấy qua GET /api/v1/admin/exams/rules, không chép lại.
     */
    static final Map<String, Integer> FULL_TEST_MINUTES = Map.of(
            "IELTS", 165,
            "TOEIC_LR", 120,
            "TOEIC_SW", 80,
            "TOEFL", 120,
            "TOEFL_OLD", 120,
            "TOEFL_NEW", 90,
            "VSTEP", 172,
            "APTIS", 180);

    static int fullTestMinutes(String preset) {
        return preset == null ? DEFAULT_FULL_TEST_MINUTES
                : FULL_TEST_MINUTES.getOrDefault(preset, DEFAULT_FULL_TEST_MINUTES);
    }

    /** Thời lượng tối đa của đề theo loại đề + chứng chỉ của đề. */
    static int maxDurationMinutes(ExamType type, String preset) {
        int fullTest = fullTestMinutes(preset);
        return type == ExamType.QUIZ ? Math.min(MAX_QUIZ_MINUTES, fullTest) : fullTest;
    }

    @Override
    @Transactional(readOnly = true)
    public ExamRulesResponse getExamRules(UUID certificateId, ExamType examType) {
        Certificate certificate = certificateId == null ? null : certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
        String preset = scoringPreset(certificate);
        return ExamRulesResponse.builder()
                .minDurationMinutes(MIN_DURATION_MINUTES)
                .maxDurationMinutes(maxDurationMinutes(examType, preset))
                .fullTestMinutes(fullTestMinutes(preset))
                .minDescriptionLength(MIN_DESCRIPTION_LENGTH)
                .build();
    }

    private static String scoringPreset(Exam exam) {
        return scoringPreset(exam.getCertificate());
    }

    private static String scoringPreset(Certificate certificate) {
        if (certificate == null) {
            return null;
        }
        JsonNode root = JsonUtil.parse(certificate.getDescription());
        String preset = root == null ? null : root.path("scoring").path("preset").asText(null);
        return preset == null || preset.isBlank() ? null : preset.trim().toUpperCase(Locale.ROOT);
    }

    private void validateDuration(Exam exam) {
        if (exam.getExamScope() == ExamScope.PRACTICE || exam.getExamType() == null) {
            return;
        }
        int max = maxDurationMinutes(exam.getExamType(), scoringPreset(exam));
        Integer minutes = exam.getDurationMinutes();
        if (minutes == null || minutes < MIN_DURATION_MINUTES || minutes > max) {
            String certificate = exam.getCertificate() != null ? " " + exam.getCertificate().getCertificateName() : "";
            throw new AppException(ErrorCode.EXAM_DURATION_OUT_OF_RANGE,
                    "Thời lượng đề " + EXAM_TYPE_LABEL.get(exam.getExamType()) + certificate + " phải từ "
                            + MIN_DURATION_MINUTES + " đến " + max + " phút");
        }
    }

    /** Khi công khai: đủ thời gian cho số câu, và tổng thời lượng các phần thi không vượt thời lượng cả đề. */
    private void validateDurationForContent(Exam exam, int questionCount) {
        if (exam.getExamScope() == ExamScope.PRACTICE || exam.getDurationMinutes() == null) {
            return;
        }
        int neededMinutes = (int) Math.ceil(questionCount * MIN_SECONDS_PER_QUESTION / 60.0);
        if (exam.getDurationMinutes() < neededMinutes) {
            throw new AppException(ErrorCode.EXAM_DURATION_TOO_SHORT, "Đề có " + questionCount
                    + " câu, cần ít nhất " + neededMinutes + " phút (tối thiểu " + MIN_SECONDS_PER_QUESTION
                    + " giây mỗi câu)");
        }
        int sectionMinutes = sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(exam.getExamId())
                .stream().map(ExamSection::getDurationMinutes).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (sectionMinutes > exam.getDurationMinutes()) {
            throw new AppException(ErrorCode.EXAM_DURATION_TOO_SHORT, "Tổng thời lượng các phần thi ("
                    + sectionMinutes + " phút) vượt thời lượng cả đề (" + exam.getDurationMinutes() + " phút)");
        }
    }

    private static final Map<ExamType, String> EXAM_TYPE_LABEL = Map.of(
            ExamType.QUIZ, "Quiz",
            ExamType.MIDTERM, "Giữa kỳ",
            ExamType.FINAL, "Cuối kỳ",
            ExamType.PLACEMENT_TEST, "Kiểm tra đầu vào");

    private void validateDescription(Exam exam) {
        if (exam.getExamScope() == ExamScope.PRACTICE) {
            return;
        }
        String description = exam.getDescription() == null ? "" : exam.getDescription().trim();
        if (description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new AppException(ErrorCode.EXAM_DESCRIPTION_TOO_SHORT);
        }
    }

    private void applyCommonFields(Exam exam, ExamType examType, String description,
                                   BigDecimal passingScore, Integer maxAttempts, Boolean showAnswers,
                                   Boolean showScore, Boolean isFree, BigDecimal price,
                                   DifficultyLevel difficulty, String recommendedLevel,
                                   LocalDateTime availableFrom, LocalDateTime availableUntil) {
        if (examType != null) exam.setExamType(examType);
        if (description != null) exam.setDescription(description);
        if (passingScore != null) exam.setPassingScore(passingScore);
        if (maxAttempts != null) exam.setMaxAttempts(maxAttempts);
        if (showAnswers != null) exam.setShowAnswers(showAnswers);
        if (showScore != null) exam.setShowScore(showScore);
        if (isFree != null) exam.setIsFree(isFree);
        if (price != null) exam.setPrice(price);
        if (difficulty != null) exam.setDifficultyLevel(difficulty);
        if (recommendedLevel != null) exam.setRecommendedLevel(blankToNull(recommendedLevel));
        if (availableFrom != null) exam.setAvailableFrom(availableFrom);
        if (availableUntil != null) exam.setAvailableUntil(availableUntil);
    }

    private void validateWindow(Exam exam) {
        if (exam.getAvailableFrom() != null && exam.getAvailableUntil() != null
                && !exam.getAvailableUntil().isAfter(exam.getAvailableFrom())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "availableUntil phải sau availableFrom");
        }
    }

    private Exam findExam(UUID examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
    }

    private PageResponse<ExamSummaryResponse> toPage(Page<Exam> page, boolean admin) {
        return PageResponse.<ExamSummaryResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(e -> examMapper.toSummary(e, admin)).toList())
                .build();
    }

    private static UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
