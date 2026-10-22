package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CourseContentResponse {

    UUID contentId;
    UUID courseId;
    String contentType;
    UUID parentContentId;
    String title;
    String description;
    Integer contentOrder;

    /**
     * Nội dung JSONB content_data đã parse.
     * Với bài học: chứa isPreview, videoUrl, durationMinutes...
     */
    Object contentData;

    /**
     * Bài học xem trước được phép hiển thị link video.
     * Bài học không preview mà học viên chưa ghi danh sẽ bị khóa (isLocked = true) và
     * không trả về videoUrl.
     */
    Boolean isPreview;

    /** true nghĩa là học viên chưa đủ quyền xem nội dung này. */
    Boolean isLocked;

    /** Chỉ được trả về khi bài học không bị khóa. */
    String videoUrl;
}