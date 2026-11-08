-- =====================================================
-- Migration V25: Thêm certificate_id và certificate_score vào bảng mentor_profiles
-- Chuẩn hoá liên kết giữa Mentor và Chứng chỉ giảng dạy
-- =====================================================

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS certificate_id UUID,
    ADD COLUMN IF NOT EXISTS certificate_score NUMERIC(6, 2);

-- Khóa ngoại tới bảng certificates
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_mentor_profiles_certificate'
    ) THEN
        ALTER TABLE mentor_profiles
            ADD CONSTRAINT fk_mentor_profiles_certificate
            FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_mentor_profiles_certificate_id ON mentor_profiles(certificate_id);
