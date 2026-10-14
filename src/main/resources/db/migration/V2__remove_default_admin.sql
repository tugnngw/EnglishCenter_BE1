-- =====================================================
-- Remove default admin account with hardcoded password
-- =====================================================

-- Delete the default admin user
DELETE FROM users WHERE email = 'admin@gmail.com';

-- =====================================================
-- SECURITY NOTE:
-- Admin accounts should be created via:
-- 1. Environment variables on application startup
-- 2. A secure onboarding process
-- 3. Never commit admin credentials to source control
-- =====================================================

COMMENT ON TABLE users IS 'Users - Admin accounts must be created via secure initialization, not migrations';
