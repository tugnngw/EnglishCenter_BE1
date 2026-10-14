-- =====================================================
-- Fix v_users_full view to exclude password hash
-- =====================================================

-- Drop the old view
DROP VIEW IF EXISTS v_users_full;

-- Recreate without password column
CREATE OR REPLACE VIEW v_users_full AS
SELECT
    u.user_id,
    u.email,
    u.full_name,
    u.phone,
    u.is_active,
    u.role_id,
    r.role_name,
    u.failed_login_attempts,
    u.locked_until,
    u.created_at,
    u.last_updated,
    sp.target_certificate_id,
    sp.target_score,
    mp.mentor_created_by,
    mp.biography,
    mp.specialization,
    mp.hourly_rate,
    mp.rating_average,
    mp.mentor_registered_at
FROM users u
         JOIN roles r ON u.role_id = r.role_id
         LEFT JOIN student_profiles sp ON u.user_id = sp.user_id
         LEFT JOIN mentor_profiles mp ON u.user_id = mp.user_id;

COMMENT ON VIEW v_users_full IS 'User information view - password excluded for security';
