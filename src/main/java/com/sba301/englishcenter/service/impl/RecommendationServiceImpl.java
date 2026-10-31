package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.dto.response.CourseResponse;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import com.sba301.englishcenter.dto.response.RecommendationResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.service.LearningPathService;
import com.sba301.englishcenter.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gợi ý khóa học & lộ trình học cá nhân hóa cho học viên (TASK-DEV1 T8).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final StudentProfileRepository studentProfileRepository;
    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final LearningPathService learningPathService;

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendations(UUID userId) {
        Optional<StudentProfile> profileOpt = studentProfileRepository.findById(userId);
        UUID targetCertId = profileOpt.map(StudentProfile::getTargetCertificateId).orElse(null);
        BigDecimal currentScore = profileOpt.map(StudentProfile::getCurrentScore).orElse(null);
        String currentLevel = profileOpt.map(StudentProfile::getCurrentLevel).orElse(null);
        BigDecimal targetScore = profileOpt.map(StudentProfile::getTargetScore).orElse(null);

        String certName = null;
        if (targetCertId != null) {
            certName = certificateRepository.findById(targetCertId)
                    .map(Certificate::getCertificateName)
                    .orElse(null);
        }

        // 1. Gợi ý khóa học
        List<CourseResponse> recommendedCourses = findRecommendedCourses(targetCertId, currentScore, targetScore);

        // 2. Gợi ý lộ trình
        List<LearningPathResponse> recommendedPaths = findRecommendedLearningPaths(targetCertId);

        // 3. Tóm tắt đề xuất
        String summary = buildSummary(certName, currentLevel, currentScore, targetScore,
                recommendedCourses.size(), recommendedPaths.size());

        return RecommendationResponse.builder()
                .targetCertificateId(targetCertId)
                .targetCertificateName(certName)
                .currentScore(currentScore)
                .currentLevel(currentLevel)
                .targetScore(targetScore)
                .courses(recommendedCourses)
                .learningPaths(recommendedPaths)
                .summary(summary)
                .build();
    }

    private List<CourseResponse> findRecommendedCourses(UUID targetCertId, BigDecimal currentScore, BigDecimal targetScore) {
        Specification<Course> spec = (root, query, cb) -> cb.equal(root.get("status"), CourseStatus.active);
        List<Course> published = courseRepository.findAll(spec);

        if (published.isEmpty()) {
            return List.of();
        }

        if (targetCertId != null) {
            List<Course> matchingCert = published.stream()
                    .filter(c -> c.getCertificate() != null && targetCertId.equals(c.getCertificate().getCertificateId()))
                    .toList();

            if (!matchingCert.isEmpty()) {
                if (currentScore != null && targetScore != null) {
                    // Ưu tiên các khóa học có targetScore nằm trong khoảng [currentScore, targetScore]
                    List<Course> inRange = matchingCert.stream()
                            .filter(c -> c.getTargetScore() != null
                                    && c.getTargetScore().compareTo(currentScore.subtract(BigDecimal.valueOf(0.5))) >= 0
                                    && c.getTargetScore().compareTo(targetScore.add(BigDecimal.valueOf(1.0))) <= 0)
                            .sorted(Comparator.comparing(Course::getTargetScore, Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();

                    if (!inRange.isEmpty()) {
                        return inRange.stream().map(this::toCourseResponse).toList();
                    }
                }
                return matchingCert.stream().map(this::toCourseResponse).toList();
            }
        }

        // Fallback: trả về top 6 khóa học bất kỳ
        return published.stream()
                .limit(6)
                .map(this::toCourseResponse)
                .toList();
    }

    private List<LearningPathResponse> findRecommendedLearningPaths(UUID targetCertId) {
        try {
            PageResponse<LearningPathResponse> page = learningPathService.getLearningPaths(
                    targetCertId, PageRequest.of(0, 10));
            if (page != null && page.getData() != null && !page.getData().isEmpty()) {
                return page.getData();
            }
            if (targetCertId != null) {
                // Nếu chứng chỉ này chưa có lộ trình, thử lấy lộ trình chung
                PageResponse<LearningPathResponse> fallback = learningPathService.getLearningPaths(
                        null, PageRequest.of(0, 5));
                return fallback != null && fallback.getData() != null ? fallback.getData() : List.of();
            }
            return List.of();
        } catch (Exception ex) {
            log.warn("Error fetching recommended learning paths: {}", ex.getMessage());
            return List.of();
        }
    }

    private String buildSummary(String certName, String currentLevel, BigDecimal currentScore, BigDecimal targetScore,
                                int courseCount, int pathCount) {
        StringBuilder sb = new StringBuilder();
        if (certName != null) {
            sb.append("Dành cho mục tiêu ").append(certName);
            if (targetScore != null) {
                sb.append(" ").append(targetScore.toPlainString());
            }
            if (currentLevel != null || currentScore != null) {
                sb.append(" (Trình độ hiện tại: ");
                if (currentLevel != null) sb.append(currentLevel);
                if (currentScore != null) {
                    if (currentLevel != null) sb.append(" · ");
                    sb.append(currentScore.toPlainString());
                }
                sb.append(")");
            }
            sb.append(": ");
        } else {
            sb.append("Khóa học và lộ trình nổi bật: ");
        }
        sb.append("Gợi ý ").append(courseCount).append(" khóa học phù hợp và ")
                .append(pathCount).append(" lộ trình tối ưu.");
        return sb.toString();
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
}
