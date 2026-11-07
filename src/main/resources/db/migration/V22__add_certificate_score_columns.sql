-- =====================================================
-- Migration V22: Thêm các cột lưu thang điểm trực tiếp vào bảng certificates
-- Chuẩn hoá Relational Schema (1NF), thay thế việc nhúng JSON vào cột description
-- =====================================================

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS min_score NUMERIC(6, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS max_score NUMERIC(6, 2) DEFAULT 10.00,
    ADD COLUMN IF NOT EXISTS score_step NUMERIC(6, 2) DEFAULT 0.50,
    ADD COLUMN IF NOT EXISTS score_unit VARCHAR(50) DEFAULT 'Điểm',
    ADD COLUMN IF NOT EXISTS scale_type VARCHAR(50) DEFAULT 'AVERAGE',
    ADD COLUMN IF NOT EXISTS rounding_rule VARCHAR(50);

-- Cập nhật dữ liệu cho các chứng chỉ hiện có trong database
-- 1. IELTS: 0 - 9.0 Band, bước 0.5, làm tròn IELTS
UPDATE certificates
SET min_score = 0.00,
    max_score = 9.00,
    score_step = 0.50,
    score_unit = 'Band',
    scale_type = 'AVERAGE',
    rounding_rule = 'IELTS'
WHERE UPPER(certificate_name) LIKE '%IELTS%';

-- 2. TOEIC Listening & Reading: 10 - 990 Điểm, bước 5, tổng điểm
UPDATE certificates
SET min_score = 10.00,
    max_score = 990.00,
    score_step = 5.00,
    score_unit = 'Điểm',
    scale_type = 'SUM',
    rounding_rule = 'STANDARD'
WHERE UPPER(certificate_name) LIKE '%TOEIC%'
  AND UPPER(certificate_name) NOT LIKE '%SPEAKING%'
  AND UPPER(certificate_name) NOT LIKE '%WRITING%';

-- 3. VSTEP: 0 - 10.0 Điểm, bước 0.5, làm tròn VSTEP (Bộ GD&ĐT)
UPDATE certificates
SET min_score = 0.00,
    max_score = 10.00,
    score_step = 0.50,
    score_unit = 'Điểm',
    scale_type = 'AVERAGE',
    rounding_rule = 'VSTEP'
WHERE UPPER(certificate_name) LIKE '%VSTEP%';
