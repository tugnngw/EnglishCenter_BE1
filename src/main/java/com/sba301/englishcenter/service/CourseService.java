package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateCourseContentRequest;
import com.sba301.englishcenter.dto.request.CreateCourseRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseContentRequest;
import com.sba301.englishcenter.dto.request.UpdateCourseRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.CourseResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý khóa học: catalog + chi tiết + CRUD + outline bài học (content / vocab / homework).
 * <p>
 * Trước đây có 2 service riêng (CourseService & CourseContentService) — đã gộp về đây
 * để CourseController chỉ inject 1 service theo đúng REST root {@code /api/v1/courses}.
 */
public interface CourseService {

    PageResponse<CourseResponse> getCourses(Pageable pageable, String keyword, UUID certificateId,
                                            UUID certificatePartId, String level,
                                            BigDecimal targetScore, String status);

    CourseResponse getCourseById(UUID courseId);

    CourseResponse createCourse(CreateCourseRequest request);

    CourseResponse updateCourse(UUID courseId, UpdateCourseRequest request);

    /** Toàn bộ outline bài học, đã xử lý preview/locked theo quyền học viên. */
    List<CourseContentResponse> getCourseContents(UUID courseId);

    /** Danh sách từ vựng flashcards kèm audio/IPA. */
    List<CourseContentResponse> getVocabularies(UUID courseId);

    /** Danh sách bài tập của khóa. */
    List<CourseContentResponse> getHomeworkList(UUID courseId);

    CourseContentResponse createContent(UUID courseId, CreateCourseContentRequest request);

    CourseContentResponse updateContent(UUID contentId, UpdateCourseContentRequest request);
}
