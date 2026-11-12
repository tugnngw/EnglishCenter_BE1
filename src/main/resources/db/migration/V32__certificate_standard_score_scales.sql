-- =====================================================
-- V32: Chuẩn hóa thang điểm chứng chỉ theo bộ thang chuẩn
--   IELTS : 1.0  – 9.0   band, bước 0.5
--   TOEIC : 10   – 990   điểm, bước 5 (chỉ Listening & Reading)
--   TOEFL : 0    – 120   điểm, bước 1
--   VSTEP : 0    – 10.0  điểm, bước 0.5
--
-- Cột standard_scale đánh dấu chứng chỉ đang dùng thang chuẩn nào;
-- NULL = thang tùy chỉnh của trung tâm.
-- =====================================================

ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS standard_scale VARCHAR(50);

-- 1. IELTS
UPDATE certificates
SET min_score = 1.00,
    max_score = 9.00,
    score_step = 0.50,
    score_unit = 'Band',
    scale_type = 'AVERAGE',
    rounding_rule = 'IELTS',
    standard_scale = 'IELTS'
WHERE UPPER(certificate_name) LIKE '%IELTS%';

-- Đồng bộ điểm tối thiểu xuống từng kỹ năng IELTS đang để 0
-- (thang AVERAGE yêu cầu min/max của kỹ năng khớp với chứng chỉ).
UPDATE certificate_parts p
SET properties = jsonb_set(p.properties, '{minScore}', '1.0'::jsonb, true)
FROM certificates c
WHERE p.certificate_id = c.certificate_id
  AND UPPER(c.certificate_name) LIKE '%IELTS%'
  AND jsonb_exists(p.properties, 'minScore')
  AND (p.properties ->> 'minScore') ~ '^[0-9]+(\.[0-9]+)?$'
  AND (p.properties ->> 'minScore')::numeric = 0;

-- 2. TOEIC Listening & Reading (Speaking & Writing là thang riêng 0 – 200)
UPDATE certificates
SET min_score = 10.00,
    max_score = 990.00,
    score_step = 5.00,
    score_unit = 'Điểm',
    scale_type = 'SUM',
    rounding_rule = 'STANDARD',
    standard_scale = 'TOEIC'
WHERE UPPER(certificate_name) LIKE '%TOEIC%'
  AND UPPER(certificate_name) NOT LIKE '%SPEAKING%'
  AND UPPER(certificate_name) NOT LIKE '%WRITING%'
  AND UPPER(certificate_name) NOT LIKE '%S&W%';

-- 3. TOEFL iBT thang 0 – 120 (bỏ qua định dạng mới 1 – 6)
UPDATE certificates
SET min_score = 0.00,
    max_score = 120.00,
    score_step = 1.00,
    score_unit = 'Điểm',
    scale_type = 'SUM',
    rounding_rule = 'STANDARD',
    standard_scale = 'TOEFL'
WHERE UPPER(certificate_name) LIKE '%TOEFL%'
  AND UPPER(certificate_name) NOT LIKE '%1-6%'
  AND UPPER(certificate_name) NOT LIKE '%1–6%'
  AND UPPER(certificate_name) NOT LIKE '%THANG 1%'
  AND UPPER(certificate_name) NOT LIKE '%2026%'
  AND UPPER(certificate_name) NOT LIKE '%MỚI%';

-- 4. VSTEP.3 – 5
UPDATE certificates
SET min_score = 0.00,
    max_score = 10.00,
    score_step = 0.50,
    score_unit = 'Điểm',
    scale_type = 'AVERAGE',
    rounding_rule = 'VSTEP',
    standard_scale = 'VSTEP'
WHERE UPPER(certificate_name) LIKE '%VSTEP%';
