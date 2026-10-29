package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.entity.CourseContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseContentRepository extends JpaRepository<CourseContent, UUID> {

    List<CourseContent> findByCourse_CourseIdOrderByContentOrderAsc(UUID courseId);

    List<CourseContent> findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(UUID courseId, ContentType contentType);

    /** Toàn bộ nội dung vocabulary của mọi khóa học — dùng cho "thư viện từ vựng". */
    List<CourseContent> findByContentTypeOrderByCourse_CourseIdAscContentOrderAsc(ContentType contentType);

    long countByCourse_CourseId(UUID courseId);

    /** Nội dung con trực tiếp (dùng để xoá dọn cây khi xoá một nội dung cha). */
    List<CourseContent> findByParentContent_ContentId(UUID parentContentId);

    /**
     * Bài tập (homework) trỏ tới một đề thi.
     * <p>
     * Dùng cho luồng bài trắc nghiệm: từ lượt thi tìm ngược về bài tập để tạo
     * bản ghi {@code homework_submissions}.
     */
    List<CourseContent> findByContentTypeAndExam_ExamId(ContentType contentType, UUID examId);
}
