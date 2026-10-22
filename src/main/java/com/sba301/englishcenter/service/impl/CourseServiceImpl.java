package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.dto.request.CreateCourseContentRequest;
import com.sba301.englishcenter.dto.request.CreateCourseRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseContentRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.CourseResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.UserRepository;
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
import java.util.List;
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
    private final SessionEnrollmentChecker enrollmentChecker;

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
        boolean isEnrolled = currentUserId != null && enrollmentChecker.isEnrolled(currentUserId, courseId);

        List<CourseContent> contents =
                courseContentRepository.findByCourse_CourseIdOrderByContentOrderAsc(courseId);

        return contents.stream()
                .map(content -> buildContentResponse(content, currentUserId, isEnrolled))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContentResponse> getVocabularies(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isEnrolled = currentUserId != null && enrollmentChecker.isEnrolled(currentUserId, courseId);

        List<CourseContent> contents =
                courseContentRepository.findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(courseId, ContentType.VOCABULARY);

        return contents.stream()
                .map(content -> buildContentResponse(content, currentUserId, isEnrolled))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseContentResponse> getHomeworkList(UUID courseId) {
        ensureCourseExists(courseId);

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        boolean isEnrolled = currentUserId != null && enrollmentChecker.isEnrolled(currentUserId, courseId);

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

        CourseContent content = CourseContent.builder()
                .course(course)
                .contentType(contentType)
                .parentContent(parent)
                .title(request.getTitle())
                .description(request.getDescription())
                .contentOrder(request.getContentOrder() != null ? request.getContentOrder() : 0)
                .contentData(normalizeContentData(request.getContentData()))
                .build();

        content = courseContentRepository.save(content);

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
        if (request.getContentData() != null) {
            content.setContentData(normalizeContentData(request.getContentData()));
        }
        content.setLastUpdated(LocalDateTime.now());

        content = courseContentRepository.save(content);
        return buildContentResponse(content, currentUserId, true);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private CourseStatus parseStatus(String status) {
        try {
            return CourseStatus.valueOf(status.trim().toUpperCase());
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
        try {
            return ContentType.valueOf(raw.trim().toUpperCase());
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
     * Quy tắc response một content:
     * <ul>
     *   <li>Nếu học viên đã ghi danh khóa học: hiển thị full videoUrl.</li>
     *   <li>Nếu học viên CHƯA ghi danh:
     *     <ul>
     *       <li>Bài có isPreview = true → vẫn trả về videoUrl.</li>
     *       <li>Bài có isPreview = false → bị khóa, ẩn videoUrl, đánh isLocked = true.</li>
     *     </ul>
     *   </li>
     *   <li>Bài vocabulary / homework không có videoUrl: chỉ tiêu đề + contentData.</li>
     * </ul>
     */
    private CourseContentResponse buildContentResponse(CourseContent content, UUID currentUserId, boolean isEnrolled) {
        boolean isPreview = JsonUtil.readBoolean(content.getContentData(), "isPreview", false);
        boolean locked = !isEnrolled && !isPreview && content.getContentType() == ContentType.LESSON;

        String videoUrl = locked ? null : JsonUtil.readString(content.getContentData(), "videoUrl");

        return CourseContentResponse.builder()
                .contentId(content.getContentId())
                .courseId(content.getCourse() != null ? content.getCourse().getCourseId() : null)
                .contentType(content.getContentType() != null ? content.getContentType().getValue() : null)
                .parentContentId(content.getParentContent() != null ? content.getParentContent().getContentId() : null)
                .title(content.getTitle())
                .description(content.getDescription())
                .contentOrder(content.getContentOrder())
                .contentData(JsonUtil.parse(content.getContentData()))
                .isPreview(isPreview)
                .isLocked(locked)
                .videoUrl(videoUrl)
                .build();
    }
}
