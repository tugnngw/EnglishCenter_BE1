-- =====================================================
-- Migration V6: Dev 5 - Schedule, Booking & Notification (V4 = SePay Dev 4, V5 = Exam Dev 3)
-- - Slot của mentor vẫn là bảng mentor_schedules (V1).
-- - Thêm bảng bookings: lượt đặt slot của học viên (giữ lịch sử hủy / đổi lịch).
-- - Bổ sung cột đã đọc + liên kết booking cho notifications.
-- CHỈ thêm mới, không sửa/xóa migration cũ.
-- Giờ học (start_time, end_time), cancelled_at, created_at/read_at của notifications
-- được lưu theo giờ UTC (cột TIMESTAMP không múi giờ, giá trị là UTC).
-- =====================================================

CREATE TYPE booking_status_enum AS ENUM ('pending', 'confirmed', 'completed', 'cancelled', 'no_show');

CREATE TABLE bookings (
    booking_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id         UUID NOT NULL,
    mentor_id           UUID NOT NULL,
    student_id          UUID NOT NULL,
    status              booking_status_enum NOT NULL DEFAULT 'confirmed',
    cancel_reason       VARCHAR(500),
    cancelled_by        UUID,
    cancelled_at        TIMESTAMP,
    rescheduled_from_id UUID,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (schedule_id) REFERENCES mentor_schedules(schedule_id) ON DELETE CASCADE,
    FOREIGN KEY (mentor_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (cancelled_by) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (rescheduled_from_id) REFERENCES bookings(booking_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bookings_schedule ON bookings(schedule_id);
CREATE INDEX IF NOT EXISTS idx_bookings_student ON bookings(student_id);
CREATE INDEX IF NOT EXISTS idx_bookings_mentor ON bookings(mentor_id);

-- Thông báo trong ứng dụng: trạng thái đã đọc + booking liên quan
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS is_read BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS related_booking_id UUID;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_booking
        FOREIGN KEY (related_booking_id) REFERENCES bookings(booking_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);
