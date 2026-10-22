-- =====================================================
-- Migration V3: Dev 2 - Course & Learning Content Management
-- Bổ sung cột cho nghiệp vụ khóa học (tự học VOD).
-- CHỈ thêm cột mới, không sửa/xóa dữ liệu hay migration cũ.
-- is_preview / videoUrl / durationMinutes được lưu trong content_data (JSONB).
-- =====================================================

-- Mã khóa học (dùng để check trùng khi tạo khóa học)
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS code VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS uq_courses_code
    ON courses(code) WHERE code IS NOT NULL;

-- Điểm mục tiêu của khóa học (dùng để lọc khóa học theo targetScore)
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS target_score NUMERIC(6,2);

CREATE INDEX IF NOT EXISTS idx_courses_certificate_part
    ON courses(certificate_part_id);