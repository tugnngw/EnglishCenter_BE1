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

    long countByCourse_CourseId(UUID courseId);
}