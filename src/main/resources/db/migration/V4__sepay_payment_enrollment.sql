CREATE SEQUENCE payment_order_code_seq MINVALUE 1000000000 MAXVALUE 9999999999 NO CYCLE;

CREATE TABLE payment_orders (
    order_id UUID PRIMARY KEY,
    order_code VARCHAR(32) NOT NULL UNIQUE,
    student_id UUID NOT NULL REFERENCES users(user_id),
    course_id UUID NOT NULL REFERENCES courses(course_id),
    course_title VARCHAR(255) NOT NULL,
    amount NUMERIC(12,0) NOT NULL CHECK (amount > 0),
    bank_code VARCHAR(50) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','PAID','CANCELLED','EXPIRED','REVIEW_REQUIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_pending_course_order ON payment_orders(student_id, course_id) WHERE status = 'PENDING';
CREATE INDEX idx_order_student_date ON payment_orders(student_id, created_at DESC);
CREATE INDEX idx_order_status_expiry ON payment_orders(status, expires_at);

ALTER TABLE payment_transactions ADD COLUMN order_id UUID REFERENCES payment_orders(order_id);
ALTER TABLE payment_transactions ADD COLUMN sepay_transaction_id BIGINT UNIQUE;
ALTER TABLE payment_transactions ADD COLUMN provider_reference VARCHAR(255);
CREATE UNIQUE INDEX uq_payment_order ON payment_transactions(order_id) WHERE order_id IS NOT NULL;

-- Preserve historical cancelled/expired enrollments; only one active entitlement is allowed.
CREATE UNIQUE INDEX uq_active_enrollment ON enrollments(student_id, course_id) WHERE status = 'active';

CREATE TABLE sepay_webhook_events (
    sepay_id BIGINT PRIMARY KEY,
    order_code VARCHAR(255),
    raw_payload TEXT NOT NULL,
    outcome VARCHAR(80) NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- The old trigger set "sent" without sending an email and replaced order_details.
CREATE OR REPLACE FUNCTION fn_create_confirmation_code()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.transaction_status = 'success' AND OLD.transaction_status IS DISTINCT FROM 'success' THEN
        NEW.confirmation_code := 'CC-' || REPLACE(NEW.transaction_id::TEXT, '-', '');
        NEW.confirmation_recipient_email := (SELECT email FROM users WHERE user_id = NEW.student_id);
        NEW.confirmation_expires_at := CURRENT_TIMESTAMP + INTERVAL '30 days';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
