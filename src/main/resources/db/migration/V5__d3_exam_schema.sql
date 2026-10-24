-- =====================================================
-- Migration V5: Dev 3 - Exam / Test Management
--
-- V1 đã có sẵn 6 bảng exam: question_bank, question_bank_options, exams,
-- exam_questions, exam_attempts, exam_attempt_details. Migration này CHỈ bổ sung
-- phần còn thiếu cho nghiệp vụ thi, KHÔNG sửa/xóa dữ liệu hay migration cũ:
--
--   1. Bảng mới  exam_sections            (Listening / Reading / Writing / Speaking / tùy cấu hình)
--   2. exam_questions                     (+ time_limit_seconds, FK section, unique câu hỏi trong đề)
--   3. exam_attempts                      (+ max_score, expires_at, auto_submitted, ràng buộc chống trùng)
--   4. exam_attempt_details               (+ earned_score, unique mỗi câu một dòng trả lời)
--
-- =====================================================

-- -----------------------------------------------------
-- 1. EXAM_SECTIONS
--    section_type là VARCHAR (không phải enum) để admin cấu hình được section tùy ý:
--    LISTENING, READING, WRITING, SPEAKING, GENERAL, ...
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS exam_sections (
    section_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id             UUID         NOT NULL,
    certificate_part_id UUID,
    section_type        VARCHAR(30)  NOT NULL DEFAULT 'GENERAL',
    title               VARCHAR(255) NOT NULL,
    instructions        TEXT,
    passage_text        TEXT,
    audio_url           VARCHAR(500),
    section_order       INT          NOT NULL DEFAULT 1,
    duration_minutes    INT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE,
    FOREIGN KEY (certificate_part_id) REFERENCES certificate_parts(part_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_exam_sections_exam ON exam_sections(exam_id, section_order);

COMMENT ON TABLE exam_sections IS 'Sections of an exam (Listening/Reading/Writing/Speaking/custom)';

-- -----------------------------------------------------
-- 2. EXAM_QUESTIONS
-- -----------------------------------------------------
ALTER TABLE exam_questions
    ADD COLUMN IF NOT EXISTS time_limit_seconds INT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_questions_section') THEN
        ALTER TABLE exam_questions
            ADD CONSTRAINT fk_exam_questions_section
                FOREIGN KEY (section_id) REFERENCES exam_sections(section_id) ON DELETE CASCADE;
    END IF;
END $$;

-- Một câu hỏi chỉ xuất hiện một lần trong cùng một đề
CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_questions_exam_question
    ON exam_questions(exam_id, question_bank_id);

CREATE INDEX IF NOT EXISTS idx_exam_questions_section ON exam_questions(section_id);
CREATE INDEX IF NOT EXISTS idx_exam_questions_bank ON exam_questions(question_bank_id);

-- -----------------------------------------------------
-- 3. EXAM_ATTEMPTS
--    max_score      : tổng điểm tối đa của đề tại thời điểm bắt đầu (snapshot)
--    expires_at     : hạn chót làm bài = start_time + duration_minutes
--    auto_submitted : TRUE nếu hệ thống tự nộp do hết giờ
-- -----------------------------------------------------
ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS max_score NUMERIC(8,2) NOT NULL DEFAULT 0.00;
ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS auto_submitted BOOLEAN NOT NULL DEFAULT FALSE;

-- Mỗi học viên chỉ có tối đa 1 lượt "in_progress" cho mỗi đề (chống mở nhiều lượt song song)
CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_attempt_in_progress
    ON exam_attempts(exam_id, student_id) WHERE status = 'in_progress';

-- attempt_number không được trùng => không thể vượt max_attempts khi gọi đồng thời
CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_attempt_number
    ON exam_attempts(exam_id, student_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_exam_attempts_status_expires
    ON exam_attempts(status, expires_at);

-- -----------------------------------------------------
-- 4. EXAM_ATTEMPT_DETAILS
--    earned_score: điểm đạt được của câu (tự chấm hoặc mentor chấm)
-- -----------------------------------------------------
ALTER TABLE exam_attempt_details
    ADD COLUMN IF NOT EXISTS earned_score NUMERIC(6,2) NOT NULL DEFAULT 0.00;

-- Mỗi câu hỏi chỉ có đúng một dòng trả lời trong một lượt thi (upsert an toàn)
CREATE UNIQUE INDEX IF NOT EXISTS uq_attempt_detail_question
    ON exam_attempt_details(attempt_id, question_bank_id);

CREATE INDEX IF NOT EXISTS idx_attempt_details_attempt ON exam_attempt_details(attempt_id);
CREATE INDEX IF NOT EXISTS idx_attempt_details_question ON exam_attempt_details(question_bank_id);
