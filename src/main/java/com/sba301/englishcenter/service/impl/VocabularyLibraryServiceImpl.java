package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.response.VocabularyLibraryItemResponse;
import com.sba301.englishcenter.dto.response.VocabularyWordResponse;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.service.VocabularyLibraryService;
import com.sba301.englishcenter.util.VocabularyContentReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Thư viện từ vựng toàn hệ thống — đọc từ {@code course_content.content_data}.
 * <p>
 * Không có bảng riêng: mọi nội dung {@code content_type = 'vocabulary'} của
 * mọi khóa học đều là 1 mục của thư viện. Mục này hỗ trợ CẢ HAI shape:
 * <ul>
 *   <li>shape BỘ → {@code content_data.items} → {@code kind = 'set'}</li>
 *   <li>shape ĐƠN → {@code content_data.word} → {@code kind = 'word'}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VocabularyLibraryServiceImpl implements VocabularyLibraryService {

    private final CourseContentRepository courseContentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VocabularyLibraryItemResponse> getLibrary() {
        List<CourseContent> contents = courseContentRepository
                .findByContentTypeOrderByCourse_CourseIdAscContentOrderAsc(ContentType.VOCABULARY);

        List<VocabularyLibraryItemResponse> items = new ArrayList<>(contents.size());

        for (CourseContent content : contents) {
            List<VocabularyWordResponse> words =
                    VocabularyContentReader.readWords(content.getContentData());

            // Row rỗng / JSONB hỏng → bỏ qua, không làm hỏng endpoint.
            if (words.isEmpty()) {
                continue;
            }

            // `course` là LAZY — cần transaction đọc được (đã có ở trên).
            Course course = content.getCourse();

            items.add(VocabularyLibraryItemResponse.builder()
                    .sourceContentId(content.getContentId())
                    .courseId(course != null ? course.getCourseId() : null)
                    .courseTitle(course != null ? course.getTitle() : null)
                    .setTitle(content.getTitle())
                    .kind(VocabularyContentReader.isSetShape(content.getContentData()) ? "set" : "word")
                    .words(words)
                    .wordCount(words.size())
                    .build());
        }

        log.debug("Đã dựng thư viện từ vựng: {} mục", items.size());
        return items;
    }
}
