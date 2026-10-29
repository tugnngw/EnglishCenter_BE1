package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.request.AssignVocabularySetsRequest;
import com.sba301.englishcenter.dto.request.CreateVocabularySetRequest;
import com.sba301.englishcenter.dto.request.VocabularyWordRequest;
import com.sba301.englishcenter.dto.response.CourseVocabularySetResponse;
import com.sba301.englishcenter.dto.response.VocabularySetResponse;
import com.sba301.englishcenter.dto.response.VocabularyWordResponse;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseVocabularySet;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.entity.VocabularySet;
import com.sba301.englishcenter.entity.VocabularySetWord;
import com.sba301.englishcenter.entity.VocabularyWord;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.CourseVocabularySetRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.repository.VocabularySetRepository;
import com.sba301.englishcenter.repository.VocabularySetWordRepository;
import com.sba301.englishcenter.repository.VocabularyWordRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.VocabularyService;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Triển khai luồng "Kho từ vựng chung" + "Gán vào khóa học".
 * <p>
 * Bản chất thay đổi so với thiết kế cũ:
 * <ul>
 *   <li>Cũ: từ vựng = {@code course_content(content_type='vocabulary')} → gắn
 *       cứng vào 1 khóa học; "thư viện" chỉ là gom tất cả row của mọi khóa học.</li>
 *   <li>Mới: từ vựng nằm ở {@code vocabulary_sets} / {@code vocabulary_words},
 *       tạo độc lập; liên kết với khóa học qua bảng trung gian
 *       {@code course_vocabulary_sets} (Many-to-Many).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VocabularyServiceImpl implements VocabularyService {

    private final VocabularySetRepository vocabularySetRepository;
    private final VocabularySetWordRepository vocabularySetWordRepository;
    private final VocabularyWordRepository vocabularyWordRepository;
    private final CourseVocabularySetRepository courseVocabularySetRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final SessionVariableHelper sessionVariableHelper;

    // ========================================================================
    // KHO TỪ VỰNG CHUNG
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<VocabularySetResponse> getBankSets(String keyword) {
        List<VocabularySet> sets = vocabularySetRepository.findAllWithWords();

        String kw = (keyword == null ? "" : keyword).trim().toLowerCase();
        if (!kw.isEmpty()) {
            sets = sets.stream()
                    .filter(set -> matches(set, kw))
                    .toList();
        }

        // Đếm số khóa học đang dùng từng bộ (1 query cho toàn bộ bộ).
        Map<UUID, Long> usageCounts = countUsagePerSet();

        return sets.stream()
                .map(set -> toSetResponse(set, usageCounts.getOrDefault(set.getSetId(), 0L).intValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VocabularySetResponse getBankSet(UUID setId) {
        VocabularySet set = vocabularySetRepository.findByIdWithWords(setId)
                .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));

        return toSetResponse(set, countUsage(setId));
    }

    @Override
    @Transactional
    public VocabularySetResponse createBankSet(CreateVocabularySetRequest request) {
        UUID currentUserId = requireCurrentUser();
        sessionVariableHelper.setCurrentUserId(currentUserId);

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String title = request.getTitle().trim();
        if (vocabularySetRepository.findByTitleIgnoreCase(title).isPresent()) {
            throw new AppException(ErrorCode.VOCABULARY_SET_TITLE_EXISTED);
        }

        VocabularySet set = vocabularySetRepository.save(VocabularySet.builder()
                .title(title)
                .description(blankToNull(request.getDescription()))
                .createdByUser(creator)
                .build());

        // Tái sử dụng từ đã có trong kho chung, tạo mới nếu chưa có.
        attachWords(set, request.getWords());

        // Bộ không có từ nào thì vô nghĩa — huỷ toàn bộ thao tác.
        if (vocabularySetWordRepository.findByVocabularySet_SetIdOrderByWordOrderAsc(set.getSetId()).isEmpty()) {
            vocabularySetRepository.delete(set);
            throw new AppException(ErrorCode.VOCABULARY_SET_EMPTY);
        }

        log.info("Đã tạo bộ từ vựng trong kho chung: setId={}, title={}", set.getSetId(), set.getTitle());

        // Nạp lại để `words` phản ánh đúng sau khi insert.
        return toSetResponse(reload(set.getSetId()), 0);
    }

    @Override
    @Transactional
    public VocabularySetResponse updateBankSet(UUID setId, CreateVocabularySetRequest request) {
        UUID currentUserId = requireCurrentUser();
        sessionVariableHelper.setCurrentUserId(currentUserId);

        VocabularySet set = vocabularySetRepository.findByIdWithWords(setId)
                .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            String title = request.getTitle().trim();
            Optional<VocabularySet> existing = vocabularySetRepository.findByTitleIgnoreCase(title);
            if (existing.isPresent() && !existing.get().getSetId().equals(setId)) {
                throw new AppException(ErrorCode.VOCABULARY_SET_TITLE_EXISTED);
            }
            set.setTitle(title);
        }

        if (request.getDescription() != null) {
            set.setDescription(blankToNull(request.getDescription()));
        }

        vocabularySetRepository.save(set);

        if (request.getWords() != null) {
            // Danh sách từ trong bộ là trạng thái tuyệt đối (thêm / bớt đều
            // được phản ánh) → xoá liên kết cũ rồi dựng lại.
            vocabularySetWordRepository.deleteBySetId(setId);
            vocabularySetWordRepository.flush();
            attachWords(set, request.getWords());

            // Bộ không có từ nào thì vô nghĩa — huỷ toàn bộ thao tác.
            if (vocabularySetWordRepository.findByVocabularySet_SetIdOrderByWordOrderAsc(setId).isEmpty()) {
                throw new AppException(ErrorCode.VOCABULARY_SET_EMPTY);
            }
        }

        log.info("Đã cập nhật bộ từ vựng: setId={}", setId);

        // Nạp lại để `words` phản ánh đúng sau khi thêm / xoá liên kết.
        return toSetResponse(reload(setId), countUsage(setId));
    }

    @Override
    @Transactional
    public void deleteBankSet(UUID setId) {
        UUID currentUserId = requireCurrentUser();
        sessionVariableHelper.setCurrentUserId(currentUserId);

        VocabularySet set = vocabularySetRepository.findById(setId)
                .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));

        // Gỡ khỏi mọi khóa học đang dùng trước khi xoá bộ.
        // FK `ON DELETE CASCADE` cũng xử lý được, nhưng xoá tường minh giúp
        // hành vi rõ ràng và không phụ thuộc vào mức độ trigger của DB.
        courseVocabularySetRepository.deleteByVocabularySetId(setId);
        courseVocabularySetRepository.flush();

        vocabularySetWordRepository.deleteBySetId(setId);
        vocabularySetRepository.delete(set);

        log.info("Đã xoá bộ từ vựng khỏi kho chung: setId={}", setId);
    }

    // ========================================================================
    // GÁN VÀO KHÓA HỌC
    // ========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CourseVocabularySetResponse> getCourseSets(UUID courseId) {
        ensureCourseExists(courseId);

        return courseVocabularySetRepository.findByCourse_CourseIdWithDetails(courseId).stream()
                .map(this::toCourseSetResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<CourseVocabularySetResponse> assignSets(UUID courseId, AssignVocabularySetsRequest request) {
        UUID currentUserId = requireCurrentUser();
        sessionVariableHelper.setCurrentUserId(currentUserId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));

        User assigner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Chuẩn hoá danh sách id (bỏ trùng, bỏ rỗng) và kiểm tra tồn tại.
        Set<UUID> requested = new LinkedHashSet<>();
        List<UUID> incoming = request.getSetIds() != null ? request.getSetIds() : List.of();
        for (UUID id : incoming) {
            if (id == null) continue;
            if (!vocabularySetRepository.existsById(id)) {
                throw new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND);
            }
            requested.add(id);
        }

        // Đọc liên kết hiện tại (kèm bộ) — nguồn sự thật cho `displayOrder`.
        List<CourseVocabularySet> current = courseVocabularySetRepository
                .findByCourse_CourseIdWithDetails(courseId);

        // 1. Gỡ các liên kết không còn được chọn.
        List<CourseVocabularySet> toRemove = current.stream()
                .filter(cvs -> !requested.contains(cvs.getVocabularySet().getSetId()))
                .toList();
        if (!toRemove.isEmpty()) {
            courseVocabularySetRepository.deleteAll(toRemove);
            courseVocabularySetRepository.flush();
        }

        // 2. Giữ các liên kết đang tồn tại — cập nhật lại `displayOrder` theo
        //    đúng thứ tự Admin đã tick (thứ tự trong `requested`).
        int order = 0;
        for (UUID setId : requested) {
            CourseVocabularySet existing = current.stream()
                    .filter(cvs -> cvs.getVocabularySet().getSetId().equals(setId))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                if (!Objects.equals(existing.getDisplayOrder(), order)) {
                    existing.setDisplayOrder(order);
                    courseVocabularySetRepository.save(existing);
                }
            } else {
                // 3. Thêm liên kết mới.
                VocabularySet set = vocabularySetRepository.findById(setId)
                        .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));
                courseVocabularySetRepository.save(CourseVocabularySet.builder()
                        .course(course)
                        .vocabularySet(set)
                        .displayOrder(order)
                        .assignedByUser(assigner)
                        .build());
            }
            order++;
        }

        log.info("Đã gán {} bộ từ vựng cho khóa học {}", requested.size(), courseId);

        return getCourseSets(courseId);
    }

    @Override
    @Transactional
    public void unassignSet(UUID courseId, UUID setId) {
        UUID currentUserId = requireCurrentUser();
        sessionVariableHelper.setCurrentUserId(currentUserId);

        CourseVocabularySet link = courseVocabularySetRepository.findLink(courseId, setId)
                .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_ASSIGNED));

        courseVocabularySetRepository.delete(link);

        log.info("Đã gỡ bộ từ vựng {} khỏi khóa học {}", setId, courseId);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Dựng / tái sử dụng các bản ghi từ trong kho chung rồi gắn vào bộ.
     * <p>
     * Từ đã tồn tại (case-insensitive) → tái sử dụng bản ghi cũ và bổ sung
     * các field còn thiếu. Từ chưa có → tạo mới.
     */
    private void attachWords(VocabularySet set, List<VocabularyWordRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Set<String> seen = new LinkedHashSet<>();
        List<VocabularySetWord> links = new ArrayList<>();
        int order = 0;

        for (VocabularyWordRequest req : requests) {
            String word = req.getWord() == null ? "" : req.getWord().trim();
            if (word.isEmpty()) continue;

            String key = word.toLowerCase();
            if (!seen.add(key)) continue; // bỏ trùng trong cùng request

            VocabularyWord entity = vocabularyWordRepository.findByWordIgnoreCase(word)
                    .map(existing -> enrich(existing, req))
                    .orElseGet(() -> VocabularyWord.builder()
                            .word(word)
                            .ipa(blankToNull(req.getIpa()))
                            .meaning(blankToNull(req.getMeaning()))
                            .audioUrl(blankToNull(req.getAudioUrl()))
                            .partOfSpeech(blankToNull(req.getPartOfSpeech()))
                            .example(blankToNull(req.getExample()))
                            .createdByUser(set.getCreatedByUser())
                            .build());

            entity = vocabularyWordRepository.save(entity);

            links.add(VocabularySetWord.builder()
                    .vocabularySet(set)
                    .word(entity)
                    .wordOrder(order++)
                    .build());
        }

        if (!links.isEmpty()) {
            vocabularySetWordRepository.saveAll(links);
        }
    }

    /** Bổ sung các field còn thiếu của từ đã tồn tại (không ghi đè giá trị cũ). */
    private VocabularyWord enrich(VocabularyWord entity, VocabularyWordRequest req) {
        if (isBlank(entity.getIpa()) && !isBlank(req.getIpa())) entity.setIpa(req.getIpa().trim());
        if (isBlank(entity.getMeaning()) && !isBlank(req.getMeaning())) entity.setMeaning(req.getMeaning().trim());
        if (isBlank(entity.getAudioUrl()) && !isBlank(req.getAudioUrl())) entity.setAudioUrl(req.getAudioUrl().trim());
        if (isBlank(entity.getPartOfSpeech()) && !isBlank(req.getPartOfSpeech())) entity.setPartOfSpeech(req.getPartOfSpeech().trim());
        if (isBlank(entity.getExample()) && !isBlank(req.getExample())) entity.setExample(req.getExample().trim());
        return entity;
    }

    /** Nạp lại bộ kèm danh sách từ (sau khi thêm / xoá liên kết). */
    private VocabularySet reload(UUID setId) {
        return vocabularySetRepository.findByIdWithWords(setId)
                .orElseThrow(() -> new AppException(ErrorCode.VOCABULARY_SET_NOT_FOUND));
    }

    /** Bộ có khớp từ khoá hay không (theo tên bộ hoặc theo từ bên trong). */
    private boolean matches(VocabularySet set, String keyword) {
        if (set.getTitle() != null && set.getTitle().toLowerCase().contains(keyword)) {
            return true;
        }
        if (set.getDescription() != null && set.getDescription().toLowerCase().contains(keyword)) {
            return true;
        }
        return set.getWords().stream()
                .anyMatch(link -> {
                    VocabularyWord w = link.getWord();
                    if (w == null) return false;
                    if (w.getWord() != null && w.getWord().toLowerCase().contains(keyword)) return true;
                    return w.getMeaning() != null && w.getMeaning().toLowerCase().contains(keyword);
                });
    }

    /** Đếm số khóa học đang dùng từng bộ (1 query cho toàn bộ bộ). */
    private Map<UUID, Long> countUsagePerSet() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : courseVocabularySetRepository.countUsagePerSet()) {
            if (row[0] instanceof UUID setId && row[1] instanceof Number count) {
                counts.merge(setId, count.longValue(), Long::sum);
            }
        }
        return counts;
    }

    private int countUsage(UUID setId) {
        return (int) courseVocabularySetRepository.countUsageOfSet(setId);
    }

    /** Chuyển entity bộ → response. */
    private VocabularySetResponse toSetResponse(VocabularySet set, int usageCount) {
        List<VocabularyWordResponse> words = set.getWords().stream()
                .sorted(Comparator.comparing(link -> link.getWordOrder() == null ? 0 : link.getWordOrder()))
                .map(link -> toWordResponse(link.getWord()))
                .filter(Objects::nonNull)
                .toList();

        User creator = set.getCreatedByUser();

        return VocabularySetResponse.builder()
                .setId(set.getSetId())
                .title(set.getTitle())
                .description(set.getDescription())
                .words(words)
                .wordCount(words.size())
                .createdByUserId(creator != null ? creator.getUserId() : null)
                .createdByUserName(creator != null ? creator.getFullName() : null)
                .usedInCourseCount(usageCount)
                .build();
    }

    /** Chuyển entity liên kết → response (dùng cho góc nhìn khóa học). */
    private CourseVocabularySetResponse toCourseSetResponse(CourseVocabularySet cvs) {
        VocabularySet set = cvs.getVocabularySet();

        List<VocabularyWordResponse> words = set.getWords().stream()
                .sorted(Comparator.comparing(link -> link.getWordOrder() == null ? 0 : link.getWordOrder()))
                .map(link -> toWordResponse(link.getWord()))
                .filter(Objects::nonNull)
                .toList();

        return CourseVocabularySetResponse.builder()
                .setId(set.getSetId())
                .assignmentId(cvs.getId())
                .title(set.getTitle())
                .description(set.getDescription())
                .displayOrder(cvs.getDisplayOrder())
                .words(words)
                .wordCount(words.size())
                .build();
    }

    private VocabularyWordResponse toWordResponse(VocabularyWord word) {
        if (word == null) return null;

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

    private void ensureCourseExists(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        }
    }

    private UUID requireCurrentUser() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
