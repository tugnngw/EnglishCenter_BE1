-- =====================================================
-- V17: Them loai token ACCOUNT_ACTIVATION (luong user dang ky qua email)
--   PostgreSQL khong cho dung gia tri enum moi trong cung transaction
--   ma no duoc them vao, nen tach rieng; V18 nui rang buoc.
-- =====================================================

ALTER TYPE auth_token_type_enum ADD VALUE IF NOT EXISTS 'ACCOUNT_ACTIVATION';
