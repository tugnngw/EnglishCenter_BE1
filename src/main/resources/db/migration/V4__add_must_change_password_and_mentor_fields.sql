-- =====================================================
-- Migration V4: Add must_change_password and mentor fields
-- Purpose: Support first-login password change and mentor profile enhancement
-- Date: 2026-10-14
-- =====================================================

-- 1. Add must_change_password column to users table
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Add department and avatar_url columns to mentor_profiles table
ALTER TABLE mentor_profiles
    ADD COLUMN department VARCHAR(255),
    ADD COLUMN avatar_url VARCHAR(500);

-- 3. Create index for must_change_password to optimize queries filtering users who must change password
CREATE INDEX idx_users_must_change_password
    ON users(must_change_password)
    WHERE must_change_password = TRUE;

-- 4. Add comments for documentation
COMMENT ON COLUMN users.must_change_password IS 'Flag indicating if user must change password on next login (e.g., after admin creates mentor account)';
COMMENT ON COLUMN mentor_profiles.department IS 'Department or faculty that the mentor belongs to';
COMMENT ON COLUMN mentor_profiles.avatar_url IS 'URL to mentor profile avatar image';
