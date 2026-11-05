package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.dto.response.CourseResponse;
import com.sba301.englishcenter.dto.response.RecommendationResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gợi ý khóa học cá nhân hóa cho học viên (TASK-DEV1 T8).
 * Không gợi ý lộ trình: lộ trình do mentor tư vấn riêng trong khóa học / gói kèm 1-1 (learningPaths luôn rỗng).
 * <p>
 * Khóa "vừa sức" (Dev 5 sửa lỗi band 1.0 vẫn gợi ý khóa 4.0 - 5.0, 5.0 - 6.0):
 * <ol>
 *     <li>đúng chứng chỉ mục tiêu, đang mở bán;</li>
 *     <li>điểm đầu vào (entry_score) &le; điểm hiện tại + 1 bước thang (IELTS: 0.5);</li>
 *     <li>điểm mục tiêu của khóa &gt; điểm hiện tại (khóa đã vượt qua thì bỏ);</li>
 *     <li>điểm đầu vào &lt; điểm mục tiêu của học viên;</li>
 *     <li>khóa gần trình độ nhất lên trước (entry cao nhất mà vẫn vừa sức).</li>
 * </ol>
 * Khóa chưa có entry_score thì không gợi ý (không biết có vừa sức không).
 * Chưa có điểm test level -> không gợi ý khóa nào (không đoán trình độ).
 * Không có khóa nào vừa sức -> danh sách rỗng + needConsultation = true: FE báo "chưa có khóa phù hợp",
 * admin bổ sung khóa dần; học viên có thể để lại SĐT cho mentor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private static final int MAX_COURSES = 6;

    private final StudentProfileRepository studentProfileRepository;
    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final ScoringScaleResolver scoringScaleResolver;

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

        // 1. Gợi ý khóa học vừa sức
        List<Course> activeOfCert = findActiveCourses(targetCertId);
        List<Course> fitting = findFittingCourses(userId, activeOfCert, currentScore, targetScore);
        List<CourseResponse> recommendedCourses = fitting.stream().map(this::toCourseResponse).toList();
        boolean needConsultation = currentScore != null && recommendedCourses.isEmpty();

        // 2. Tóm tắt đề xuất
        String summary = buildSummary(certName, currentLevel, currentScore, targetScore,
                recommendedCourses.size(), needConsultation, lowestEntry(activeOfCert));

        return RecommendationResponse.builder()
                .targetCertificateId(targetCertId)
                .targetCertificateName(certName)
                .currentScore(currentScore)
                .currentLevel(currentLevel)
                .targetScore(targetScore)
                .courses(recommendedCourses)
                .learningPaths(List.of())
                .summary(summary)
                .needConsultation(needConsultation)
                .build();
    }

    private List<Course> findActiveCourses(UUID targetCertId) {
        Specification<Course> spec = (root, query, cb) -> cb.equal(root.get("status"), CourseStatus.active);
        List<Course> published = courseRepository.findAll(spec);
        if (targetCertId == null) {
            return published;
        }
        return published.stream()
                .filter(c -> c.getCertificate() != null && targetCertId.equals(c.getCertificate().getCertificateId()))
                .toList();
    }

    private List<Course> findFittingCourses(UUID userId, List<Course> courses, BigDecimal currentScore,
                                            BigDecimal targetScore) {
        // Chưa có điểm test level -> chưa biết trình độ, không đoán (tránh gợi ý khóa quá sức)
        if (courses.isEmpty() || currentScore == null) {
            return List.of();
        }
        ScoringScale scale = scoringScaleResolver.resolve(userId, (String) null);
        BigDecimal maxEntry = currentScore.add(scale.step());
        return courses.stream()
                .filter(c -> c.getEntryScore() != null && c.getEntryScore().compareTo(maxEntry) <= 0)
                .filter(c -> c.getTargetScore() == null || c.getTargetScore().compareTo(currentScore) > 0)
                .filter(c -> targetScore == null || c.getEntryScore().compareTo(targetScore) < 0)
                .sorted(Comparator.comparing(Course::getEntryScore, Comparator.reverseOrder())
                        .thenComparing(Course::getTargetScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_COURSES)
                .toList();
    }

    private static BigDecimal lowestEntry(List<Course> courses) {
        return courses.stream()
                .map(Course::getEntryScore)
                .filter(s -> s != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private String buildSummary(String certName, String currentLevel, BigDecimal currentScore, BigDecimal targetScore,
                                int courseCount, boolean needConsultation, BigDecimal lowestEntry) {
        StringBuilder sb = new StringBuilder();
        if (certName != null) {
            sb.append("Dành cho mục tiêu ").append(certName);
            if (targetScore != null) {
                sb.append(" ").append(formatScore(targetScore));
            }
            if (currentLevel != null || currentScore != null) {
                sb.append(" (Trình độ hiện tại: ");
                if (currentLevel != null) sb.append(currentLevel);
                if (currentScore != null) {
                    if (currentLevel != null) sb.append(" · ");
                    sb.append(formatScore(currentScore));
                }
                sb.append(")");
            }
            sb.append(": ");
        } else {
            sb.append("Bạn chưa chọn chứng chỉ mục tiêu, hãy cập nhật nguyện vọng trong hồ sơ để được gợi ý đúng. ");
        }
        if (currentScore == null) {
            sb.append("Bạn chưa có kết quả test trình độ, hãy làm test để được gợi ý khóa học vừa sức.");
            return sb.toString();
        }
        if (needConsultation) {
            sb.append("Chưa có khóa học phù hợp với trình độ của bạn");
            if (lowestEntry != null) {
                sb.append(" (khóa thấp nhất yêu cầu đầu vào ").append(formatScore(lowestEntry)).append(")");
            }
            sb.append(".");
            return sb.toString();
        }
        sb.append("Gợi ý ").append(courseCount).append(" khóa học vừa sức.");
        return sb.toString();
    }

    /** Thang ≤ 10 (IELTS, VSTEP) hiện 1 chữ số thập phân (7.0), thang lớn (TOEIC) hiện số nguyên (650) */
    private static String formatScore(BigDecimal score) {
        return score.setScale(score.compareTo(BigDecimal.TEN) <= 0 ? 1 : 0, java.math.RoundingMode.HALF_UP).toPlainString();
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
                .entryScore(course.getEntryScore())
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
