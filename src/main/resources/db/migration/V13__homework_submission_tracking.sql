-- =====================================================
-- Migration V8: Bài tập — trạng thái chấm, file Cloudinary, kỹ năng chứng chỉ
--
-- Mục tiêu (chỉ CỘNG THÊM — không xoá/đổi tên/đổi kiểu cột cũ):
--   1. `homework_submissions.passed`          — kết quả đạt/không đạt (null = chưa chấm).
--   2. `homework_submissions.submission_type` — snapshot hình thức nộp tại lúc nộp
--                                              (text | file | multiple_choice).
--   3. `homework_submissions.exam_attempt_id` — liên kết lượt thi thật của bài trắc nghiệm.
--   4. File trên Cloudinary: chỉ lưu metadata + public_id (KHÔNG lưu file/URL tạm).
--   5. `course_content.certificate_part_id`  — gắn bài tập với kỹ năng của chứng chỉ.
--   6. `homework_grade_history`                — lịch sử mentor sửa điểm.
--
-- Tương thích ngược: mọi cột mới đều NULLABLE, dữ liệu cũ chạy y hệt.
-- =====================================================

-- ---------------------------------------------------------------
-- 1. Trạng thái chấm + hình thức nộp + liên kết lượt thi
-- ---------------------------------------------------------------
ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS passed BOOLEAN;

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS submission_type VARCHAR(20);

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS exam_attempt_id UUID;

-- ---------------------------------------------------------------
-- 2. Metadata file Cloudinary (chỉ lưu public_id, không lưu URL tạm)
-- ---------------------------------------------------------------
ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS file_public_id VARCHAR(255);

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS file_resource_type VARCHAR(20);

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS file_format VARCHAR(20);

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS file_bytes BIGINT;

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS file_original_name VARCHAR(255);

-- ---------------------------------------------------------------
-- 3. FK bài nộp → lượt thi (ON DELETE SET NULL: xoá lượt thi không mất bài nộp)
-- ---------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_homework_sub_exam_attempt'
          AND conrelid = 'homework_submissions'::regclass
    ) THEN
        ALTER TABLE homework_submissions
            ADD CONSTRAINT fk_homework_sub_exam_attempt
                FOREIGN KEY (exam_attempt_id) REFERENCES exam_attempts(attempt_id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_homework_sub_exam_attempt
    ON homework_submissions(exam_attempt_id);

CREATE INDEX IF NOT EXISTS idx_homework_sub_passed
    ON homework_submissions(passed);

-- ---------------------------------------------------------------
-- 4. Gắn bài tập với kỹ năng của chứng chỉ
-- ---------------------------------------------------------------
ALTER TABLE course_content
    ADD COLUMN IF NOT EXISTS certificate_part_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_course_content_certificate_part'
          AND conrelid = 'course_content'::regclass
    ) THEN
        ALTER TABLE course_content
            ADD CONSTRAINT fk_course_content_certificate_part
                FOREIGN KEY (certificate_part_id) REFERENCES certificate_parts(part_id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_course_content_certificate_part
    ON course_content(certificate_part_id);

-- ---------------------------------------------------------------
-- 5. Lịch sử chấm (mentor sửa điểm → ghi lại người sửa, điểm cũ, điểm mới, thời gian)
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS homework_grade_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    graded_by_user_id UUID,
    old_score NUMERIC(6, 2),
    new_score NUMERIC(6, 2),
    passed BOOLEAN,
    feedback TEXT,
    graded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_homework_grade_history_submission
        FOREIGN KEY (submission_id) REFERENCES homework_submissions(submission_id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_grade_history_user
        FOREIGN KEY (graded_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_homework_grade_history_submission
    ON homework_grade_history(submission_id, graded_at DESC);

COMMENT ON COLUMN homework_submissions.passed IS
    'TRUE khi đạt điểm. NULL = chưa chấm (đang chờ mentor). Tính lại mỗi khi mentor sửa điểm.';
COMMENT ON COLUMN homework_submissions.submission_type IS
    'Snapshot hình thức nộp tại lúc nộp: text | file | multiple_choice.';
COMMENT ON COLUMN homework_submissions.exam_attempt_id IS
    'Lượt thi thật dùng cho bài trắc nghiệm (exam_attempts). NULL với bài tự luận / nộp file.';
COMMENT ON COLUMN homework_submissions.file_public_id IS
    'public_id của file trên Cloudinary. KHÔNG lưu URL (URL có thời hạn được sinh mỗi lần xem).';
COMMENT ON COLUMN course_content.certificate_part_id IS
    'Kỹ năng (certificate_parts) mà bài tập đo lường. NULL = chưa gắn.';
COMMENT ON TABLE homework_grade_history IS
    'Lịch sử mentor chấm/sửa điểm bài tập: người sửa, điểm cũ, điểm mới, thời gian.';
