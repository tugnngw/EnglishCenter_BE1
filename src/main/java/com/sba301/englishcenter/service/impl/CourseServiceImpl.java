package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.CourseStatus;
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
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.repository.VocabularySetRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.CourseService;
import com.sba301.englishcenter.specification.CourseSpecification;
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
    private final SessionEnrollmentChecker enrollmentChecker;

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
    public CourseResponse getCourseById(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        return toCourseResponse(course);
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        if (courseRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.COURSE_CODE_EXISTED);
        }

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        sessionVariableHelper.setCurrentUserId(currentUserId);

        Certificate certificate = resolveCertificate(request.getCertificateId());
        CertificatePart certificatePart = resolveCertificatePart(request.getCertificatePartId());
        User mentor = resolveMentor(request.getMentorId());

        Course course = Course.builder()
                .code(request.getCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .level(request.getLevel())
                .price(request.getPrice())
                .targetScore(request.getTargetScore())
                .thumbnailUrl(request.getThumbnailUrl())
                .status(CourseStatus.DRAFT)
                .createdByUser(creator)
                .certificate(certificate)
                .certificatePart(certificatePart)
                .mentor(mentor)
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
            course.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getLevel() != null) {
            course.setLevel(request.getLevel());
        }
        if (request.getPrice() != null) {
            course.setPrice(request.getPrice());
        }
        if (request.getTargetScore() != null) {
            course.setTargetScore(request.getTargetScore());
        }
        if (request.getThumbnailUrl() != null) {
            course.setThumbnailUrl(request.getThumbnailUrl());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            course.setStatus(parseStatus(request.getStatus()));
        }
        if (request.getCertificateId() != null) {
            course.setCertificate(resolveCertificate(request.getCertificateId()));
        }
        if (request.getCertificatePartId() != null) {
            course.setCertificatePart(resolveCertificatePart(request.getCertificatePartId()));
        }
        if (request.getMentorId() != null) {
            course.setMentor(resolveMentor(request.getMentorId()));
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

        List<CourseVocabularySet> links =
                courseVocabularySetRepository.findByCourse_CourseIdWithDetails(courseId);

        return links.stream()
                .map(link -> toCourseVocabularyResponse(link, canView))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContentResponse> getHomeworkList(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isEnrolled = enrollmentChecker.canAccessContent(currentUserId, courseId);

        List<CourseContent> contents =
                courseContentRepository.findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(courseId, ContentType.HOMEWORK);

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

        CourseContent parent = null;
        if (request.getParentContentId() != null) {
            parent = courseContentRepository.findById(request.getParentContentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        }

        String contentData = normalizeContentData(request.getContentData());

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
            contentData = buildVocabularyContentData(vocabularySetIds);
        } else if (contentType == ContentType.HOMEWORK) {
            exam = resolveExam(request.getExamId(), contentData, courseId);
            if (exam != null) {
                // Ghi `examId` vào JSONB luôn để client đọc được, đồng thời set FK.
                contentData = withExamId(contentData, exam.getExamId());
            }
            // Bài trắc nghiệm BẮT BUỘC có đề thi (học viên cần đề để làm,
            // backend `ExamGrader` cần đề để chấm).
            if (isMultipleChoiceHomework(contentData) && exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED);
            }
        }

        CourseContent content = CourseContent.builder()
                .course(course)
                .contentType(contentType)
                .parentContent(parent)
                .title(request.getTitle())
                .description(request.getDescription())
                .contentOrder(request.getContentOrder() != null ? request.getContentOrder() : 0)
                .contentData(contentData)
                .exam(exam)
                .certificatePart(resolveCertificatePart(request.getCertificatePartId()))
                .build();

        content = courseContentRepository.save(content);

        // Đảm bảo các bộ từ vựng được tham chiếu đã gán vào khóa học.
        if (contentType == ContentType.VOCABULARY) {
            ensureVocabularySetsAssigned(courseId, vocabularySetIds);
        }

        log.info("Đã thêm nội dung: contentId={}, type={}", content.getContentId(), contentType);

        return buildContentResponse(content, currentUserId, true);
    }

    @Override
    @Transactional
    public CourseContentResponse updateContent(UUID contentId, UpdateCourseContentRequest request) {
        CourseContent content = courseContentRepository.findById(contentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

        if (request.getTitle() != null) {
            content.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            content.setDescription(request.getDescription());
        }
        if (request.getContentOrder() != null) {
            content.setContentOrder(request.getContentOrder());
        }
        if (request.getParentContentId() != null) {
            content.setParentContent(courseContentRepository.findById(request.getParentContentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND)));
        }

        String contentData = request.getContentData() != null
                ? normalizeContentData(request.getContentData())
                : content.getContentData();

        if (content.getContentType() == ContentType.VOCABULARY) {
            List<UUID> vocabularySetIds = resolveVocabularySetIds(contentData, request.getVocabularySetIds());
            if (vocabularySetIds.isEmpty()) {
                throw new AppException(ErrorCode.VOCABULARY_CONTENT_MUST_BE_ASSIGNED);
            }
            contentData = buildVocabularyContentData(vocabularySetIds);
            content.setContentData(contentData);
        } else if (content.getContentType() == ContentType.HOMEWORK) {
            Exam exam = resolveExam(request.getExamId(), contentData, content.getCourse().getCourseId());
            content.setExam(exam);
            if (exam != null) {
                contentData = withExamId(contentData, exam.getExamId());
            }
            if (isMultipleChoiceHomework(contentData) && exam == null) {
                throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED);
            }
            // Kỹ năng chứng chỉ: chỉ đổi khi request có gửi id (null = giữ nguyên).
            if (request.getCertificatePartId() != null) {
                content.setCertificatePart(resolveCertificatePart(request.getCertificatePartId()));
            }
            content.setContentData(contentData);
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
        CourseContent content = courseContentRepository.findById(contentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(currentUserId);

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

    /** Tạo JSONB `content_data` chuẩn cho nội dung 'vocabulary'. */
    private String buildVocabularyContentData(List<UUID> setIds) {
        List<String> ids = setIds.stream().map(UUID::toString).toList();
        return "{\"setIds\":" + JsonUtil.toJson(ids) + "}";
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
        // Đề phải thuộc khóa học đang chỉnh sửa — tránh gắn nhầm đề của khóa khác.
        if (courseId != null && exam.getCourse() != null
                && !courseId.equals(exam.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.EXAM_NOT_FOUND);
        }
        return exam;
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

    private User resolveMentor(UUID mentorId) {
        if (mentorId == null) {
            return null;
        }
        return userRepository.findById(mentorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private CourseResponse toCourseResponse(Course course) {
        Certificate certificate = course.getCertificate();
        CertificatePart certificatePart = course.getCertificatePart();
        User mentor = course.getMentor();
        User creator = course.getCreatedByUser();

        return CourseResponse.builder()
                .courseId(course.getCourseId())
                .code(course.getCode())
                .title(course.getTitle())
                .description(course.getDescription())
                .level(course.getLevel())
                .price(course.getPrice())
                .targetScore(course.getTargetScore())
                .thumbnailUrl(course.getThumbnailUrl())
                .status(course.getStatus() != null ? course.getStatus().getValue() : null)
                .certificateId(certificate != null ? certificate.getCertificateId() : null)
                .certificateName(certificate != null ? certificate.getCertificateName() : null)
                .certificatePartId(certificatePart != null ? certificatePart.getPartId() : null)
                .certificatePartName(certificatePart != null ? certificatePart.getPartName() : null)
                .mentorId(mentor != null ? mentor.getUserId() : null)
                .mentorName(mentor != null ? mentor.getFullName() : null)
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
     */
    private CourseVocabularySetResponse toCourseVocabularyResponse(CourseVocabularySet link, boolean canView) {
        VocabularySet set = link.getVocabularySet();

        List<VocabularyWordResponse> words = canView
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
                .description(canView ? set.getDescription() : null)
                .displayOrder(link.getDisplayOrder())
                .words(words)
                .wordCount(canView ? words.size() : null)
                .locked(!canView)
                .build();
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
     * </ul>
     */
    private CourseContentResponse buildContentResponse(CourseContent content, UUID currentUserId, boolean isEnrolled) {
        boolean isPreview = JsonUtil.readBoolean(content.getContentData(), "isPreview", false);
        boolean locked = !isEnrolled && !isPreview;

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
                .build();
    }
}
