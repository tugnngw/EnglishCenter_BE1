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
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.ExamAccessPolicy;
import com.sba301.englishcenter.service.ExamService;
import com.sba301.englishcenter.specification.ExamSpecification;
import com.sba301.englishcenter.util.QuestionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ExamAccessPolicy accessPolicy;
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
        Page<Exam> page = examRepository.findAll(ExamSpecification.filterExams(keyword, examType,
                ExamStatus.PUBLISHED, examScope, certificateId, courseId, level, difficulty, isFree), pageable);
        return toPage(page, false);
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
        Optional<ErrorCode> denied = accessPolicy.check(userId, exam, LocalDateTime.now());
        Boolean accessible = userId == null ? null : denied.isEmpty();
        String message = denied.map(ErrorCode::getMessage).orElse(null);
        // Nội dung câu hỏi chỉ được giao trong lượt thi (có đồng hồ) - xem POST /exams/{id}/attempts
        return buildDetail(exam, ExamMapper.View.OUTLINE, accessible, message);
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
        if (request.getCertificateId() != null || request.getCourseId() != null) {
            assertStructureEditable(exam);
            applyScopeTarget(exam, exam.getExamScope(),
                    request.getCertificateId() != null ? request.getCertificateId()
                            : exam.getCertificate() != null ? exam.getCertificate().getCertificateId() : null,
                    request.getCourseId() != null ? request.getCourseId()
                            : exam.getCourse() != null ? exam.getCourse().getCourseId() : null);
        }
        applyCommonFields(exam, request.getExamType(), request.getDescription(), request.getPassingScore(),
                request.getMaxAttempts(), request.getShowAnswers(), request.getShowScore(), request.getIsFree(),
                request.getPrice(), request.getDifficultyLevel(), request.getRecommendedLevel(),
                request.getAvailableFrom(), request.getAvailableUntil());
        validateWindow(exam);

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
                    "Đề thi đã có lượt thi, hãy dùng unpublish thay vì xóa");
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
        return examMapper.toDetail(exam,
                sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(exam.getExamId()),
                exam.getExamId() == null ? List.of() : examQuestionRepository.findDetailedByExamId(exam.getExamId()),
                view, accessible, message);
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
        } else {
            throw new AppException(ErrorCode.INVALID_EXAM_SCOPE);
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
