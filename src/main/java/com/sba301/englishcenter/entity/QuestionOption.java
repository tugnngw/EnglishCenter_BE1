package com.sba301.englishcenter.entity;

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
 * Đáp án của câu hỏi. Ý nghĩa của {@code optionText}/{@code isCorrect} phụ thuộc loại câu hỏi:
 * <ul>
 *     <li>multiple_choice / true_false: mỗi dòng là một lựa chọn, đúng một dòng {@code isCorrect = true};</li>
 *     <li>fill_blank: mỗi dòng là một đáp án được chấp nhận (tất cả {@code isCorrect = true});</li>
 *     <li>matching: mỗi dòng là một cặp {@code "vế trái::vế phải"} (tất cả {@code isCorrect = true});</li>
 *     <li>essay: không có dòng nào (chấm tay).</li>
 * </ul>
 * Bảng không có created_at/last_updated nên không kế thừa BaseEntity.
 */
@Entity
@Table(name = "question_bank_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "option_id")
    private UUID optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Builder.Default
    @Column(name = "is_correct")
    private Boolean isCorrect = false;

    @Column(name = "option_order", nullable = false)
    private Integer optionOrder;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;
}
