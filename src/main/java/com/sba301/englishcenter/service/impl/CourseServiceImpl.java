package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.dto.request.CreateCourseContentRequest;
import com.sba301.englishcenter.dto.request.CreateCourseRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseContentRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.CourseResponse;
import com.sba301.englishcenter.dto.response.CourseVocabularySetResponse;
import com.sba301.englishcenter.dto.response.VocabularyWordResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.CourseVocabularySet;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.LessonProgress;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.entity.VocabularySet;
import com.sba301.englishcenter.entity.VocabularySetWord;
import com.sba301.englishcenter.entity.VocabularyWord;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.CourseVocabularySetRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.repository.VocabularySetRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.CourseService;
import com.sba301.englishcenter.service.CourseExamContentService;
import com.sba301.englishcenter.service.HomeworkContentData;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.LessonProgressService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.specification.CourseSpecification;
import com.sba301.englishcenter.util.CourseRules;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.SessionEnrollmentChecker;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quản lý khóa học (catalog, CRUD, outline bài học).
 * Trước đây có {@code CourseContentServiceImpl} riêng — đã gộp về đây.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CertificateRepository certificateRepository;
    private final CertificatePartRepository certificatePartRepository;
    private final UserRepository userRepository;
    private final SessionVariableHelper sessionVariableHelper;
    // Chỉ phục vụ outline/CRUD nội dung bài học.
    // HomeworkSubmissionServiceImpl vẫn inject trực tiếp bean này cho flow nộp bài.
    private final CourseContentRepository courseContentRepository;
    private final CourseVocabularySetRepository courseVocabularySetRepository;
    private final VocabularySetRepository vocabularySetRepository;
    private final ExamRepository examRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final SessionEnrollmentChecker enrollmentChecker;
    /** Gửi thông báo cho mentor khi Admin giao bài tập cần chấm tay. */
    private final NotificationService notificationService;
    /** Quy tắc "đã mua gói mentor 1-1" — quyết định mở tab Bài tập + tên mentor hiển thị. */
    private final HomeworkGraderAssignmentService graderAssignmentService;
    /** Tiến độ xem video (V29) — tick hoàn thành + điều kiện mở bài thi cuối kỳ. */
    private final LessonProgressService lessonProgressService;
    /** Luật mở khoá nội dung "Bài thi cuối kỳ" (V29). */
    private final CourseExamContentService courseExamContentService;

    @Override
    @Transactional(readOnly = true)
    public boolean existsContent(UUID contentId) {
        return contentId != null && courseContentRepository.existsById(contentId);
    }

    // ========================================================================
    // CATALOG & CRUD
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getCourses(Pageable pageable, String keyword, UUID certificateId,
                                                   UUID certificatePartId, String level,
                                                   BigDecimal targetScore, String status) {
        Specification<Course> spec = CourseSpecification.filterCourses(
                keyword, certificateId, certificatePartId, level, targetScore, status);

        Page<Course> coursePage = courseRepository.findAll(spec, pageable);

        return PageResponse.<CourseResponse>builder()
                .currentPage(coursePage.getNumber())
                .pageSize(coursePage.getSize())
                .totalPages(coursePage.getTotalPages())
                .totalElements(coursePage.getTotalElements())
                .data(coursePage.getContent().stream()
                        .map(this::toCourseResponse)
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> getCoursesForLearner(Pageable pageable, String keyword, UUID certificatePartId,
                                                             String level, UUID goalCertificateId,
                                                             BigDecimal goalTargetScore, BigDecimal currentScore) {
        Specification<Course> spec = CourseSpecification.filterCourses(
                keyword, goalCertificateId, certificatePartId, level, null, CourseStatus.ACTIVE.getValue())
                .and(CourseSpecification.relevantToGoal(goalTargetScore, currentScore));

        Page<Course> coursePage = courseRepository.findAll(spec, pageable);

        return PageResponse.<CourseResponse>builder()
                .currentPage(coursePage.getNumber())
                .pageSize(coursePage.getSize())
                .totalPages(coursePage.getTotalPages())
                .totalElements(coursePage.getTotalElements())
                .data(coursePage.getContent().stream()
                        .map(this::toCourseResponse)
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseResponse getCourseById(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        return toCourseResponse(course);
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (courseRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.COURSE_CODE_EXISTED);
        }

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        sessionVariableHelper.setCurrentUserId(currentUserId);

        Certificate certificate = resolveCertificate(request.getCertificateId());
        CertificatePart certificatePart = resolveCertificatePart(request.getCertificatePartId());
        CourseRules.validatePartBelongsTo(certificate, certificatePart);
        CourseRules.validateScores(certificate, certificatePart, request.getEntryScore(), request.getTargetScore());
        CourseRules.validatePrice(request.getPrice());

        // Quy tắc: khóa học do Admin tạo RA KHÔNG có mentor sẵn.
        // Mentor chỉ được gán vào khóa sau khi học viên mua gói kèm 1-1
        // (xem CourseMentorAssignmentService) → nên BỎ QUA mentorId trong request.
        if (request.getMentorId() != null) {
            log.info("Bỏ qua mentorId khi tạo khóa học {} — khóa học không gán mentor sẵn",
                    request.getCode());
        }

        Course course = Course.builder()
                .code(code)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .level(CourseRules.normalizeLevel(request.getLevel()))
                .price(request.getPrice())
                .targetScore(request.getTargetScore())
                .entryScore(request.getEntryScore())
                .thumbnailUrl(request.getThumbnailUrl())
                .status(CourseStatus.DRAFT)
                .createdByUser(creator)
                .certificate(certificate)
                .certificatePart(certificatePart)
                .mentor(null)
                .build();

        try {
            course = courseRepository.saveAndFlush(course);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Tạo khóa học bị trigger từ chối: {}", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().contains("Only admins")) {
                throw new AppException(ErrorCode.FORBIDDEN_OPERATION);
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        log.info("Đã tạo khóa học: courseId={}, code={}", course.getCourseId(), course.getCode());

        return toCourseResponse(course);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            course.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getLevel() != null) {
            // Giữ nguyên giá trị cũ (dữ liệu trước khi chuẩn hoá CEFR), giá trị mới phải là CEFR.
            if (!request.getLevel().equals(course.getLevel())) {
                course.setLevel(CourseRules.normalizeLevel(request.getLevel()));
            }
        }
        if (request.getPrice() != null) {
            CourseRules.validatePrice(request.getPrice());
            course.setPrice(request.getPrice());
        }
        if (request.getTargetScore() != null) {
            course.setTargetScore(request.getTargetScore());
        }
        if (request.getEntryScore() != null) {
            course.setEntryScore(request.getEntryScore());
        }
        if (request.getThumbnailUrl() != null) {
            course.setThumbnailUrl(request.getThumbnailUrl());
        }
        if (request.getCertificateId() != null) {
            Certificate certificate = resolveCertificate(request.getCertificateId());
            boolean changed = course.getCertificate() == null
                    || !course.getCertificate().getCertificateId().equals(certificate.getCertificateId());
            course.setCertificate(certificate);
            // Đổi chứng chỉ → kỹ năng của chứng chỉ cũ không còn hợp lệ.
            if (changed && request.getCertificatePartId() == null) {
                course.setCertificatePart(null);
            }
        }
        if (request.getCertificatePartId() != null) {
            course.setCertificatePart(resolveCertificatePart(request.getCertificatePartId()));
        }
        CourseRules.validatePartBelongsTo(course.getCertificate(), course.getCertificatePart());
        CourseRules.validateScores(course.getCertificate(), course.getCertificatePart(),
                course.getEntryScore(), course.getTargetScore());
        if (request.getMentorId() != null) {
            // Admin KHÔNG gán mentor cho khóa học — mentor do hệ thống gán tự động
            // khi học viên mua gói kèm 1-1 (CourseMentorAssignmentService).
            // Giữ nguyên mentor hiện có, không ghi đè.
            log.info("Bỏ qua mentorId khi cập nhật khóa học {} — không cho admin gán mentor",
                    courseId);
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            CourseStatus nextStatus = parseStatus(request.getStatus());
            if (nextStatus == CourseStatus.ACTIVE && course.getStatus() != CourseStatus.ACTIVE
                    && (course.getPrice() == null || courseContentRepository.countByCourse_CourseId(courseId) == 0)) {
                throw new AppException(ErrorCode.COURSE_CANNOT_ACTIVATE);
            }
            course.setStatus(nextStatus);
        }

        course.setLastUpdated(LocalDateTime.now());
        course = courseRepository.save(course);

        log.info("Đã cập nhật khóa học: courseId={}", courseId);

        return toCourseResponse(course);
    }

    // ========================================================================
    // OUTLINE / CONTENT  (từng là CourseContentServiceImpl)
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CourseContentResponse> getCourseContents(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isEnrolled = enrollmentChecker.canAccessContent(currentUserId, courseId);

        List<CourseContent> contents =
                courseContentRepository.findByCourse_CourseIdOrderByContentOrderAsc(courseId);

        return contents.stream()
                .map(content -> buildContentResponse(content, currentUserId, isEnrolled))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseVocabularySetResponse> getVocabularies(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean canView = enrollmentChecker.canAccessContent(currentUserId, courseId);

        // Nội dung 'vocabulary' được admin đánh dấu "xem thử" (isPreview = true)
        // → học viên CHƯA ghi danh khóa học vẫn xem được các bộ từ của nội dung đó.
        // (Trước đây chỉ 'lesson' mới có cơ chế xem thử → vocabulary luôn bị khoá.)
        Set<UUID> previewSetIds = collectPreviewVocabularySetIds(courseId);

        List<CourseVocabularySet> links =
                courseVocabularySetRepository.findByCourse_CourseIdWithDetails(courseId);

        // Không có nội dung nào được admin tick "xem thử" → mở bộ ĐẦU TIÊN làm
        // mẫu học thử, để nút "Học thử miễn phí" luôn có nội dung để xem.
        boolean useSample = !canView && previewSetIds.isEmpty() && !links.isEmpty();
        UUID sampleSetId = useSample ? links.get(0).getVocabularySet().getSetId() : null;

        final boolean sampleMode = useSample;
        final UUID sampleId = sampleSetId;

        return links.stream()
                .map(link -> {
                    UUID setId = link.getVocabularySet().getSetId();
                    boolean isPreview = previewSetIds.contains(setId)
                            || (sampleMode && setId.equals(sampleId));
                    return toCourseVocabularyResponse(link, canView, isPreview);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContentResponse> getHomeworkList(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isEnrolled = enrollmentChecker.canAccessContent(currentUserId, courseId);

        // Học viên CHƯA mua gói kèm 1-1 → không thấy bài tập của khóa (tab "Bài tập"
        // bị ẩn hoàn toàn ở UI và API trả rỗng → không lách được bằng cách gọi thẳng).
        // Admin/Mentor vẫn thấy để quản lý / chấm bài.
        if (currentUserId != null && !isStaff() && !graderAssignmentService.hasMentor(currentUserId)) {
            return List.of();
        }

        List<CourseContent> contents =
                courseContentRepository.findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(courseId, ContentType.HOMEWORK);

        // Bài tập KHÔNG mở khoá cho người chưa ghi danh — kể cả khi admin tick
        // "xem thử" hay khi dùng bài đầu tiên làm mẫu. Lý do: bài tập gắn với
        // quyền nộp + chấm điểm, học thử chỉ mở phần XEM (bài học, từ vựng).
        // → Học viên chưa mua khóa học thấy bài tập ở trạng thái khoá.
        return contents.stream()
                .map(content -> buildContentResponse(content, currentUserId, isEnrolled))
                .toList();
    }

    @Override
    @Transactional
    public CourseContentResponse createContent(UUID courseId, CreateCourseContentRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

        ContentType contentType = parseContentType(request.getContentType());

        CourseContent parent = resolveParent(request.getParentContentId(), courseId, null);

        String title = contentType == ContentType.VOCABULARY
                ? request.getTitle()
                : CourseRules.requireTitle(request.getTitle());
        CourseRules.validateOrder(request.getContentOrder());

        String contentData = normalizeContentData(request.getContentData());
        CertificatePart skill = null;

        Exam exam = null;
        List<UUID> vocabularySetIds = null;

        if (contentType == ContentType.VOCABULARY) {
            // Nội dung 'vocabulary' KHÔNG chứa từ — nó là CON TRỎ tới các bộ
            // trong KHO TỪ VỰNG CHUNG (`vocabulary_sets`). Mỗi bộ được tham chiếu
            // bằng `setId` và được gán vào khóa học qua `course_vocabulary_sets`.
            vocabularySetIds = resolveVocabularySetIds(contentData, request.getVocabularySetIds());
            if (vocabularySetIds.isEmpty()) {
                throw new AppException(ErrorCode.VOCABULARY_CONTENT_MUST_BE_ASSIGNED);
            }
            contentData = buildVocabularyContentData(vocabularySetIds, readIsPreview(contentData));
        } else if (contentType == ContentType.HOMEWORK) {
            contentData = CourseRules.validateHomeworkData(contentData, null);
            exam = isMultipleChoiceHomework(contentData)
                    ? resolveExam(request.getExamId(), contentData, courseId)
                    : null;
            // Bài trắc nghiệm BẮT BUỘC có đề thi (học viên cần đề để làm,
            // backend `ExamGrader` cần đề để chấm).
            if (isMultipleChoiceHomework(contentData) && exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED);
            }
            contentData = exam != null ? withExamId(contentData, exam.getExamId()) : withoutExamId(contentData);
            skill = resolveHomeworkSkill(course, request.getCertificatePartId());
        } else if (contentType == ContentType.EXAM) {
            // Nội dung "Bài thi cuối kỳ" — CHỈ gắn được đề CUỐI KỲ của kho đề thi.
            exam = resolveFinalExam(request.getExamId(), contentData, courseId);
            if (exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED,
                        "Vui lòng chọn một đề thi CUỐI KỲ trong kho đề thi");
            }
            contentData = withExamId(contentData, exam.getExamId());
        }

        CourseContent content = CourseContent.builder()
                .course(course)
                .contentType(contentType)
                .parentContent(parent)
                .title(title)
                .description(request.getDescription())
                .contentOrder(request.getContentOrder() != null ? request.getContentOrder() : nextOrder(courseId))
                .contentData(contentData)
                .exam(exam)
                .certificatePart(skill)
                .build();

        content = courseContentRepository.save(content);

        // Đảm bảo các bộ từ vựng được tham chiếu đã gán vào khóa học.
        if (contentType == ContentType.VOCABULARY) {
            ensureVocabularySetsAssigned(courseId, vocabularySetIds);
        }

        log.info("Đã thêm nội dung: contentId={}, type={}", content.getContentId(), contentType);

        // Bài tập cần mentor chấm tay (không phải trắc nghiệm) → báo mentor
        // phụ trách khóa học biết có bài mới, tránh bài nằm im không ai thấy.
        notifyMentorNewHomework(course, content, contentData);

        return buildContentResponse(content, currentUserId, true);
    }

    @Override
    @Transactional
    public CourseContentResponse updateContent(UUID contentId, UpdateCourseContentRequest request) {
        // Bài tập riêng của mentor (V27, course = null) chỉ sửa / xóa qua /api/v1/mentor/homeworks
        CourseContent content = courseContentRepository.findById(contentId)
                .filter(c -> c.getCourse() != null)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

        UUID courseId = content.getCourse().getCourseId();
        if (request.getTitle() != null) {
            content.setTitle(content.getContentType() == ContentType.VOCABULARY
                    ? request.getTitle()
                    : CourseRules.requireTitle(request.getTitle()));
        }
        if (request.getDescription() != null) {
            content.setDescription(request.getDescription());
        }
        if (request.getContentOrder() != null) {
            CourseRules.validateOrder(request.getContentOrder());
            content.setContentOrder(request.getContentOrder());
        }
        if (request.getParentContentId() != null) {
            content.setParentContent(resolveParent(request.getParentContentId(), courseId, content.getContentId()));
        }

        String contentData = request.getContentData() != null
                ? normalizeContentData(request.getContentData())
                : content.getContentData();

        if (content.getContentType() == ContentType.VOCABULARY) {
            List<UUID>             vocabularySetIds = resolveVocabularySetIds(contentData, request.getVocabularySetIds());
            if (vocabularySetIds.isEmpty()) {
                throw new AppException(ErrorCode.VOCABULARY_CONTENT_MUST_BE_ASSIGNED);
            }
            contentData = buildVocabularyContentData(vocabularySetIds, readIsPreview(contentData));
            content.setContentData(contentData);
        } else if (content.getContentType() == ContentType.HOMEWORK) {
            String previousData = content.getContentData();
            contentData = CourseRules.validateHomeworkData(contentData, previousData);
            Exam exam = isMultipleChoiceHomework(contentData)
                    ? resolveExam(request.getExamId(), contentData, courseId)
                    : null;
            if (isMultipleChoiceHomework(contentData) && exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED);
            }
            // Đã có học viên nộp → không cho đổi hình thức nộp / đề / thang điểm (bài nộp cũ sẽ lệch).
            if (homeworkSubmissionRepository.existsByHomeworkContent_ContentId(contentId)) {
                assertHomeworkUnchanged(content, contentData, exam);
            }
            content.setExam(exam);
            contentData = exam != null ? withExamId(contentData, exam.getExamId()) : withoutExamId(contentData);
            // Kỹ năng chứng chỉ: chỉ đổi khi request có gửi id (null = giữ nguyên).
            if (request.getCertificatePartId() != null) {
                content.setCertificatePart(resolveHomeworkSkill(content.getCourse(), request.getCertificatePartId()));
            }
            content.setContentData(contentData);
        } else if (content.getContentType() == ContentType.EXAM) {
            // Đổi đề cuối kỳ → vẫn chỉ cho gắn đề CUỐI KỲ.
            Exam exam = resolveFinalExam(request.getExamId(), contentData, courseId);
            if (exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED,
                        "Vui lòng chọn một đề thi CUỐI KỲ trong kho đề thi");
            }
            content.setExam(exam);
            content.setContentData(withExamId(contentData, exam.getExamId()));
        } else {
            content.setContentData(contentData);
        }

        content.setLastUpdated(LocalDateTime.now());

        content = courseContentRepository.save(content);

        if (content.getContentType() == ContentType.VOCABULARY) {
            ensureVocabularySetsAssigned(content.getCourse().getCourseId(),
                    readVocabularySetIds(content.getContentData()));
        }

        return buildContentResponse(content, currentUserId, true);
    }

    @Override
    @Transactional
    public void deleteContent(UUID contentId) {
        // Bài tập riêng của mentor (V27, course = null) chỉ sửa / xóa qua /api/v1/mentor/homeworks
        CourseContent content = courseContentRepository.findById(contentId)
                .filter(c -> c.getCourse() != null)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

        // FK homework_submissions ON DELETE CASCADE → xoá bài tập sẽ xoá luôn bài nộp + điểm của học viên.
        if (content.getContentType() == ContentType.HOMEWORK
                && homeworkSubmissionRepository.existsByHomeworkContent_ContentId(contentId)) {
            throw new AppException(ErrorCode.HOMEWORK_HAS_SUBMISSIONS);
        }

        // Nội dung con tham chiếu tới nội dung cha — thả FK để tránh vi phạm ràng buộc.
        // (FK parent_content_id cũng ON DELETE CASCADE, nhưng xoá tường minh giúp
        //  hành vi rõ ràng và không phụ thuộc vào mức độ trigger của DB.)
        List<CourseContent> children = courseContentRepository.findByParentContent_ContentId(contentId);
        for (CourseContent child : children) {
            child.setParentContent(null);
        }
        if (!children.isEmpty()) {
            courseContentRepository.saveAll(children);
        }

        courseContentRepository.delete(content);

        log.info("Đã xoá nội dung khóa học: contentId={}, type={}", contentId, content.getContentType());
    }

    // ========================================================================
    // Helpers — nội dung theo loại
    // ========================================================================

    /**
     * Chuẩn hoá danh sách {@code setId} của nội dung 'vocabulary'.
     * <p>
     * Nguồn: {@code request.vocabularySetIds} (ưu tiên khi khác null — kể cả
     * danh sách RỖNG, tức admin muốn gỡ hết bộ) hoặc {@code content_data.setIds}.
     * Mọi id đều phải tồn tại trong kho từ vựng chung, nếu không → lỗi 404.
     */
    private List<UUID> resolveVocabularySetIds(String contentData, List<UUID> requested) {
        List<UUID> ids = requested != null
                ? requested
                : readVocabularySetIds(contentData);

        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID id : ids) {
            if (id == null) continue;
            if (!vocabularySetRepository.existsById(id)) {
                throw new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND);
            }
            unique.add(id);
        }
        return new ArrayList<>(unique);
    }

    /** Đọc `setIds` từ JSONB `content_data` (mảng UUID). */
    private List<UUID> readVocabularySetIds(String contentData) {
        List<UUID> result = new ArrayList<>();
        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.hasNonNull("setIds") || !node.get("setIds").isArray()) {
            return result;
        }
        for (JsonNode item : node.get("setIds")) {
            if (item.isTextual()) {
                try {
                    result.add(UUID.fromString(item.asText()));
                } catch (IllegalArgumentException ignored) {
                    // id không hợp lệ → bỏ qua
                }
            }
        }
        return result;
    }

    /**
     * Tạo JSONB `content_data` chuẩn cho nội dung 'vocabulary'.
     * <p>
     * Giữ lại cờ {@code isPreview} (nếu có) để admin có thể đánh dấu nội dung
     * từ vựng là "xem thử" — học viên chưa ghi danh vẫn xem được bộ từ này.
     */
    private String buildVocabularyContentData(List<UUID> setIds, boolean isPreview) {
        List<String> ids = setIds.stream().map(UUID::toString).toList();
        return "{\"setIds\":" + JsonUtil.toJson(ids)
                + ",\"isPreview\":" + isPreview + "}";
    }

    /** Đọc cờ {@code isPreview} từ {@code content_data} (mặc định false). */
    private boolean readIsPreview(String contentData) {
        return JsonUtil.readBoolean(contentData, "isPreview", false);
    }

    /**
     * Đảm bảo các bộ từ vựng được tham chiếu bởi nội dung 'vocabulary' ĐÃ được
     * gán vào khóa học (bảng {@code course_vocabulary_sets}).
     * <p>
     * Quy tắc: chỉ THÊM (idempotent), KHÔNG gỡ. Lý do:
     * <ul>
     *   <li>Cùng một bộ có thể được gán bởi nhiều nội dung 'vocabulary' → không
     *       được gỡ khi 1 nội dung bị xoá/sửa.</li>
     *   <li>Bộ còn có thể đã được gán bởi luồng "Gán từ vựng" (tick chọn) →
     *       xoá nội dung không được âm thầm gỡ bộ khỏi khóa học.</li>
     * </ul>
     * Việc gỡ bộ khỏi khóa học là thao tác riêng của luồng gán từ vựng.
     *
     * @param setIds danh sách bộ cần đảm bảo đã gán; rỗng/null = không làm gì.
     */
    protected void ensureVocabularySetsAssigned(UUID courseId, List<UUID> setIds) {
        if (courseId == null || setIds == null || setIds.isEmpty()) {
            return;
        }

        int order = (int) courseVocabularySetRepository.countByCourse_CourseId(courseId);
        for (UUID setId : setIds) {
            if (courseVocabularySetRepository.existsByCourse_CourseIdAndVocabularySet_SetId(courseId, setId)) {
                continue;
            }
            VocabularySet set = vocabularySetRepository.findById(setId)
                    .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));
            courseVocabularySetRepository.save(CourseVocabularySet.builder()
                    .course(courseRepository.getReferenceById(courseId))
                    .vocabularySet(set)
                    .displayOrder(order++)
                    .build());
        }
    }

    /**
     * Resolve đề thi liên kết cho nội dung 'homework'.
     * <p>
     * Ưu tiên {@code request.examId}; nếu không có thì đọc {@code content_data.examId}.
     * Đề phải tồn tại và thuộc đúng khóa học đang chỉnh sửa.
     */
    private Exam resolveExam(UUID requestedExamId, String contentData, UUID courseId) {
        UUID examId = requestedExamId;
        if (examId == null) {
            String raw = JsonUtil.readString(contentData, "examId");
            if (raw != null && !raw.isBlank()) {
                try {
                    examId = UUID.fromString(raw.trim());
                } catch (IllegalArgumentException ignored) {
                    examId = null;
                }
            }
        }
        if (examId == null) {
            return null;
        }
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        // Đề thuộc KHÁC khóa học đang chỉnh sửa → không gắn được (học viên khóa
        // này cũng không mở được lượt thi vì `ExamAccessPolicy` yêu cầu
        // enrollment đúng khóa của đề).
        if (courseId != null && exam.getCourse() != null
                && !courseId.equals(exam.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.EXAM_NOT_FOUND,
                    "Đề thi \"" + exam.getExamTitle() + "\" thuộc khóa học khác");
        }
        // Đề nháp / lưu trữ thì học viên không mở được lượt thi.
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Đề thi \"" + exam.getExamTitle() + "\" chưa xuất bản, hãy xuất bản đề trước khi gắn vào bài tập");
        }
        // Đề trả phí (không gắn khóa học) → học viên phải mua mới làm được
        // → không cho gắn vào bài tập.
        if (Boolean.FALSE.equals(exam.getIsFree())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Đề thi \"" + exam.getExamTitle() + "\" là đề trả phí, không thể gắn vào bài tập");
        }
        // Đề giữa kỳ / cuối kỳ có điều kiện tiên quyết (đạt quiz / đạt giữa kỳ +
        // đủ bài tập của khóa). Gắn vào bài tập sẽ tạo vòng lặp: bài tập này
        // chưa nộp → đề mở không được → học viên không nộp được bài tập.
        // → Chỉ cho gắn đề `quiz` (và placement test) vào bài tập.
        if (exam.getExamType() == ExamType.MIDTERM || exam.getExamType() == ExamType.FINAL) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Đề \"" + exam.getExamTitle() + "\" là đề giữa kỳ/cuối kỳ (có điều kiện tiên quyết), "
                            + "không thể gắn vào bài tập. Hãy dùng đề Quiz.");
        }
        return exam;
    }

    /**
     * Resolve đề thi CUỐI KỲ cho nội dung 'exam' (Bài thi cuối kỳ) — V29.
     * <p>
     * Luật:
     * <ul>
     *   <li>Đề phải tồn tại và có {@code exam_type = 'final'} (đề cuối kỳ);</li>
     *   <li>Đề phải đã CÔNG BÁI, không trả phí;</li>
     *   <li>Đề thuộc KHÁC khóa học đang chỉnh sửa → không gắn được.</li>
     * </ul>
     *
     * @return đề hợp lệ, hoặc {@code null} nếu không có id được cung cấp.
     */
    private Exam resolveFinalExam(UUID requestedExamId, String contentData, UUID courseId) {
        UUID examId = requestedExamId;
        if (examId == null) {
            String raw = JsonUtil.readString(contentData, "examId");
            if (raw != null && !raw.isBlank()) {
                try {
                    examId = UUID.fromString(raw.trim());
                } catch (IllegalArgumentException ignored) {
                    examId = null;
                }
            }
        }
        if (examId == null) {
            return null;
        }
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        // CHỈ đề CUỐI Kỳ được gắn vào nội dung 'exam'.
        if (exam.getExamType() != ExamType.FINAL) {
            throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_NOT_FINAL,
                    "Đề thi \"" + exam.getExamTitle() + "\" không phải đề CUỐI KỲ");
        }
        // Đề gắn vào KHÓA HỌC KHÁC → học viên khóa này không mở được lượt thi
        // (`ExamAccessPolicy` yêu cầu enrollment đúng khóa của đề).
        if (courseId != null && exam.getCourse() != null
                && !courseId.equals(exam.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_NOT_OWNED,
                    "Đề thi \"" + exam.getExamTitle() + "\" thuộc khóa học khác");
        }
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Đề thi \"" + exam.getExamTitle() + "\" chưa xuất bản, hãy xuất bản đề trước khi gắn");
        }
        if (Boolean.FALSE.equals(exam.getIsFree())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Đề thi \"" + exam.getExamTitle() + "\" là đề trả phí, không thể gắn vào bài thi cuối kỳ");
        }
        // Đề phạm vi TRIAL (không gắn khóa học) → học viên không mở được lượt thi
        // (`ExamAccessPolicy` chỉ cho làm đề có `exam_scope = 'course'`).
        if (exam.getExamScope() != ExamScope.COURSE) {
            throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_NOT_OWNED,
                    "Đề thi \"" + exam.getExamTitle() + "\" không thuộc phạm vi khóa học (thi thử) nên không thể gắn vào bài thi cuối kỳ");
        }
        return exam;
    }

    /** Bỏ `examId` khỏi JSONB khi bài tập không phải trắc nghiệm. */
    private String withoutExamId(String contentData) {        JsonNode node = JsonUtil.parse(contentData);
        if (node == null || !node.isObject()) {
            return contentData;
        }
        ((ObjectNode) node).remove("examId");
        return node.toString();
    }

    /** Nội dung cha phải cùng khóa học và không phải chính nó / con của nó. */
    private CourseContent resolveParent(UUID parentId, UUID courseId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Nội dung không thể là cha của chính nó");
        }
        CourseContent parent = courseContentRepository.findById(parentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        if (!parent.getCourse().getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Nội dung cha phải thuộc cùng khóa học");
        }
        if (selfId != null && parent.getParentContent() != null
                && selfId.equals(parent.getParentContent().getContentId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Không thể chọn nội dung con làm nội dung cha");
        }
        return parent;
    }

    /** Thứ tự tiếp theo của khóa (max + 1) khi admin không nhập. */
    private int nextOrder(UUID courseId) {
        return courseContentRepository.findFirstByCourse_CourseIdOrderByContentOrderDesc(courseId)
                .map(CourseContent::getContentOrder)
                .map(order -> order == null ? 0 : order + 1)
                .orElse(0);
    }

    /** Kỹ năng bài tập đo lường phải thuộc chứng chỉ của khóa học. */
    private CertificatePart resolveHomeworkSkill(Course course, UUID partId) {
        CertificatePart part = resolveCertificatePart(partId);
        if (part != null && course.getCertificate() != null) {
            CourseRules.validatePartBelongsTo(course.getCertificate(), part);
        }
        return part;
    }

    /** Bài tập đã có bài nộp: hình thức nộp, đề thi, điểm tối đa phải giữ nguyên. */
    private void assertHomeworkUnchanged(CourseContent content, String newData, Exam newExam) {
        String oldData = content.getContentData();
        boolean typeChanged = !Objects.equals(CourseRules.readSubmissionType(oldData),
                CourseRules.readSubmissionType(newData));
        UUID oldExamId = content.getExam() != null ? content.getExam().getExamId() : null;
        UUID newExamId = newExam != null ? newExam.getExamId() : null;
        BigDecimal oldMax = HomeworkContentData.readNumber(oldData, "maxScore");
        BigDecimal newMax = HomeworkContentData.readNumber(newData, "maxScore");
        boolean maxChanged = oldMax == null ? newMax != null : newMax == null || oldMax.compareTo(newMax) != 0;
        if (typeChanged || !Objects.equals(oldExamId, newExamId) || maxChanged) {
            throw new AppException(ErrorCode.HOMEWORK_LOCKED);
        }
    }

    /**
     * Bài tập có hình thức nộp trắc nghiệm không?
     * <p>
     * Bám sát {@code content_data.submissionType} — giá trị {@code multiple_choice}
     * nghĩa là học viên làm bài trong phòng thi nên BẮT BUỘC phải có đề thi.
     */
    private boolean isMultipleChoiceHomework(String contentData) {
        return "multiple_choice".equals(JsonUtil.readString(contentData, "submissionType"));
    }

    /** Ghi thêm `examId` vào JSONB `content_data` mà không mất các key khác. */
    private String withExamId(String contentData, UUID examId) {
        JsonNode node = JsonUtil.parse(contentData);
        ObjectNode object = (node != null && node.isObject())
                ? (ObjectNode) node
                : JsonUtil.mapper().createObjectNode();
        object.put("examId", examId.toString());
        return object.toString();
    }

    /**
     * Báo mentor phụ trách khóa học khi Admin giao một BÀI TẬP MỚI cần chấm tay.
     * <p>
     * Bài trắc nghiệm ({@code multiple_choice}) được chấm tự động → không báo.
     * Bài tự luận / nộp file / ghi âm → mentor phải chấm → cần báo để mentor
     * biết mà vào hàng đợi chấm, tránh bài nằm im không ai thấy.
     * <p>
     * Lỗi gửi thông báo KHÔNG làm hỏng việc tạo bài tập → nuốt exception.
     */
    private void notifyMentorNewHomework(Course course, CourseContent content, String contentData) {
        if (content.getContentType() != ContentType.HOMEWORK) {
            return;
        }
        if (isMultipleChoiceHomework(contentData)) {
            return; // chấm tự động → không cần mentor
        }
        User mentor = course.getMentor();
        if (mentor == null || mentor.getUserId() == null) {
            return; // khóa học chưa gán mentor
        }
        try {
            String dueDate = JsonUtil.readString(contentData, "dueDate");
            StringBuilder message = new StringBuilder("Bạn được giao bài tập \"")
                    .append(content.getTitle()).append("\" để chấm.");
            if (dueDate != null && !dueDate.isBlank()) {
                message.append(" Hạn nộp: ").append(dueDate).append('.');
            }
            notificationService.notify(
                    mentor.getUserId(),
                    NotificationType.homework_deadline,
                    "Bài tập mới cần chấm",
                    message.toString());
        } catch (Exception e) {
            log.warn("Không gửi được thông báo bài tập cho mentor: {}", e.getMessage());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private CourseStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_COURSE_STATUS);
        }
        try {
            return CourseStatus.fromValue(status);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_COURSE_STATUS);
        }
    }

    /** Người dùng là quản trị viên hoặc mentor (không phải học viên). */
    private boolean isStaff() {
        return SecurityUtils.getCurrentUserPrincipal()
                .map(p -> "ADMIN".equalsIgnoreCase(p.getRoleName())
                        || "MENTOR".equalsIgnoreCase(p.getRoleName()))
                .orElse(false);
    }

    private void ensureCourseExists(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
    }

    private ContentType parseContentType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE);
        }
        try {
            return ContentType.fromValue(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE);
        }
    }

    private String normalizeContentData(String raw) {
        try {
            return JsonUtil.normalize(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    private Certificate resolveCertificate(UUID certificateId) {
        if (certificateId == null) {
            return null;
        }
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private CertificatePart resolveCertificatePart(UUID partId) {
        if (partId == null) {
            return null;
        }
        return certificatePartRepository.findById(partId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_PART_NOT_FOUND));
    }

    private CourseResponse toCourseResponse(Course course) {
        Certificate certificate = course.getCertificate();
        CertificatePart certificatePart = course.getCertificatePart();
        User mentor = course.getMentor();
        User creator = course.getCreatedByUser();

        // Mentor 1-1 của HỌC VIÊN ĐANG XEM (mỗi học viên có thể mua gói với mentor khác nhau).
        UUID viewerId = SecurityUtils.getCurrentUserId().orElse(null);
        UUID viewerMentorId = viewerId != null
                ? graderAssignmentService.findAssignedMentorId(viewerId).orElse(null)
                : null;
        String viewerMentorName = viewerMentorId != null
                ? graderAssignmentService.findAssignedMentorName(viewerId)
                : null;

        // Tab "Bài tập" chỉ mở cho học viên ĐÃ MUA GÓI MENTOR 1-1 (bài tập cần mentor chấm tay).
        // Admin/Mentor vẫn thấy để quản lý.
        boolean staff = isStaff();
        boolean homeworkEnabled = staff || viewerMentorId != null;

        // Khóa học được nhiều học viên học chung, mỗi người có thể mua gói 1-1 với
        // mentor KHÁC NHAU → cột `mentor_id` của khóa KHÔNG được lộ cho học viên
        // (sẽ hiện tên mentor của học viên khác). Học viên chỉ thấy mentor của MÌNH.
        UUID shownMentorId = staff
                ? (mentor != null ? mentor.getUserId() : null)
                : viewerMentorId;
        String shownMentorName = staff
                ? (mentor != null ? mentor.getFullName() : null)
                : viewerMentorName;

        return CourseResponse.builder()
                .courseId(course.getCourseId())
                .code(course.getCode())
                .title(course.getTitle())
                .description(course.getDescription())
                .level(course.getLevel())
                .price(course.getPrice())
                .targetScore(course.getTargetScore())
                .entryScore(course.getEntryScore())
                .thumbnailUrl(course.getThumbnailUrl())
                .status(course.getStatus() != null ? course.getStatus().getValue() : null)
                .certificateId(certificate != null ? certificate.getCertificateId() : null)
                .certificateName(certificate != null ? certificate.getCertificateName() : null)
                .certificatePartId(certificatePart != null ? certificatePart.getPartId() : null)
                .certificatePartName(certificatePart != null ? certificatePart.getPartName() : null)
                .mentorId(shownMentorId)
                .mentorName(shownMentorName)
                .viewerMentorId(viewerMentorId)
                .viewerMentorName(viewerMentorName)
                .homeworkEnabled(homeworkEnabled)
                .createdByUserId(creator != null ? creator.getUserId() : null)
                .createdAt(course.getCreatedAt())
                .lastUpdated(course.getLastUpdated())
                .build();
    }

    /**
     * Dựng response cho 1 bộ từ vựng đã gán vào khóa học.
     * <p>
     * Học viên CHƯA ghi danh khóa học → không trả danh sách từ (khoá nội dung),
     * chỉ trả metadata để màn client hiển thị trạng thái "mở khoá".
     * <p>
     * Ngoại lệ: bộ thuộc nội dung 'vocabulary' được admin đánh dấu "xem thử"
     * ({@code isPreview = true}) → vẫn trả đầy đủ từ cho học viên chưa ghi danh.
     *
     * @param canView   Học viên đã ghi danh khóa học (xem được mọi bộ).
     * @param isPreview Bộ này được admin cho xem thử miễn phí.
     */
    private CourseVocabularySetResponse toCourseVocabularyResponse(CourseVocabularySet link, boolean canView,
                                                                   boolean isPreview) {
        VocabularySet set = link.getVocabularySet();
        boolean readable = canView || isPreview;

        List<VocabularyWordResponse> words = readable
                ? set.getWords().stream()
                .sorted(Comparator.comparing(l -> l.getWordOrder() == null ? 0 : l.getWordOrder()))
                .map(l -> toVocabularyWordResponse(l.getWord()))
                .filter(Objects::nonNull)
                .toList()
                : List.of();

        return CourseVocabularySetResponse.builder()
                .setId(set.getSetId())
                .assignmentId(link.getId())
                .title(set.getTitle())
                .description(readable ? set.getDescription() : null)
                .displayOrder(link.getDisplayOrder())
                .words(words)
                .wordCount(readable ? words.size() : null)
                .locked(!readable)
                .isPreview(isPreview)
                .build();
    }

    /**
     * Gom id các bộ từ vựng nằm trong nội dung 'vocabulary' được admin đánh dấu
     * "xem thử" ({@code content_data.isPreview = true}).
     * <p>
     * Dùng để mở khoá bộ từ cho học viên CHƯA ghi danh khóa học khi họ bấm
     * "Học thử miễn phí".
     */
    private Set<UUID> collectPreviewVocabularySetIds(UUID courseId) {
        Set<UUID> result = new LinkedHashSet<>();
        List<CourseContent> vocabContents =
                courseContentRepository.findByCourse_CourseIdAndContentType(courseId, ContentType.VOCABULARY);
        for (CourseContent content : vocabContents) {
            if (!JsonUtil.readBoolean(content.getContentData(), "isPreview", false)) {
                continue;
            }
            result.addAll(readVocabularySetIds(content.getContentData()));
        }
        return result;
    }

    private VocabularyWordResponse toVocabularyWordResponse(VocabularyWord word) {
        if (word == null) {
            return null;
        }
        String ipa = word.getIpa();
        return VocabularyWordResponse.builder()
                .wordId(word.getWordId())
                .word(word.getWord())
                .ipa(ipa)
                .phonetic(ipa)
                .meaning(word.getMeaning())
                .audioUrl(word.getAudioUrl())
                .partOfSpeech(word.getPartOfSpeech())
                .example(word.getExample())
                .build();
    }

    /**
     * Quy tắc response một content:
     * <ul>
     *   <li>Nếu học viên đã ghi danh khóa học: hiển thị full videoUrl.</li>
     *   <li>Nếu học viên CHƯA ghi danh:
     *     <ul>
     *       <li>Bài có isPreview = true → vẫn trả về videoUrl.</li>
     *       <li>Bài có isPreview = false → bị khóa, ẩn videoUrl, đánh isLocked = true.</li>
     *     </ul>
     *   </li>
     *   <li>Nội dung bị khóa chỉ trả metadata, không trả description hoặc contentData.</li>
     *   <li>BÀI TẬP (homework) LUÔN khoá với người chưa ghi danh — kể cả khi
     *       admin tick "xem thử". Bài tập gắn với quyền nộp + chấm điểm, học thử
     *       chỉ mở phần XEM (bài học video + từ vựng).</li>
     * </ul>
     */
    private CourseContentResponse buildContentResponse(CourseContent content, UUID currentUserId, boolean isEnrolled) {
        boolean isPreview = JsonUtil.readBoolean(content.getContentData(), "isPreview", false);
        // Bài tập không bao giờ mở khoá cho người chưa mua khóa học.
        boolean homeworkAlwaysLocked = content.getContentType() == ContentType.HOMEWORK;
        boolean locked = !isEnrolled && (homeworkAlwaysLocked || !isPreview);

        String videoUrl = locked ? null : JsonUtil.readString(content.getContentData(), "videoUrl");

        // Đề thi liên kết — ưu tiên FK, fallback về JSONB (dữ liệu cũ chưa có FK).
        UUID examId = content.getExam() != null ? content.getExam().getExamId() : null;
        if (examId == null) {
            String raw = JsonUtil.readString(content.getContentData(), "examId");
            if (raw != null && !raw.isBlank()) {
                try {
                    examId = UUID.fromString(raw.trim());
                } catch (IllegalArgumentException ignored) {
                    examId = null;
                }
            }
        }

        List<String> vocabularySetIds = content.getContentType() == ContentType.VOCABULARY
                ? readVocabularySetIds(content.getContentData()).stream().map(UUID::toString).toList()
                : null;

        /* ---------- V29: tiến độ xem video của học viên ---------- */
        Boolean completed = null;
        Integer watchedSeconds = null;
        Integer completedLessonCount = null;
        Integer totalLessonCount = null;

        if (content.getContentType() == ContentType.LESSON && isEnrolled && currentUserId != null) {
            for (LessonProgress progress : lessonProgressService.listProgress(currentUserId,
                    content.getCourse().getCourseId())) {
                if (content.getContentId().equals(progress.getContentId())) {
                    completed = Boolean.TRUE.equals(progress.getCompleted());
                    watchedSeconds = progress.getWatchedSeconds();
                    break;
                }
            }
        }

        /* ---------- V29: luật mở khoá bài thi cuối kỳ ---------- */
        Boolean requiresMentor = null;
        String examLockReason = null;
        Boolean hasMentorPlan = null;

        if (content.getContentType() == ContentType.EXAM) {
            requiresMentor = courseExamContentService.requiresMentorGrading(examId);
            if (isEnrolled && currentUserId != null) {
                hasMentorPlan = courseExamContentService.hasMentorPlan(currentUserId);
                long total = courseExamContentService.countLessons(content.getCourse().getCourseId());
                long done = lessonProgressService.countCompletedLessons(currentUserId,
                        content.getCourse().getCourseId());
                completedLessonCount = (int) done;
                totalLessonCount = (int) total;

                if (!courseExamContentService.allLessonsCompleted(currentUserId,
                        content.getCourse().getCourseId())) {
                    // Chưa xem xong hết video → khoá.
                    examLockReason = "lessons";
                    locked = true;
                }
                // V30: KHÔNG khoá đề khi học viên chưa mua gói mentor 1-1.
                // Học viên vẫn làm + nộp được toàn bộ đề; phần tự luận lưu nháp
                // (awaiting_mentor) cho tới khi nâng cấp gói → mentor chấm tay.
            }
        }

        return CourseContentResponse.builder()
                .contentId(content.getContentId())
                .courseId(content.getCourse() != null ? content.getCourse().getCourseId() : null)
                .contentType(content.getContentType() != null ? content.getContentType().getValue() : null)
                .parentContentId(content.getParentContent() != null ? content.getParentContent().getContentId() : null)
                .title(content.getTitle())
                .description(locked ? null : content.getDescription())
                .contentOrder(content.getContentOrder())
                .contentData(locked ? null : JsonUtil.parse(content.getContentData()))
                .isPreview(isPreview)
                .isLocked(locked)
                .videoUrl(videoUrl)
                .examId(examId)
                .examTitle(content.getExam() != null ? content.getExam().getExamTitle() : null)
                .certificatePartId(content.getCertificatePart() != null
                        ? content.getCertificatePart().getPartId() : null)
                .vocabularySetIds(vocabularySetIds)
                .completed(completed)
                .watchedSeconds(watchedSeconds)
                .requiresMentor(requiresMentor)
                .hasMentorPlan(hasMentorPlan)
                .examLockReason(examLockReason)
                .completedLessonCount(completedLessonCount)
                .totalLessonCount(totalLessonCount)
                .build();
    }
}
