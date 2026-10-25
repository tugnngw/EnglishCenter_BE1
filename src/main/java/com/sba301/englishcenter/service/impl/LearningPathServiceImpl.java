package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.LearningPathItemType;
import com.sba301.englishcenter.common.enums.LearningPathStatus;
import com.sba301.englishcenter.dto.learningpath.LearningPathItemData;
import com.sba301.englishcenter.dto.learningpath.StudentProgressData;
import com.sba301.englishcenter.dto.request.LearningPathItemRequest;
import com.sba301.englishcenter.dto.request.LearningPathRequest;
import com.sba301.englishcenter.dto.response.LearningPathItemResponse;
import com.sba301.englishcenter.dto.response.LearningPathProgressResponse;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import com.sba301.englishcenter.entity.LearningPath;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.LearningPathRepository;
import com.sba301.englishcenter.service.CertificateService;
import com.sba301.englishcenter.service.CourseService;
import com.sba301.englishcenter.service.EnrollmentService;
import com.sba301.englishcenter.service.ExamAttemptService;
import com.sba301.englishcenter.service.ExamService;
import com.sba301.englishcenter.service.LearningPathService;
import com.sba301.englishcenter.service.PracticeExamService;
import com.sba301.englishcenter.util.JsonbMapper;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LearningPathServiceImpl implements LearningPathService {

    private static final TypeReference<List<LearningPathItemData>> ITEMS = new TypeReference<>() {
    };
    private static final TypeReference<List<StudentProgressData>> PROGRESS = new TypeReference<>() {
    };
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final LearningPathRepository learningPathRepository;
    private final CertificateService certificateService;
    private final CourseService courseService;
    private final ExamService examService;
    private final PracticeExamService practiceExamService;
    private final EnrollmentService enrollmentService;
    private final ExamAttemptService examAttemptService;

    // ===================== public =====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LearningPathResponse> getLearningPaths(UUID certificateId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Page<LearningPath> page = certificateId == null
                ? learningPathRepository.findAll(sorted)
                : learningPathRepository.findByCertificateId(certificateId, sorted);
        return PageResponse.<LearningPathResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathResponse getLearningPath(UUID learningPathId) {
        return toResponse(findPath(learningPathId));
    }

    // ===================== admin =====================

    @Override
    @Transactional
    public LearningPathResponse create(UUID adminId, LearningPathRequest request) {
        // Chứng chỉ không tồn tại -> CERTIFICATE_NOT_FOUND (Dev 2)
        certificateService.getCertificateById(request.getCertificateId());
        LearningPath path = learningPathRepository.save(LearningPath.builder()
                .certificateId(request.getCertificateId())
                .createdByUserId(adminId)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .items(JsonbMapper.write(buildItems(request.getItems(), List.of())))
                .studentProgress("[]")
                .build());
        log.info("Admin {} created learning path {}", adminId, path.getLearningPathId());
        return toResponse(path);
    }

    @Override
    @Transactional
    public LearningPathResponse update(UUID learningPathId, LearningPathRequest request) {
        LearningPath path = learningPathRepository.findWithLockByLearningPathId(learningPathId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
        certificateService.getCertificateById(request.getCertificateId());
        path.setCertificateId(request.getCertificateId());
        path.setName(request.getName().trim());
        path.setDescription(trimToNull(request.getDescription()));
        // Giữ itemId của chặng cũ (cùng type + refId); tiến độ luôn tính lại theo dữ liệu thật nên không bị lệch
        path.setItems(JsonbMapper.write(buildItems(request.getItems(), itemsOf(path))));
        return toResponse(learningPathRepository.save(path));
    }

    @Override
    @Transactional
    public void delete(UUID learningPathId) {
        LearningPath path = learningPathRepository.findWithLockByLearningPathId(learningPathId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
        if (!progressOf(path).isEmpty()) {
            throw new AppException(ErrorCode.LEARNING_PATH_HAS_STUDENTS);
        }
        learningPathRepository.delete(path);
    }

    // ===================== học viên =====================

    @Override
    @Transactional
    public LearningPathProgressResponse enroll(UUID studentId, UUID learningPathId) {
        // Khóa dòng: student_progress là mảng JSONB, 2 request ghi danh cùng lúc không được nhân đôi
        LearningPath path = learningPathRepository.findWithLockByLearningPathId(learningPathId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
        List<StudentProgressData> progress = progressOf(path);
        if (progress.stream().noneMatch(p -> studentId.equals(p.studentId()))) {
            progress.add(new StudentProgressData(studentId, LearningPathStatus.active, 0, 0,
                    UtcTimeUtil.nowUtc(), null));
            path.setStudentProgress(JsonbMapper.write(progress));
            log.info("Student {} enrolled learning path {}", studentId, learningPathId);
        }
        return refreshProgress(path, studentId);
    }

    @Override
    @Transactional
    public List<LearningPathProgressResponse> getMyProgress(UUID studentId) {
        String filter = JsonbMapper.write(List.of(Map.of("studentId", studentId.toString())));
        List<LearningPathProgressResponse> result = new ArrayList<>();
        for (LearningPath enrolled : learningPathRepository.findEnrolledBy(filter)) {
            LearningPath path = learningPathRepository.findWithLockByLearningPathId(enrolled.getLearningPathId())
                    .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
            result.add(refreshProgress(path, studentId));
        }
        return result;
    }

    // ===================== tính tiến độ =====================

    /** Tính lại tiến độ của 1 học viên theo dữ liệu thật (đã ghi danh khóa / đậu đề / AI chấm đạt band) và lưu. */
    private LearningPathProgressResponse refreshProgress(LearningPath path, UUID studentId) {
        List<LearningPathItemData> items = itemsOf(path);
        List<LearningPathItemResponse> itemResponses = new ArrayList<>();
        int completed = 0;
        for (LearningPathItemData item : items) {
            boolean done = isCompleted(studentId, item);
            if (done) {
                completed++;
            }
            itemResponses.add(toItemResponse(item, done));
        }
        int total = items.size();
        int percent = total == 0 ? 0 : completed * 100 / total;
        boolean finished = total > 0 && completed == total;

        List<StudentProgressData> progress = progressOf(path);
        int index = indexOf(progress, studentId);
        StudentProgressData old = progress.get(index);
        // Đã hoàn thành rồi thì giữ mốc completedAt cũ; admin thêm chặng mới thì quay lại active
        LocalDateTime completedAt = finished ? (old.completedAt() != null ? old.completedAt() : UtcTimeUtil.nowUtc()) : null;
        LearningPathStatus status = old.status() == LearningPathStatus.abandoned ? LearningPathStatus.abandoned
                : finished ? LearningPathStatus.completed : LearningPathStatus.active;
        StudentProgressData updated = new StudentProgressData(studentId, status, percent, completed,
                old.enrolledAt(), completedAt);
        if (!updated.equals(old)) {
            progress.set(index, updated);
            path.setStudentProgress(JsonbMapper.write(progress));
        }
        // Entity đang được quản lý trong transaction: thay đổi (kể cả ghi danh mới) được flush khi commit
        learningPathRepository.save(path);
        if (finished && old.completedAt() == null) {
            log.info("Student {} completed learning path {}", studentId, path.getLearningPathId());
        }
        return LearningPathProgressResponse.builder()
                .learningPathId(path.getLearningPathId())
                .certificateId(path.getCertificateId())
                .name(path.getName())
                .status(status.name())
                .progressPercent(percent)
                .completedItems(completed)
                .totalItems(total)
                .enrolledAt(UtcTimeUtil.toInstant(updated.enrolledAt()))
                .completedAt(UtcTimeUtil.toInstant(completedAt))
                .items(itemResponses)
                .build();
    }

    private boolean isCompleted(UUID studentId, LearningPathItemData item) {
        return switch (item.type()) {
            // Quyết định nhóm: chặng khóa học hoàn thành khi đã ghi danh khóa (đã thanh toán)
            case COURSE -> enrollmentService.isEnrolled(studentId, item.refId());
            case EXAM -> examAttemptService.hasPassedExam(studentId, item.refId());
            case PRACTICE_EXAM -> practiceExamService.hasReachedBand(studentId, item.refId(), item.targetBand());
        };
    }

    // ===================== helpers =====================

    private List<LearningPathItemData> buildItems(List<LearningPathItemRequest> requests, List<LearningPathItemData> existing) {
        Map<String, UUID> existingIds = new HashMap<>();
        existing.forEach(i -> existingIds.put(i.type() + ":" + i.refId(), i.itemId()));
        Set<String> seen = new HashSet<>();
        List<LearningPathItemData> items = new ArrayList<>();
        for (LearningPathItemRequest r : requests) {
            String key = r.getType() + ":" + r.getRefId();
            if (!seen.add(key)) {
                // Cùng 1 khóa / đề xuất hiện 2 lần trong lộ trình
                throw new AppException(ErrorCode.LEARNING_PATH_ITEM_INVALID);
            }
            if (r.getTargetBand() != null && r.getType() != LearningPathItemType.PRACTICE_EXAM) {
                throw new AppException(ErrorCode.LEARNING_PATH_ITEM_INVALID);
            }
            items.add(new LearningPathItemData(existingIds.getOrDefault(key, UUID.randomUUID()), r.getStage().trim(),
                    r.getOrder(), r.getType(), r.getRefId(), resolveTitle(r.getType(), r.getRefId()), r.getTargetBand()));
        }
        items.sort(Comparator.comparingInt(LearningPathItemData::order));
        return items;
    }

    /** Lấy tiêu đề qua service của module khác; không tồn tại -> lỗi NOT_FOUND tương ứng. */
    private String resolveTitle(LearningPathItemType type, UUID refId) {
        return switch (type) {
            case COURSE -> courseService.getCourseById(refId).getTitle();
            case EXAM -> examService.getExamDetail(refId).getExam().getExamTitle();
            case PRACTICE_EXAM -> practiceExamService.getTitle(refId);
        };
    }

    private LearningPath findPath(UUID learningPathId) {
        return learningPathRepository.findById(learningPathId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
    }

    private List<LearningPathItemData> itemsOf(LearningPath path) {
        return JsonbMapper.readList(path.getItems(), ITEMS);
    }

    private List<StudentProgressData> progressOf(LearningPath path) {
        return JsonbMapper.readList(path.getStudentProgress(), PROGRESS);
    }

    private int indexOf(List<StudentProgressData> progress, UUID studentId) {
        for (int i = 0; i < progress.size(); i++) {
            if (studentId.equals(progress.get(i).studentId())) {
                return i;
            }
        }
        throw new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LearningPathItemResponse toItemResponse(LearningPathItemData item, Boolean completed) {
        return LearningPathItemResponse.builder()
                .itemId(item.itemId())
                .stage(item.stage())
                .order(item.order())
                .type(item.type().name())
                .refId(item.refId())
                .title(item.title())
                .targetBand(item.targetBand())
                .completed(completed)
                .build();
    }

    private LearningPathResponse toResponse(LearningPath path) {
        List<LearningPathItemData> items = itemsOf(path);
        return LearningPathResponse.builder()
                .learningPathId(path.getLearningPathId())
                .certificateId(path.getCertificateId())
                .name(path.getName())
                .description(path.getDescription())
                .totalItems(items.size())
                .enrolledCount(progressOf(path).size())
                .items(items.stream().map(i -> toItemResponse(i, null)).toList())
                .createdAt(UtcTimeUtil.fromServerTime(path.getCreatedAt()))
                .lastUpdated(UtcTimeUtil.fromServerTime(path.getLastUpdated()))
                .build();
    }
}
