-- =====================================================
-- Migration V4: Add mentor profile fields
-- Purpose: Enhance mentor profile with department and avatar
-- Date: 2026-10-14
-- =====================================================

-- 1. Add department and avatar_url columns to mentor_profiles table
ALTER TABLE mentor_profiles
    ADD COLUMN department VARCHAR(255),
    ADD COLUMN avatar_url VARCHAR(500);

-- 2. Add comments for documentation
COMMENT ON COLUMN mentor_profiles.department IS 'Department or faculty that the mentor belongs to';
COMMENT ON COLUMN mentor_profiles.avatar_url IS 'URL to mentor profile avatar image';
