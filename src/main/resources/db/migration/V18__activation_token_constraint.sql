-- =====================================================
-- V18: Noi chot chk_auth_token_user cho token kich hoat tai khoan
--   ACCOUNT_ACTIVATION gan voi user (giong REFRESH);
--   chi ACCESS_REVOKED bat buoc user_id NULL.
-- =====================================================

ALTER TABLE auth_tokens DROP CONSTRAINT chk_auth_token_user;
ALTER TABLE auth_tokens ADD CONSTRAINT chk_auth_token_user CHECK (
    (token_type IN ('REFRESH', 'ACCOUNT_ACTIVATION') AND user_id IS NOT NULL)
    OR (token_type = 'ACCESS_REVOKED' AND user_id IS NULL)
);
