package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.LearningPathItemType;
import com.sba301.englishcenter.dto.learningpath.StudentProgressData;
import com.sba301.englishcenter.dto.request.HomeworkAiImportRequest;
import com.sba301.englishcenter.dto.request.MentorHomeworkRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.HomeworkAiDraftResponse;
import com.sba301.englishcenter.dto.response.MentorHomeworkResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.LearningPath;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.ClaudeClient;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.LearningPathRepository;
import com.sba301.englishcenter.service.HomeworkContentData;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.MentorHomeworkService;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.JsonbMapper;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorHomeworkServiceImpl implements MentorHomeworkService {

    private static final TypeReference<List<StudentProgressData>> PROGRESS = new TypeReference<>() {
    };
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal DEFAULT_MAX_SCORE = BigDecimal.TEN;
    private static final int MAX_OUTPUT_TOKENS = 8000;

    static final String HOMEWORK_INSTRUCTION = """
            You read an English homework / exercise document for students preparing for an English certificate.
            Return JSON only: {"title":"...","instructions":"...","submissionType":"text","maxScore":10}
            - title: short name of the homework (max 120 characters), in the document's language.
            - instructions: the full task text exactly as in the document: every question, prompt, word limit and note.
              Keep line breaks as \\n. Translate nothing; invent nothing; do not include answer keys.
            - submissionType: "audio" if the student must speak / record; "file" if the student must upload a document
              (long multi-part answer sheet, drawing, table to fill); otherwise "text".
            - maxScore: the total points if the document states them, otherwise 10.
            """;

    private final CourseContentRepository courseContentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final LearningPathRepository learningPathRepository;
    private final ClaudeClient claude;
    // Quy tắc "đã mua gói kèm 1-1" dùng chung với phần gán người chấm của Dev 2
    private final HomeworkGraderAssignmentService graderAssignmentService;
    private final ObjectMapper json = new ObjectMapper();

    // ===================== mentor =====================

    @Override
    @Transactional(readOnly = true)
    public List<MentorHomeworkResponse> getMine(UUID mentorId) {
        return courseContentRepository.findByOwnerMentorIdAndCourseIsNullOrderByCreatedAtDesc(mentorId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MentorHomeworkResponse create(UUID mentorId, MentorHomeworkRequest request) {
        CourseContent saved = courseContentRepository.save(CourseContent.builder()
                .ownerMentorId(mentorId)
                .contentType(ContentType.HOMEWORK)
                .title(request.getTitle().trim())
                .contentOrder(0)
                .contentData(buildContentData(request))
                .build());
        log.info("Mentor {} created homework {}", mentorId, saved.getContentId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MentorHomeworkResponse update(UUID mentorId, UUID contentId, MentorHomeworkRequest request) {
        CourseContent homework = findOwned(mentorId, contentId);
        // Đã có học viên nộp: không đổi hình thức nộp / điểm tối đa (giống homework khóa học - HOMEWORK_LOCKED)
        if (submissionRepository.existsByHomeworkContent_ContentId(contentId)) {
            String oldType = HomeworkContentData.readText(homework.getContentData(), "submissionType");
            BigDecimal oldMax = HomeworkContentData.readNumber(homework.getContentData(), "maxScore");
            if (!request.getSubmissionType().equalsIgnoreCase(oldType)
                    || oldMax == null || oldMax.compareTo(request.getMaxScore()) != 0) {
                throw new AppException(ErrorCode.HOMEWORK_LOCKED);
            }
        }
        homework.setTitle(request.getTitle().trim());
        homework.setContentData(buildContentData(request));
        return toResponse(courseContentRepository.save(homework));
    }

    @Override
    @Transactional
    public void delete(UUID mentorId, UUID contentId) {
        CourseContent homework = findOwned(mentorId, contentId);
        if (submissionRepository.existsByHomeworkContent_ContentId(contentId)) {
            throw new AppException(ErrorCode.HOMEWORK_HAS_SUBMISSIONS);
        }
        if (!pathsContaining(contentId).isEmpty()) {
            throw new AppException(ErrorCode.MENTOR_HOMEWORK_IN_PATH);
        }
        courseContentRepository.delete(homework);
        log.info("Mentor {} deleted homework {}", mentorId, contentId);
    }

    @Override
    public HomeworkAiDraftResponse importFromPdf(HomeworkAiImportRequest request) {
        // Dùng chung cách kiểm tra PDF với import đề của admin (base64, bắt đầu bằng %PDF, tối đa 15MB)
        String pdf = ExamAiImportServiceImpl.validPdf(request.getFileBase64());
        if (pdf == null) {
            throw new AppException(ErrorCode.AI_IMPORT_INVALID_FILE);
        }
        String raw = claude.generate(HOMEWORK_INSTRUCTION, pdf, null, MAX_OUTPUT_TOKENS, ErrorCode.AI_IMPORT_FAILED);
        log.info("AI đọc file bài tập {} ({} ký tự kết quả)", request.getFileName(), raw.length());
        try {
            JsonNode root = json.readTree(ClaudeClient.jsonObjectOf(raw));
            String title = root.path("title").asText("").trim();
            String instructions = root.path("instructions").asText("").trim();
            String type = root.path("submissionType").asText("text").trim().toLowerCase(Locale.ROOT);
            BigDecimal maxScore = root.path("maxScore").isNumber() ? root.path("maxScore").decimalValue() : DEFAULT_MAX_SCORE;
            if (!StringUtils.hasText(instructions)) {
                throw new AppException(ErrorCode.AI_IMPORT_FAILED);
            }
            return HomeworkAiDraftResponse.builder()
                    .title(truncate(StringUtils.hasText(title) ? title : fileTitle(request.getFileName()), 255))
                    .instructions(truncate(instructions, 20000))
                    .submissionType(List.of("text", "file", "audio").contains(type) ? type : "text")
                    .maxScore(maxScore.signum() > 0 && maxScore.compareTo(BigDecimal.valueOf(100)) <= 0
                            ? maxScore : DEFAULT_MAX_SCORE)
                    .build();
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI trả bài tập sai định dạng: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_IMPORT_FAILED);
        }
    }

    // ===================== học viên =====================

    @Override
    @Transactional(readOnly = true)
    public CourseContentResponse getForStudent(UUID studentId, UUID contentId) {
        CourseContent homework = courseContentRepository.findById(contentId)
                .filter(this::isMentorHomework)
                .orElseThrow(() -> new AppException(ErrorCode.MENTOR_HOMEWORK_NOT_FOUND));
        assertStudentCanAccess(studentId, homework);

        // Hạn nộp riêng của học viên -> ghi vào content_data.dueDate để modal nộp bài của Dev 2 hiển thị / chặn
        JsonNode data = JsonUtil.parse(homework.getContentData());
        ObjectNode contentData = data instanceof ObjectNode node ? node : json.createObjectNode();
        LocalDate dueDate = resolveDueDate(homework, studentId);
        if (dueDate != null) {
            contentData.put("dueDate", dueDate.toString());
        }
        return CourseContentResponse.builder()
                .contentId(homework.getContentId())
                .contentType(ContentType.HOMEWORK.getValue())
                .title(homework.getTitle())
                .description(homework.getDescription())
                .contentOrder(homework.getContentOrder())
                .contentData(contentData)
                .isPreview(false)
                .isLocked(false)
                .build();
    }

    // ===================== dùng chung =====================

    @Override
    public boolean isMentorHomework(CourseContent content) {
        return content != null && content.getCourse() == null && content.getOwnerMentorId() != null
                && content.getContentType() == ContentType.HOMEWORK;
    }

    @Override
    @Transactional(readOnly = true)
    public void assertStudentCanAccess(UUID studentId, CourseContent homework) {
        // Không mua gói kèm 1-1 (đã thanh toán) thì không có bài tập -> mentor của gói luôn là người chấm, không AI chấm
        if (!graderAssignmentService.hasMentor(studentId)) {
            throw new AppException(ErrorCode.MENTOR_HOMEWORK_PLAN_REQUIRED);
        }
        if (findEnrolledAt(homework.getContentId(), studentId).isEmpty()) {
            throw new AppException(ErrorCode.HOMEWORK_PATH_ENROLLMENT_REQUIRED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDate resolveDueDate(CourseContent homework, UUID studentId) {
        BigDecimal dueDays = HomeworkContentData.readNumber(homework.getContentData(), "dueDays");
        if (dueDays == null || dueDays.intValue() <= 0) {
            return null;
        }
        return findEnrolledAt(homework.getContentId(), studentId)
                .map(enrolledAt -> UtcTimeUtil.toInstant(enrolledAt).atZone(VN_ZONE).toLocalDate()
                        .plusDays(dueDays.intValue()))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveTitleForPath(UUID contentId, UUID pathOwnerId) {
        CourseContent homework = courseContentRepository.findById(contentId)
                .filter(this::isMentorHomework)
                .orElseThrow(() -> new AppException(ErrorCode.MENTOR_HOMEWORK_NOT_FOUND));
        if (!homework.getOwnerMentorId().equals(pathOwnerId)) {
            throw new AppException(ErrorCode.MENTOR_HOMEWORK_NOT_OWNED);
        }
        return homework.getTitle();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPassed(UUID studentId, UUID contentId) {
        return submissionRepository.existsByHomeworkContent_ContentIdAndStudent_UserIdAndPassedTrue(contentId, studentId);
    }

    // ===================== helpers =====================

    private CourseContent findOwned(UUID mentorId, UUID contentId) {
        return courseContentRepository.findById(contentId)
                .filter(this::isMentorHomework)
                .filter(c -> c.getOwnerMentorId().equals(mentorId))
                .orElseThrow(() -> new AppException(ErrorCode.MENTOR_HOMEWORK_NOT_FOUND));
    }

    /** Lộ trình có chặng HOMEWORK trỏ tới bài tập này. */
    private List<LearningPath> pathsContaining(UUID contentId) {
        String filter = JsonbMapper.write(List.of(Map.of(
                "type", LearningPathItemType.HOMEWORK.name(),
                "refId", contentId.toString())));
        return learningPathRepository.findContainingItem(filter);
    }

    /**
     * Ngày ghi danh (UTC) của học viên vào lộ trình chứa bài; ghi danh nhiều lộ trình cùng chứa bài thì lấy
     * lần ghi danh MUỘN nhất (hạn nộp rộng nhất). Empty = chưa ghi danh lộ trình nào chứa bài.
     */
    private Optional<LocalDateTime> findEnrolledAt(UUID contentId, UUID studentId) {
        return pathsContaining(contentId).stream()
                .flatMap(path -> JsonbMapper.readList(path.getStudentProgress(), PROGRESS).stream())
                .filter(p -> studentId.equals(p.studentId()))
                .map(StudentProgressData::enrolledAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    /** content_data giống homework khóa học của Dev 2 (đọc bởi HomeworkContentData / modal nộp bài) + dueDays. */
    private String buildContentData(MentorHomeworkRequest request) {
        ObjectNode data = json.createObjectNode();
        data.put("submissionType", request.getSubmissionType().trim().toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(request.getInstructions())) {
            data.put("instructions", request.getInstructions().trim());
        }
        data.put("maxScore", request.getMaxScore());
        if (request.getPassingScore() != null) {
            data.put("passingScore", request.getPassingScore());
        }
        if (request.getMaxAttempts() != null) {
            data.put("maxAttempts", request.getMaxAttempts());
        }
        if (request.getDueDays() != null) {
            data.put("dueDays", request.getDueDays());
        }
        if (request.getAllowedFileTypes() != null && !request.getAllowedFileTypes().isEmpty()) {
            var types = data.putArray("allowedFileTypes");
            request.getAllowedFileTypes().stream()
                    .map(t -> t.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .forEach(types::add);
        }
        if (StringUtils.hasText(request.getAttachmentUrl())) {
            data.put("attachmentUrl", request.getAttachmentUrl().trim());
            data.put("attachmentName", StringUtils.hasText(request.getAttachmentName())
                    ? request.getAttachmentName().trim() : "De-bai.pdf");
            if (StringUtils.hasText(request.getAttachmentPublicId())) {
                data.put("attachmentPublicId", request.getAttachmentPublicId().trim());
            }
        }
        return data.toString();
    }

    private MentorHomeworkResponse toResponse(CourseContent homework) {
        String data = homework.getContentData();
        BigDecimal maxAttempts = HomeworkContentData.readNumber(data, "maxAttempts");
        BigDecimal dueDays = HomeworkContentData.readNumber(data, "dueDays");
        return MentorHomeworkResponse.builder()
                .contentId(homework.getContentId())
                .title(homework.getTitle())
                .instructions(HomeworkContentData.readText(data, "instructions"))
                .submissionType(HomeworkContentData.readText(data, "submissionType"))
                .maxScore(HomeworkContentData.readNumber(data, "maxScore"))
                .passingScore(HomeworkContentData.readNumber(data, "passingScore"))
                .maxAttempts(maxAttempts != null ? maxAttempts.intValue() : null)
                .dueDays(dueDays != null ? dueDays.intValue() : null)
                .allowedFileTypes(HomeworkContentData.readAllowedFileTypes(data))
                .attachmentUrl(HomeworkContentData.readText(data, "attachmentUrl"))
                .attachmentName(HomeworkContentData.readText(data, "attachmentName"))
                .attachmentPublicId(HomeworkContentData.readText(data, "attachmentPublicId"))
                .learningPaths(pathsContaining(homework.getContentId()).stream()
                        .map(p -> new MentorHomeworkResponse.PathRef(p.getLearningPathId(), p.getName()))
                        .toList())
                .submissionCount(submissionRepository.countByHomeworkContent_ContentId(homework.getContentId()))
                .createdAt(UtcTimeUtil.fromServerTime(homework.getCreatedAt()))
                .lastUpdated(UtcTimeUtil.fromServerTime(homework.getLastUpdated()))
                .build();
    }

    private static String fileTitle(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "Bài tập";
        }
        String name = fileName.trim();
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name.substring(0, name.length() - 4) : name;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
