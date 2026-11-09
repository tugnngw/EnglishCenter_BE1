-- =====================================================================
-- XÓA TẤT CẢ TÀI KHOẢN, CHỈ GIỮ LẠI ADMIN  (PostgreSQL - English Center)
-- ---------------------------------------------------------------------
-- Xóa mọi user có role MENTOR / STUDENT / USER cùng dữ liệu của họ:
--   hồ sơ, token, thông báo, ghi danh, bài nộp, lượt thi, lịch học, booking,
--   yêu cầu tư vấn, gói kèm 1-1, đơn thanh toán, bài tập riêng của mentor (V27)...
-- GIỮ LẠI (chuyển người tạo sang admin): chứng chỉ, khóa học, ngân hàng câu hỏi,
--   đề thi, lộ trình, đề luyện tập, bộ từ vựng do mentor tạo.
--
-- CÁCH CHẠY AN TOÀN:
--   1. Backup DB trước (Render: Dashboard > Database > Backups, hoặc pg_dump).
--   2. Chạy thử: đổi dòng COMMIT ở cuối file thành ROLLBACK -> xem số liệu, không mất gì.
--   3. Thấy đúng thì đổi lại COMMIT và chạy thật.
-- Lỗi ở bất kỳ bước nào -> cả transaction tự rollback, DB giữ nguyên.
-- =====================================================================

BEGIN;

-- ---------- 0. Danh sách cần xóa + admin nhận lại nội dung ----------
CREATE TEMP TABLE _victims ON COMMIT DROP AS
SELECT u.user_id
FROM users u
JOIN roles r ON r.role_id = u.role_id
WHERE r.role_name <> 'ADMIN';

CREATE TEMP TABLE _admin ON COMMIT DROP AS
SELECT u.user_id
FROM users u
JOIN roles r ON r.role_id = u.role_id
WHERE r.role_name = 'ADMIN'
ORDER BY u.created_at
LIMIT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM _admin) THEN
        RAISE EXCEPTION 'Không tìm thấy tài khoản ADMIN nào -> dừng, không xóa gì';
    END IF;
END $$;

-- Xem trước: số tài khoản theo role
SELECT r.role_name,
       count(*)                                        AS total,
       count(*) FILTER (WHERE r.role_name <> 'ADMIN')  AS se_xoa
FROM users u
JOIN roles r ON r.role_id = u.role_id
GROUP BY r.role_name
ORDER BY r.role_name;

-- ---------- 1. Nội dung do mentor tạo: chuyển sang admin (FK ON DELETE RESTRICT) ----------
UPDATE certificates      SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE courses           SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE question_bank     SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE exams             SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE learning_paths    SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE practice_exams    SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE vocabulary_words  SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);
UPDATE vocabulary_sets   SET created_by_user_id = (SELECT user_id FROM _admin) WHERE created_by_user_id IN (SELECT user_id FROM _victims);

-- ---------- 2. Thanh toán (FK không cascade) ----------
-- payment_transactions có trigger chặn xóa giao dịch 'success' -> tắt tạm trong transaction này
ALTER TABLE payment_transactions DISABLE TRIGGER trg_prevent_payment_delete;

DELETE FROM payment_transactions
WHERE student_id IN (SELECT user_id FROM _victims)
   OR order_id IN (SELECT order_id FROM payment_orders WHERE student_id IN (SELECT user_id FROM _victims));

ALTER TABLE payment_transactions ENABLE TRIGGER trg_prevent_payment_delete;

-- Đơn SePay: của học viên bị xóa, hoặc trỏ tới gói 1-1 / booking sắp bị xóa theo
DELETE FROM payment_orders
WHERE student_id IN (SELECT user_id FROM _victims)
   OR plan_id IN (SELECT plan_id FROM mentoring_plans
                  WHERE mentor_id IN (SELECT user_id FROM _victims)
                     OR student_id IN (SELECT user_id FROM _victims))
   OR booking_id IN (SELECT booking_id FROM bookings
                     WHERE mentor_id IN (SELECT user_id FROM _victims)
                        OR student_id IN (SELECT user_id FROM _victims));

-- ---------- 3. Tiến độ lộ trình (JSONB, không có FK): bỏ học viên bị xóa ----------
UPDATE learning_paths lp
SET student_progress = COALESCE((
        SELECT jsonb_agg(e)
        FROM jsonb_array_elements(lp.student_progress) e
        WHERE (e ->> 'studentId')::uuid NOT IN (SELECT user_id FROM _victims)
    ), '[]'::jsonb)
WHERE lp.student_progress IS NOT NULL
  AND jsonb_array_length(lp.student_progress) > 0;

-- ---------- 4. Xóa tài khoản: các bảng còn lại tự CASCADE / SET NULL ----------
DELETE FROM users WHERE user_id IN (SELECT user_id FROM _victims);

-- ---------- 5. Lộ trình: bỏ chặng HOMEWORK trỏ tới bài tập mentor vừa bị xóa theo (V27) ----------
UPDATE learning_paths lp
SET items = COALESCE((
        SELECT jsonb_agg(e ORDER BY (e ->> 'order')::int)
        FROM jsonb_array_elements(lp.items) e
        WHERE e ->> 'type' <> 'HOMEWORK'
           OR EXISTS (SELECT 1 FROM course_content c WHERE c.content_id = (e ->> 'refId')::uuid)
    ), '[]'::jsonb)
WHERE lp.items @> '[{"type":"HOMEWORK"}]'::jsonb;

-- ---------- 6. Kết quả ----------
SELECT r.role_name, count(*) AS con_lai
FROM users u
JOIN roles r ON r.role_id = u.role_id
GROUP BY r.role_name;

-- Chạy thử: đổi COMMIT thành ROLLBACK
COMMIT;
