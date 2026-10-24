package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Một phần của đề thi (Listening / Reading / Writing / Speaking / tùy cấu hình).
 * {@code sectionType} là chuỗi tự do (VARCHAR) để admin cấu hình section tùy ý.
 */
@Entity
@Table(name = "exam_sections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "section_id")
    private UUID sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    /** Liên kết (tùy chọn) tới phần của chứng chỉ do Dev 2 quản lý. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_part_id")
    private CertificatePart certificatePart;

    @Builder.Default
    @Column(name = "section_type", nullable = false, length = 30)
    private String sectionType = "GENERAL";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    /** Đoạn văn (Reading) hoặc nội dung dùng chung cho cả section. */
    @Column(name = "passage_text", columnDefinition = "TEXT")
    private String passageText;

    /** File nghe dùng chung cho cả section (Listening). */
    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Builder.Default
    @Column(name = "section_order", nullable = false)
    private Integer sectionOrder = 1;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;
}
