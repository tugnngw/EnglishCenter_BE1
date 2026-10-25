-- =====================================================
-- Migration V7: Dev 5 x Dev 4 - Thanh toán buổi kèm 1-1 (MENTOR_SESSION) qua SePay
-- - Dùng lại bảng payment_orders của Dev 4: thêm loại đơn + booking_id, course_id cho phép null.
-- - Booking mới tạo ở trạng thái pending, chỉ confirmed sau khi webhook SePay báo đã thanh toán.
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

ALTER TABLE payment_orders
    ADD COLUMN order_type VARCHAR(30) NOT NULL DEFAULT 'COURSE'
        CHECK (order_type IN ('COURSE', 'MENTOR_SESSION'));

ALTER TABLE payment_orders
    ADD COLUMN booking_id UUID REFERENCES bookings(booking_id);

ALTER TABLE payment_orders
    ALTER COLUMN course_id DROP NOT NULL;

-- Đơn khóa học phải có course_id; đơn buổi kèm phải có booking_id
ALTER TABLE payment_orders
    ADD CONSTRAINT chk_payment_order_target CHECK (
        (order_type = 'COURSE' AND course_id IS NOT NULL AND booking_id IS NULL)
        OR (order_type = 'MENTOR_SESSION' AND booking_id IS NOT NULL AND course_id IS NULL)
    );

-- 1 booking chỉ có tối đa 1 đơn đang chờ và 1 đơn đã thanh toán
CREATE UNIQUE INDEX uq_pending_booking_order ON payment_orders(booking_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_paid_booking_order ON payment_orders(booking_id) WHERE status = 'PAID';

-- Booking mặc định chờ thanh toán
ALTER TABLE bookings ALTER COLUMN status SET DEFAULT 'pending';
