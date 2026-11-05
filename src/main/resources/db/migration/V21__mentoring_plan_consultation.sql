-- =====================================================
-- Migration V21: Dev 5 - Tư vấn lộ trình + Gói kèm 1-1 theo lộ trình
-- - consultation_requests: học viên để lại SĐT nhờ mentor tư vấn (mọi mentor đều thấy, mentor nhận trước phụ trách).
-- - mentoring_plans: gói kèm 1-1 mentor tạo cho 1 học viên sau khi tư vấn (N buổi, thanh toán 1 lần cả gói).
-- - mentor_schedules.plan_id: buổi học thuộc gói nào (bỏ slot công khai, slot chỉ còn tạo trong gói).
-- - learning_paths.total_sessions: số buổi kèm 1-1 của lộ trình (VD 12 buổi).
-- - courses.entry_score: điểm đầu vào của khóa (VD khóa 4.0 - 5.0 thì entry 4.0) để gợi ý khóa vừa sức.
-- - payment_orders: thêm loại đơn MENTOR_PLAN + plan_id.
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

-- 1. Điểm đầu vào của khóa học
ALTER TABLE courses ADD COLUMN IF NOT EXISTS entry_score NUMERIC(6,2);

-- Điền sẵn cho khóa cũ có tên dạng "4.0 - 5.0" / "450-650": lấy số đầu tiên của khoảng làm điểm đầu vào
UPDATE courses
SET entry_score = CAST(substring(title FROM '([0-9]+(\.[0-9]+)?)\s*[-–]\s*[0-9]') AS NUMERIC(6,2))
WHERE entry_score IS NULL
  AND substring(title FROM '([0-9]+(\.[0-9]+)?)\s*[-–]\s*[0-9]') IS NOT NULL
  AND (target_score IS NULL
       OR CAST(substring(title FROM '([0-9]+(\.[0-9]+)?)\s*[-–]\s*[0-9]') AS NUMERIC(6,2)) < target_score);

-- 2. Số buổi kèm 1-1 của lộ trình
ALTER TABLE learning_paths ADD COLUMN IF NOT EXISTS total_sessions INT
    CHECK (total_sessions IS NULL OR total_sessions BETWEEN 1 AND 100);

-- 3. Yêu cầu tư vấn
CREATE TABLE consultation_requests (
    request_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    mentor_id    UUID REFERENCES users(user_id) ON DELETE SET NULL,
    phone        VARCHAR(20) NOT NULL,
    note         VARCHAR(1000),
    status       VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'contacted', 'agreed', 'closed')),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consultation_status ON consultation_requests(status, created_at DESC);
CREATE INDEX idx_consultation_student ON consultation_requests(student_id);
-- 1 học viên chỉ có tối đa 1 yêu cầu đang mở
CREATE UNIQUE INDEX uq_consultation_open ON consultation_requests(student_id) WHERE status IN ('pending', 'contacted');

-- 4. Gói kèm 1-1
CREATE TABLE mentoring_plans (
    plan_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mentor_id        UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    learning_path_id UUID REFERENCES learning_paths(learning_path_id) ON DELETE SET NULL,
    consultation_id  UUID REFERENCES consultation_requests(request_id) ON DELETE SET NULL,
    title            VARCHAR(255) NOT NULL,
    note             TEXT,
    total_sessions   INT NOT NULL CHECK (total_sessions > 0),
    total_price      NUMERIC(12,2) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'pending_payment'
        CHECK (status IN ('pending_payment', 'active', 'completed', 'cancelled')),
    cancel_reason    VARCHAR(500),
    paid_at          TIMESTAMP,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mentoring_plans_mentor ON mentoring_plans(mentor_id, created_at DESC);
CREATE INDEX idx_mentoring_plans_student ON mentoring_plans(student_id, created_at DESC);

ALTER TABLE mentor_schedules
    ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES mentoring_plans(plan_id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_mentor_schedules_plan ON mentor_schedules(plan_id);

-- 5. Đơn thanh toán cả gói
ALTER TABLE payment_orders ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES mentoring_plans(plan_id);

-- Bỏ các CHECK cũ liên quan order_type (tên tự sinh ở V7) rồi tạo lại có MENTOR_PLAN
DO $$
DECLARE c record;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'payment_orders'::regclass AND contype = 'c'
               AND pg_get_constraintdef(oid) LIKE '%order_type%'
    LOOP
        EXECUTE format('ALTER TABLE payment_orders DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_order_type CHECK (order_type IN ('COURSE', 'MENTOR_SESSION', 'MENTOR_PLAN'));

ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_order_target CHECK (
        (order_type = 'COURSE' AND course_id IS NOT NULL AND booking_id IS NULL AND plan_id IS NULL)
        OR (order_type = 'MENTOR_SESSION' AND booking_id IS NOT NULL AND course_id IS NULL AND plan_id IS NULL)
        OR (order_type = 'MENTOR_PLAN' AND plan_id IS NOT NULL AND course_id IS NULL AND booking_id IS NULL)
    );

-- 1 gói chỉ có tối đa 1 đơn đang chờ và 1 đơn đã thanh toán
CREATE UNIQUE INDEX uq_pending_plan_order ON payment_orders(plan_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_paid_plan_order ON payment_orders(plan_id) WHERE status = 'PAID';
