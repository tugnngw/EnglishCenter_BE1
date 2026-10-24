package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.dto.request.CreateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.CreateExamSectionRequest;
import com.sba301.englishcenter.dto.request.QuestionOptionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamSectionRequest;
import com.sba301.englishcenter.dto.response.ExamQuestionResponse;
import com.sba301.englishcenter.dto.response.ExamSectionResponse;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.QuestionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.ExamStructureService;
import com.sba301.englishcenter.specification.QuestionSpecification;
import com.sba301.englishcenter.util.QuestionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamStructureServiceImpl implements ExamStructureService {

    private final ExamRepository examRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final CertificatePartRepository certificatePartRepository;
    private final UserRepository userRepository;
    private final ExamMapper examMapper;

    // ============================================
    // SECTIONS (D3-03)
    // ============================================

    @Override
    @Transactional
    public ExamSectionResponse createSection(UUID examId, CreateExamSectionRequest request) {
        Exam exam = findEditableExam(examId);

        ExamSection section = ExamSection.builder()
                .exam(exam)
                .title(request.getTitle().trim())
                .sectionType(normalizeSectionType(request.getSectionType()))
                .instructions(request.getInstructions())
                .passageText(request.getPassageText())
                .audioUrl(request.getAudioUrl())
                .sectionOrder(request.getSectionOrder() != null ? request.getSectionOrder()
                        : (int) sectionRepository.countByExam_ExamId(examId) + 1)
                .durationMinutes(request.getDurationMinutes())
                .certificatePart(findPart(request.getCertificatePartId()))
                .build();
        section = sectionRepository.save(section);
        refreshTotals(exam);
        log.info("Thêm section {} ({}) vào đề {}", section.getSectionId(), section.getSectionType(), examId);
        return toSectionResponse(section);
    }

    @Override
    @Transactional
    public ExamSectionResponse updateSection(UUID examId, UUID sectionId, UpdateExamSectionRequest request) {
        findEditableExam(examId);
        ExamSection section = findSection(examId, sectionId);

        if (request.getTitle() != null && !request.getTitle().isBlank()) section.setTitle(request.getTitle().trim());
        if (request.getSectionType() != null) section.setSectionType(normalizeSectionType(request.getSectionType()));
        if (request.getInstructions() != null) section.setInstructions(request.getInstructions());
        if (request.getPassageText() != null) section.setPassageText(request.getPassageText());
        if (request.getAudioUrl() != null) section.setAudioUrl(request.getAudioUrl());
        if (request.getSectionOrder() != null) section.setSectionOrder(request.getSectionOrder());
        if (request.getDurationMinutes() != null) section.setDurationMinutes(request.getDurationMinutes());
        if (request.getCertificatePartId() != null) section.setCertificatePart(findPart(request.getCertificatePartId()));

        return toSectionResponse(sectionRepository.save(section));
    }

    @Override
    @Transactional
    public void deleteSection(UUID examId, UUID sectionId) {
        Exam exam = findEditableExam(examId);
        ExamSection section = findSection(examId, sectionId);
        // FK exam_questions.section_id ON DELETE CASCADE gỡ các câu của section khỏi đề
        sectionRepository.delete(section);
        sectionRepository.flush();
        refreshTotals(exam);
        log.info("Xóa section {} của đề {}", sectionId, examId);
    }

    // ============================================
    // QUESTIONS & OPTIONS (D3-04, D3-05)
    // ============================================

    @Override
    @Transactional
    public ExamQuestionResponse addQuestion(UUID examId, CreateExamQuestionRequest request) {
        Exam exam = findEditableExam(examId);
        ExamSection section = request.getSectionId() != null ? findSection(examId, request.getSectionId()) : null;

        Question question;
        if (request.getQuestionId() != null) {
            // Dùng lại câu hỏi có sẵn trong ngân hàng
            question = questionRepository.findById(request.getQuestionId())
                    .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
            if (examQuestionRepository.existsByExam_ExamIdAndQuestion_QuestionId(examId, question.getQuestionId())) {
                throw new AppException(ErrorCode.EXAM_QUESTION_ALREADY_ADDED);
            }
        } else {
            question = createQuestion(exam, section, request);
        }

        ExamQuestion examQuestion = examQuestionRepository.save(ExamQuestion.builder()
                .exam(exam)
                .section(section)
                .question(question)
                .questionOrder(request.getQuestionOrder() != null ? request.getQuestionOrder()
                        : examQuestionRepository.findMaxQuestionOrder(examId) + 1)
                .timeLimitSeconds(request.getTimeLimitSeconds())
                .build());
        refreshTotals(exam);
        log.info("Thêm câu hỏi {} vào đề {}", question.getQuestionId(), examId);
        return examMapper.toQuestion(examQuestion, null, ExamMapper.View.ADMIN);
    }

    @Override
    @Transactional
    public ExamQuestionResponse updateQuestion(UUID examId, UUID questionId, UpdateExamQuestionRequest request) {
        findEditableExam(examId);
        ExamQuestion examQuestion = examQuestionRepository.findDetailedByExamIdAndQuestionId(examId, questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        Question question = examQuestion.getQuestion();

        boolean contentChanged = request.getQuestionType() != null || request.getQuestionText() != null
                || request.getPoints() != null || request.getDifficultyLevel() != null
                || request.getTopic() != null || request.getExplanation() != null
                || request.getAudioUrl() != null || request.getImageUrl() != null
                || request.getCertificatePartId() != null || request.getOptions() != null;

        if (contentChanged) {
            // Câu đã có người trả lời: sửa đáp án sẽ làm sai lệch kết quả cũ
            if (answerRepository.existsByQuestion_QuestionId(questionId)) {
                throw new AppException(ErrorCode.QUESTION_IN_USE);
            }
            // Câu dùng chung với đề khác: sửa ở đây sẽ đổi luôn đề kia
            if (examQuestionRepository.existsByQuestion_QuestionIdAndExam_ExamIdNot(questionId, examId)) {
                throw new AppException(ErrorCode.QUESTION_IN_USE,
                        "Câu hỏi đang được dùng ở đề thi khác, hãy tạo câu hỏi mới thay vì sửa");
            }
            QuestionType newType = request.getQuestionType() != null ? request.getQuestionType()
                    : question.getQuestionType();
            if (newType != question.getQuestionType() && request.getOptions() == null) {
                throw new AppException(ErrorCode.INVALID_QUESTION_OPTIONS,
                        "Đổi loại câu hỏi thì phải gửi lại toàn bộ options");
            }
            if (request.getQuestionText() != null) {
                if (request.getQuestionText().isBlank()) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Nội dung câu hỏi không được để trống");
                }
                question.setQuestionText(request.getQuestionText().trim());
            }
            question.setQuestionType(newType);
            if (request.getPoints() != null) question.setPoints(request.getPoints());
            if (request.getDifficultyLevel() != null) question.setDifficultyLevel(request.getDifficultyLevel());
            if (request.getTopic() != null) question.setTopic(request.getTopic());
            if (request.getExplanation() != null) question.setExplanation(request.getExplanation());
            if (request.getAudioUrl() != null) question.setAudioUrl(request.getAudioUrl());
            if (request.getImageUrl() != null) question.setImageUrl(request.getImageUrl());
            if (request.getCertificatePartId() != null) {
                question.setCertificatePart(findPart(request.getCertificatePartId()));
            }
            if (request.getOptions() != null) {
                List<QuestionOption> newOptions = buildOptions(question, newType, request.getOptions());
                question.getOptions().clear();
                question.getOptions().addAll(newOptions);
            } else {
                validateOptions(newType, question.getOptions());
            }
            questionRepository.save(question);
        }

        if (Boolean.TRUE.equals(request.getRemoveFromSection())) {
            examQuestion.setSection(null);
        } else if (request.getSectionId() != null) {
            examQuestion.setSection(findSection(examId, request.getSectionId()));
        }
        if (request.getQuestionOrder() != null) examQuestion.setQuestionOrder(request.getQuestionOrder());
        if (request.getTimeLimitSeconds() != null) examQuestion.setTimeLimitSeconds(request.getTimeLimitSeconds());
        examQuestion = examQuestionRepository.save(examQuestion);

        refreshTotals(examQuestion.getExam());
        return examMapper.toQuestion(examQuestion, null, ExamMapper.View.ADMIN);
    }

    @Override
    @Transactional
    public void removeQuestion(UUID examId, UUID questionId) {
        Exam exam = findEditableExam(examId);
        ExamQuestion examQuestion = examQuestionRepository.findDetailedByExamIdAndQuestionId(examId, questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        examQuestionRepository.delete(examQuestion);
        examQuestionRepository.flush();
        refreshTotals(exam);
        log.info("Gỡ câu hỏi {} khỏi đề {}", questionId, examId);
    }

    // ============================================
    // QUESTION BANK
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamQuestionResponse> searchQuestionBank(Pageable pageable, String keyword, QuestionType type,
                                                                 DifficultyLevel difficulty, UUID certificateId,
                                                                 UUID certificatePartId, String topic) {
        Page<Question> page = questionRepository.findAll(QuestionSpecification.filterQuestions(
                keyword, type, difficulty, certificateId, certificatePartId, topic), pageable);
        return PageResponse.<ExamQuestionResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(q -> examMapper.toQuestion(q, ExamMapper.View.ADMIN)).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ExamQuestionResponse getBankQuestion(UUID questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new AppException(ErrorCode.QUESTION_NOT_FOUND));
        return examMapper.toQuestion(question, ExamMapper.View.ADMIN);
    }

    // ============================================
    // HELPERS
    // ============================================

    private Question createQuestion(Exam exam, ExamSection section, CreateExamQuestionRequest request) {
        if (request.getQuestionType() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "questionType không được để trống khi tạo câu hỏi mới");
        }
        if (request.getQuestionText() == null || request.getQuestionText().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "questionText không được để trống khi tạo câu hỏi mới");
        }
        UUID creatorId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        CertificatePart part = request.getCertificatePartId() != null ? findPart(request.getCertificatePartId())
                : section != null ? section.getCertificatePart() : null;

        Question question = Question.builder()
                .createdByUser(creator)
                .certificate(exam.getCertificate())
                .certificatePart(part)
                .questionType(request.getQuestionType())
                .questionText(request.getQuestionText().trim())
                .points(request.getPoints() != null ? request.getPoints() : BigDecimal.ONE)
                .topic(request.getTopic())
                .explanation(request.getExplanation())
                .audioUrl(request.getAudioUrl())
                .imageUrl(request.getImageUrl())
                .build();
        if (request.getDifficultyLevel() != null) {
            question.setDifficultyLevel(request.getDifficultyLevel());
        }
        question.getOptions().addAll(buildOptions(question, request.getQuestionType(), request.getOptions()));
        return questionRepository.save(question);
    }

    /**
     * Dựng danh sách đáp án và validate theo loại câu hỏi.
     * fill_blank / matching: mọi dòng đều là đáp án đúng nên {@code isCorrect} luôn = true.
     */
    private List<QuestionOption> buildOptions(Question question, QuestionType type,
                                              List<QuestionOptionRequest> requests) {
        List<QuestionOptionRequest> source = requests == null ? List.of() : requests;
        boolean allCorrect = type == QuestionType.FILL_BLANK || type == QuestionType.MATCHING;

        List<QuestionOption> options = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            QuestionOptionRequest r = source.get(i);
            options.add(QuestionOption.builder()
                    .question(question)
                    .optionText(r.getOptionText() == null ? null : r.getOptionText().trim())
                    .isCorrect(allCorrect || Boolean.TRUE.equals(r.getIsCorrect()))
                    .optionOrder(r.getOptionOrder() != null ? r.getOptionOrder() : i + 1)
                    .explanation(r.getExplanation())
                    .build());
        }
        validateOptions(type, options);
        return options;
    }

    private static void validateOptions(QuestionType type, List<QuestionOption> options) {
        QuestionValidator.validate(type, options.stream()
                .map(o -> new QuestionValidator.OptionData(o.getOptionText(), Boolean.TRUE.equals(o.getIsCorrect())))
                .toList());
    }

    /** Cập nhật total_questions / total_parts của đề. */
    private void refreshTotals(Exam exam) {
        exam.setTotalQuestions((int) examQuestionRepository.countByExam_ExamId(exam.getExamId()));
        exam.setTotalParts((int) sectionRepository.countByExam_ExamId(exam.getExamId()));
        examRepository.save(exam);
    }

    private Exam findEditableExam(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        ExamServiceImpl.assertStructureEditable(exam, attemptRepository);
        return exam;
    }

    private ExamSection findSection(UUID examId, UUID sectionId) {
        return sectionRepository.findBySectionIdAndExam_ExamId(sectionId, examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_SECTION_NOT_FOUND));
    }

    private CertificatePart findPart(UUID partId) {
        if (partId == null) {
            return null;
        }
        return certificatePartRepository.findById(partId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_PART_NOT_FOUND));
    }

    private static String normalizeSectionType(String type) {
        return type == null || type.isBlank() ? "GENERAL" : type.trim().toUpperCase(Locale.ROOT);
    }

    private ExamSectionResponse toSectionResponse(ExamSection section) {
        return ExamSectionResponse.builder()
                .sectionId(section.getSectionId())
                .sectionType(section.getSectionType())
                .title(section.getTitle())
                .instructions(section.getInstructions())
                .passageText(section.getPassageText())
                .audioUrl(section.getAudioUrl())
                .sectionOrder(section.getSectionOrder())
                .durationMinutes(section.getDurationMinutes())
                .certificatePartId(section.getCertificatePart() != null ? section.getCertificatePart().getPartId() : null)
                .certificatePartName(section.getCertificatePart() != null
                        ? section.getCertificatePart().getPartName() : null)
                .questionCount((int) examQuestionRepository.countBySection_SectionId(section.getSectionId()))
                .build();
    }
}
