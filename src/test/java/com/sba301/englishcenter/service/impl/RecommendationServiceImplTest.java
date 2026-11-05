package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.dto.response.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Dev 5: band 1.0 không được gợi ý khóa 4.0 - 5.0 / 5.0 - 6.0.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private ScoringScaleResolver scoringScaleResolver;
    @InjectMocks private RecommendationServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID certId = UUID.randomUUID();
    private Certificate ielts;

    @BeforeEach
    void setUp() {
        ielts = Certificate.builder().certificateId(certId).certificateName("IELTS").build();
        lenient().when(certificateRepository.findById(certId)).thenReturn(Optional.of(ielts));
        lenient().when(scoringScaleResolver.resolve(eq(userId), (String) isNull())).thenReturn(ScoringScale.IELTS);
    }

    private Course course(String title, String entry, String target) {
        return Course.builder().courseId(UUID.randomUUID()).title(title).certificate(ielts).status(CourseStatus.active)
                .entryScore(entry == null ? null : new BigDecimal(entry))
                .targetScore(target == null ? null : new BigDecimal(target)).build();
    }

    private void profile(String current, String target) {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(StudentProfile.builder()
                .userId(userId).targetCertificateId(certId).currentLevel("A1")
                .currentScore(new BigDecimal(current)).targetScore(new BigDecimal(target)).build()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void band1DoesNotGetIntermediateCourses() {
        profile("1.0", "6.0");
        when(courseRepository.findAll(any(Specification.class))).thenReturn(List.of(
                course("IELTS 4.0 - 5.0", "4.0", "5.0"), course("IELTS 5.0 - 6.0", "5.0", "6.0")));

        RecommendationResponse r = service.getRecommendations(userId);

        assertTrue(r.getCourses().isEmpty());
        assertTrue(r.isNeedConsultation());
        assertTrue(r.getLearningPaths().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void noLevelTestYetRecommendsNothing() {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(StudentProfile.builder()
                .userId(userId).targetCertificateId(certId).targetScore(new BigDecimal("3.0")).build()));
        when(courseRepository.findAll(any(Specification.class))).thenReturn(List.of(course("IELTS 5.5 - 7.0", "5.5", "7.0")));

        RecommendationResponse r = service.getRecommendations(userId);

        assertTrue(r.getCourses().isEmpty());
        assertFalse(r.isNeedConsultation());
    }

    @Test
    @SuppressWarnings("unchecked")
    void band4GetsTheCourseStartingAt4() {
        profile("4.0", "6.0");
        when(courseRepository.findAll(any(Specification.class))).thenReturn(List.of(
                course("IELTS 0 - 3.0", "0", "3.0"), course("IELTS 4.0 - 5.0", "4.0", "5.0"),
                course("IELTS 5.0 - 6.0", "5.0", "6.0"), course("IELTS 7.0 - 8.0", "7.0", "8.0")));

        RecommendationResponse r = service.getRecommendations(userId);

        assertEquals(1, r.getCourses().size());
        assertEquals("IELTS 4.0 - 5.0", r.getCourses().get(0).getTitle());
        assertFalse(r.isNeedConsultation());
    }
}
