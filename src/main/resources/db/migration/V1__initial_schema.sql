-- =====================================================
-- DATABASE SCHEMA - English Learning Platform
-- Version: 12.0 (All IDs converted to UUID)
-- Database: PostgreSQL
-- Total Tables: 24
--   - 20 main tables
--   -  1 auth table (auth_tokens)
--   -  1 audit table (payment_audit)
--   -  2 profile tables (student_profiles, mentor_profiles)
-- =====================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =====================================================
-- ENUM TYPES
-- =====================================================
CREATE TYPE course_status_enum AS ENUM ('draft', 'active', 'archived');
CREATE TYPE content_type_enum AS ENUM ('lesson', 'vocabulary', 'homework');
CREATE TYPE question_type_enum AS ENUM ('multiple_choice', 'fill_blank', 'essay', 'matching', 'true_false');
CREATE TYPE difficulty_level_enum AS ENUM ('easy', 'medium', 'hard', 'real_test_level');
CREATE TYPE exam_status_enum AS ENUM ('draft', 'published', 'archived');
CREATE TYPE exam_type_enum AS ENUM ('quiz', 'midterm', 'final', 'placement_test');
CREATE TYPE exam_scope_enum AS ENUM ('trial', 'course');
CREATE TYPE attempt_status_enum AS ENUM ('in_progress', 'submitted', 'graded', 'expired');
CREATE TYPE transaction_type_enum AS ENUM ('course_purchase', 'mentor_session', 'trial_exam_access');
CREATE TYPE transaction_status_enum AS ENUM ('pending', 'success', 'failed');
CREATE TYPE email_status_enum AS ENUM ('pending', 'sent', 'failed');
CREATE TYPE enrollment_status_enum AS ENUM ('active', 'expired', 'cancelled');
CREATE TYPE payment_action_enum AS ENUM ('CREATE', 'UPDATE_ATTEMPT', 'DELETE_ATTEMPT');
CREATE TYPE schedule_status_enum AS ENUM ('available', 'booked', 'completed', 'cancelled');
CREATE TYPE path_status_enum AS ENUM ('active', 'completed', 'abandoned');
CREATE TYPE repeat_rule_enum AS ENUM ('none', 'daily', 'weekly');
CREATE TYPE study_status_enum AS ENUM ('pending', 'done', 'skipped', 'cancelled');
CREATE TYPE notification_type_enum AS ENUM ('study_reminder', 'homework_deadline', 'payment', 'exam_result', 'mentor_session', 'general');
CREATE TYPE auth_token_type_enum AS ENUM ('REFRESH', 'ACCESS_REVOKED');

-- =====================================================
-- PART 1: USER MANAGEMENT (4 TABLES)
-- =====================================================

-- 1. ROLES
CREATE TABLE roles (
                       role_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       role_name VARCHAR(50) UNIQUE NOT NULL,
                       description VARCHAR(255)
);

-- 2. USERS (CHỈ cột chung cho mọi role)
CREATE TABLE users (
                       user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password VARCHAR(255) NOT NULL,
                       full_name VARCHAR(255),
                       phone VARCHAR(20),
                       is_active BOOLEAN DEFAULT TRUE,
                       role_id UUID NOT NULL,
                       failed_login_attempts INT NOT NULL DEFAULT 0,
                       locked_until TIMESTAMP,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                       FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE RESTRICT
);

-- 3. STUDENT_PROFILES (CHỈ student dùng)
CREATE TABLE student_profiles (
                                  user_id UUID PRIMARY KEY,
                                  target_certificate_id UUID,
                                  target_score NUMERIC(6,2),
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 4. MENTOR_PROFILES (CHỈ mentor dùng)
CREATE TABLE mentor_profiles (
                                 user_id UUID PRIMARY KEY,
                                 mentor_created_by UUID,
                                 biography TEXT,
                                 specialization VARCHAR(255),
                                 department VARCHAR(255),
                                 avatar_url VARCHAR(500),
                                 hourly_rate NUMERIC(12,2),
                                 rating_average NUMERIC(3,2) DEFAULT 0.00,
                                 mentor_registered_at TIMESTAMP,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                 FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                 FOREIGN KEY (mentor_created_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- =====================================================
-- PART 2: AUTH (1 TABLE)
-- =====================================================

-- 5. AUTH_TOKENS (gộp refresh_tokens + revoked_access_tokens)
CREATE TABLE auth_tokens (
                             token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             token_hash VARCHAR(64) UNIQUE NOT NULL,
                             token_type auth_token_type_enum NOT NULL,
                             user_id UUID,
                             expires_at TIMESTAMP NOT NULL,
                             revoked BOOLEAN NOT NULL DEFAULT FALSE,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                             CONSTRAINT chk_auth_token_user CHECK (
                                 (token_type = 'REFRESH' AND user_id IS NOT NULL) OR
                                 (token_type = 'ACCESS_REVOKED' AND user_id IS NULL)
                                 )
);

-- =====================================================
-- PART 3: CERTIFICATE MANAGEMENT (2 TABLES)
-- =====================================================

-- 6. CERTIFICATES
CREATE TABLE certificates (
                              certificate_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              created_by_user_id UUID NOT NULL,
                              certificate_name VARCHAR(255) NOT NULL,
                              description TEXT,
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- 7. CERTIFICATE_PARTS
CREATE TABLE certificate_parts (
                                   part_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   certificate_id UUID NOT NULL,
                                   part_name VARCHAR(255) NOT NULL,
                                   part_description TEXT,
                                   part_order INT NOT NULL,
                                   properties JSONB DEFAULT '[]'::jsonb,
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                   FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE CASCADE
);

-- FK student_profiles.target_certificate_id → certificates
ALTER TABLE student_profiles
    ADD CONSTRAINT fk_student_target_certificate
        FOREIGN KEY (target_certificate_id) REFERENCES certificates(certificate_id) ON DELETE SET NULL;

-- =====================================================
-- PART 4: COURSE MANAGEMENT (3 TABLES)
-- =====================================================

-- 8. COURSES
CREATE TABLE courses (
                         course_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         mentor_id UUID,
                         created_by_user_id UUID NOT NULL,
                         certificate_id UUID,
                         certificate_part_id UUID,
                         title VARCHAR(255) NOT NULL,
                         description TEXT,
                         level VARCHAR(100),
                         price NUMERIC(12,2),
                         thumbnail_url VARCHAR(500),
                         status course_status_enum DEFAULT 'draft',
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (mentor_id) REFERENCES users(user_id) ON DELETE SET NULL,
                         FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
                         FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE SET NULL,
                         FOREIGN KEY (certificate_part_id) REFERENCES certificate_parts(part_id) ON DELETE SET NULL
);

-- 9. COURSE_CONTENT (gộp lesson + vocabulary + homework)
CREATE TABLE course_content (
                                content_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                course_id UUID NOT NULL,
                                content_type content_type_enum NOT NULL,
                                parent_content_id UUID,
                                title VARCHAR(255),
                                description TEXT,
                                content_order INT DEFAULT 0,
                                content_data JSONB DEFAULT '{}'::jsonb,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
                                FOREIGN KEY (parent_content_id) REFERENCES course_content(content_id) ON DELETE CASCADE
);

-- 10. HOMEWORK_SUBMISSIONS
CREATE TABLE homework_submissions (
                                      submission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      homework_content_id UUID NOT NULL,
                                      student_id UUID NOT NULL,
                                      answer_content TEXT,
                                      file_url VARCHAR(500),
                                      score NUMERIC(6,2),
                                      feedback TEXT,
                                      submitted_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      graded_date TIMESTAMP,
                                      FOREIGN KEY (homework_content_id) REFERENCES course_content(content_id) ON DELETE CASCADE,
                                      FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- =====================================================
-- PART 5: EXAM SYSTEM (6 TABLES)
-- =====================================================

-- 11. QUESTION_BANK
CREATE TABLE question_bank (
                               question_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               created_by_user_id UUID NOT NULL,
                               certificate_id UUID,
                               certificate_part_id UUID,
                               question_type question_type_enum NOT NULL,
                               question_text TEXT NOT NULL,
                               difficulty_level difficulty_level_enum DEFAULT 'medium',
                               topic VARCHAR(255),
                               points NUMERIC(6,2) DEFAULT 1.00,
                               usage_count INT DEFAULT 0,
                               correct_rate NUMERIC(5,2) DEFAULT 0.00,
                               last_used_date TIMESTAMP,
                               explanation TEXT,
                               audio_url VARCHAR(500),
                               image_url VARCHAR(500),
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
                               FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE SET NULL,
                               FOREIGN KEY (certificate_part_id) REFERENCES certificate_parts(part_id) ON DELETE SET NULL
);

-- 12. QUESTION_BANK_OPTIONS
CREATE TABLE question_bank_options (
                                       option_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       question_id UUID NOT NULL,
                                       option_text TEXT NOT NULL,
                                       is_correct BOOLEAN DEFAULT FALSE,
                                       option_order INT NOT NULL,
                                       explanation TEXT,
                                       FOREIGN KEY (question_id) REFERENCES question_bank(question_id) ON DELETE CASCADE
);

-- 13. EXAMS
CREATE TABLE exams (
                       exam_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       exam_scope exam_scope_enum NOT NULL,
                       created_by_user_id UUID NOT NULL,
                       certificate_id UUID,
                       exam_code VARCHAR(50) UNIQUE,
                       course_id UUID,
                       exam_type exam_type_enum,
                       exam_title VARCHAR(255) NOT NULL,
                       description TEXT,
                       duration_minutes INT NOT NULL,
                       total_questions INT DEFAULT 0,
                       passing_score NUMERIC(6,2) DEFAULT 60.00,
                       max_attempts INT DEFAULT 1,
                       show_answers BOOLEAN DEFAULT TRUE,
                       show_score BOOLEAN DEFAULT TRUE,
                       is_free BOOLEAN DEFAULT TRUE,
                       price NUMERIC(12,2) DEFAULT 0.00,
                       difficulty_level difficulty_level_enum DEFAULT 'medium',
                       recommended_level VARCHAR(100),
                       total_parts INT DEFAULT 0,
                       total_attempts INT DEFAULT 0,
                       average_score NUMERIC(6,2) DEFAULT 0.00,
                       available_from TIMESTAMP,
                       available_until TIMESTAMP,
                       published_date TIMESTAMP,
                       sections JSONB DEFAULT '[]'::jsonb,
                       status exam_status_enum DEFAULT 'draft',
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
                       FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE CASCADE,
                       FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
                       CONSTRAINT chk_exam_scope CHECK (
                           (exam_scope = 'trial' AND certificate_id IS NOT NULL) OR
                           (exam_scope = 'course' AND course_id IS NOT NULL)
                           )
);

-- 14. EXAM_QUESTIONS
CREATE TABLE exam_questions (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                exam_id UUID NOT NULL,
                                section_id UUID,
                                question_bank_id UUID NOT NULL,
                                question_order INT NOT NULL,
                                FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE,
                                FOREIGN KEY (question_bank_id) REFERENCES question_bank(question_id) ON DELETE RESTRICT
);

-- 15. EXAM_ATTEMPTS
CREATE TABLE exam_attempts (
                               attempt_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               exam_id UUID NOT NULL,
                               student_id UUID NOT NULL,
                               payment_transaction_id UUID,
                               attempt_number INT DEFAULT 1,
                               start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               end_time TIMESTAMP,
                               score NUMERIC(6,2) DEFAULT 0.00,
                               band_score NUMERIC(3,1),
                               scaled_score INT,
                               passed BOOLEAN DEFAULT FALSE,
                               status attempt_status_enum DEFAULT 'in_progress',
                               time_spent_seconds INT DEFAULT 0,
                               correct_count INT DEFAULT 0,
                               incorrect_count INT DEFAULT 0,
                               unanswered_count INT DEFAULT 0,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE CASCADE,
                               FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 16. EXAM_ATTEMPT_DETAILS
CREATE TABLE exam_attempt_details (
                                      detail_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      attempt_id UUID NOT NULL,
                                      question_bank_id UUID NOT NULL,
                                      selected_option_id UUID,
                                      answer_text TEXT,
                                      is_correct BOOLEAN DEFAULT FALSE,
                                      time_spent_seconds INT DEFAULT 0,
                                      answered_date TIMESTAMP,
                                      manual_score NUMERIC(6,2),
                                      mentor_feedback TEXT,
                                      graded_by UUID,
                                      graded_date TIMESTAMP,
                                      FOREIGN KEY (attempt_id) REFERENCES exam_attempts(attempt_id) ON DELETE CASCADE,
                                      FOREIGN KEY (question_bank_id) REFERENCES question_bank(question_id) ON DELETE RESTRICT,
                                      FOREIGN KEY (selected_option_id) REFERENCES question_bank_options(option_id) ON DELETE SET NULL,
                                      FOREIGN KEY (graded_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- =====================================================
-- PART 6: PAYMENT SYSTEM (3 TABLES)
-- =====================================================

-- 17. PAYMENT_TRANSACTIONS
CREATE TABLE payment_transactions (
                                      transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      student_id UUID NOT NULL,
                                      transaction_type transaction_type_enum NOT NULL,
                                      course_id UUID,
                                      mentor_schedule_id UUID,
                                      exam_id UUID,
                                      amount NUMERIC(12,2) NOT NULL,
                                      payment_method VARCHAR(50),
                                      transaction_status transaction_status_enum DEFAULT 'pending',
                                      payment_date TIMESTAMP,
                                      confirmation_code VARCHAR(50) UNIQUE,
                                      confirmation_code_sent BOOLEAN DEFAULT FALSE,
                                      confirmation_code_sent_at TIMESTAMP,
                                      confirmation_recipient_email VARCHAR(255),
                                      confirmation_email_status email_status_enum DEFAULT 'pending',
                                      confirmation_expires_at TIMESTAMP,
                                      order_details JSONB,
                                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                      FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE RESTRICT,
                                      FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE SET NULL,
                                      FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE SET NULL
);

-- FK exam_attempts → payment_transactions
ALTER TABLE exam_attempts
    ADD CONSTRAINT fk_attempt_payment
        FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(transaction_id) ON DELETE SET NULL;

-- 18. PAYMENT_AUDIT
CREATE TABLE payment_audit (
                               audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               transaction_id UUID NOT NULL,
                               action_type payment_action_enum NOT NULL,
                               old_status VARCHAR(50),
                               new_status VARCHAR(50),
                               old_amount NUMERIC(12,2),
                               new_amount NUMERIC(12,2),
                               attempted_by UUID,
                               attempt_reason TEXT,
                               blocked BOOLEAN DEFAULT FALSE,
                               error_message TEXT,
                               ip_address VARCHAR(45),
                               user_agent VARCHAR(500),
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (transaction_id) REFERENCES payment_transactions(transaction_id) ON DELETE CASCADE,
                               FOREIGN KEY (attempted_by) REFERENCES users(user_id) ON DELETE SET NULL
);

-- 19. ENROLLMENTS
CREATE TABLE enrollments (
                             enrollment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             student_id UUID NOT NULL,
                             course_id UUID NOT NULL,
                             payment_transaction_id UUID,
                             enrolled_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             status enrollment_status_enum DEFAULT 'active',
                             FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
                             FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
                             FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(transaction_id) ON DELETE SET NULL
);

-- =====================================================
-- PART 7: SCHEDULES (2 TABLES)
-- =====================================================

-- 20. MENTOR_SCHEDULES
CREATE TABLE mentor_schedules (
                                  schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  mentor_id UUID NOT NULL,
                                  student_id UUID,
                                  start_time TIMESTAMP NOT NULL,
                                  end_time TIMESTAMP NOT NULL,
                                  status schedule_status_enum DEFAULT 'available',
                                  price NUMERIC(12,2),
                                  meeting_url VARCHAR(500),
                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                  FOREIGN KEY (mentor_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                  FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- FK payment_transactions → mentor_schedules
ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_payment_mentor_schedule
        FOREIGN KEY (mentor_schedule_id) REFERENCES mentor_schedules(schedule_id) ON DELETE SET NULL;

-- 21. STUDY_SCHEDULES
CREATE TABLE study_schedules (
                                 schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 student_id UUID NOT NULL,
                                 lesson_content_id UUID,
                                 title VARCHAR(255) NOT NULL,
                                 scheduled_time TIMESTAMP NOT NULL,
                                 repeat_rule repeat_rule_enum DEFAULT 'none',
                                 reminder_minutes_before INT DEFAULT 15,
                                 status study_status_enum DEFAULT 'pending',
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 FOREIGN KEY (student_id) REFERENCES users(user_id) ON DELETE CASCADE,
                                 FOREIGN KEY (lesson_content_id) REFERENCES course_content(content_id) ON DELETE SET NULL
);

-- =====================================================
-- PART 8: LEARNING PATH (1 TABLE)
-- =====================================================

-- 22. LEARNING_PATHS
CREATE TABLE learning_paths (
                                learning_path_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                certificate_id UUID NOT NULL,
                                created_by_user_id UUID NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                description TEXT,
                                items JSONB DEFAULT '[]'::jsonb,
                                student_progress JSONB DEFAULT '[]'::jsonb,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (certificate_id) REFERENCES certificates(certificate_id) ON DELETE CASCADE,
                                FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- =====================================================
-- PART 9: NOTIFICATION (1 TABLE)
-- =====================================================

-- 23. NOTIFICATIONS
CREATE TABLE notifications (
                               notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id UUID NOT NULL,
                               type notification_type_enum NOT NULL,
                               related_mentor_schedule_id UUID,
                               related_study_schedule_id UUID,
                               title VARCHAR(255) NOT NULL,
                               message TEXT,
                               recipient_email VARCHAR(255) NOT NULL,
                               email_status email_status_enum DEFAULT 'pending',
                               scheduled_at TIMESTAMP,
                               sent_at TIMESTAMP,
                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                               FOREIGN KEY (related_mentor_schedule_id) REFERENCES mentor_schedules(schedule_id) ON DELETE SET NULL,
                               FOREIGN KEY (related_study_schedule_id) REFERENCES study_schedules(schedule_id) ON DELETE SET NULL
);

-- =====================================================
-- PART 10: PRACTICE EXAM - AI (1 TABLE)
-- =====================================================

-- 24. PRACTICE_EXAMS
CREATE TABLE practice_exams (
                                practice_exam_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                practice_exam_bank_id UUID,
                                created_by_user_id UUID,
                                course_id UUID,
                                bank_name VARCHAR(255),
                                title VARCHAR(255) NOT NULL,
                                description TEXT,
                                exam_type VARCHAR(50) NOT NULL,
                                answers JSONB DEFAULT '[]'::jsonb,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT,
                                FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE SET NULL,
                                FOREIGN KEY (practice_exam_bank_id) REFERENCES practice_exams(practice_exam_id) ON DELETE CASCADE
);

-- =====================================================
-- INDEXES
-- =====================================================

-- Users
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(is_active);
CREATE INDEX idx_users_role ON users(role_id);

-- Student profiles
CREATE INDEX idx_student_profiles_certificate ON student_profiles(target_certificate_id);

-- Mentor profiles
CREATE INDEX idx_mentor_profiles_created_by ON mentor_profiles(mentor_created_by);
CREATE INDEX idx_mentor_profiles_registered ON mentor_profiles(mentor_registered_at) WHERE mentor_registered_at IS NOT NULL;
CREATE INDEX idx_mentor_profiles_specialization ON mentor_profiles(specialization);

-- Auth
CREATE INDEX idx_auth_tokens_token_hash ON auth_tokens(token_hash);
CREATE INDEX idx_auth_tokens_user_id ON auth_tokens(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_auth_tokens_expires_at ON auth_tokens(expires_at);
CREATE INDEX idx_auth_tokens_type ON auth_tokens(token_type);

-- Courses
CREATE INDEX idx_courses_status ON courses(status);
CREATE INDEX idx_courses_mentor_status ON courses(mentor_id, status);
CREATE INDEX idx_courses_certificate ON courses(certificate_id);

-- Course content
CREATE INDEX idx_course_content_course ON course_content(course_id);
CREATE INDEX idx_course_content_type ON course_content(content_type);
CREATE INDEX idx_course_content_parent ON course_content(parent_content_id);
CREATE INDEX idx_course_content_order ON course_content(course_id, content_order);

-- Homework submissions
CREATE INDEX idx_homework_sub_student ON homework_submissions(student_id);
CREATE INDEX idx_homework_sub_homework ON homework_submissions(homework_content_id);

-- Question bank
CREATE INDEX idx_question_bank_type ON question_bank(question_type);
CREATE INDEX idx_question_bank_difficulty ON question_bank(difficulty_level);
CREATE INDEX idx_question_bank_certificate ON question_bank(certificate_id);
CREATE INDEX idx_question_bank_part ON question_bank(certificate_part_id);
CREATE INDEX idx_question_bank_usage ON question_bank(usage_count DESC);
CREATE INDEX idx_question_text_fulltext ON question_bank USING gin(to_tsvector('english', question_text));

-- Exams
CREATE INDEX idx_exams_scope_status ON exams(exam_scope, status);
CREATE INDEX idx_exams_certificate ON exams(certificate_id);
CREATE INDEX idx_exams_course ON exams(course_id);
CREATE INDEX idx_exams_code ON exams(exam_code);

-- Exam questions
CREATE INDEX idx_exam_questions_exam ON exam_questions(exam_id);

-- Exam attempts
CREATE INDEX idx_exam_attempts_student ON exam_attempts(student_id);
CREATE INDEX idx_exam_attempts_exam ON exam_attempts(exam_id);

-- Payments
CREATE INDEX idx_payment_student ON payment_transactions(student_id);
CREATE INDEX idx_payment_status ON payment_transactions(transaction_status);
CREATE INDEX idx_payment_date ON payment_transactions(payment_date);
CREATE INDEX idx_payment_type ON payment_transactions(transaction_type);

-- Enrollments
CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
CREATE INDEX idx_enrollments_student_status ON enrollments(student_id, status);

-- Mentor schedules
CREATE INDEX idx_mentor_schedules_mentor ON mentor_schedules(mentor_id);
CREATE INDEX idx_mentor_schedules_status ON mentor_schedules(status);
CREATE INDEX idx_mentor_schedules_time ON mentor_schedules(start_time, end_time);

-- Study schedules
CREATE INDEX idx_study_schedules_student ON study_schedules(student_id);
CREATE INDEX idx_study_schedules_time ON study_schedules(scheduled_time);

-- Notifications
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(email_status);
CREATE INDEX idx_notifications_scheduled ON notifications(scheduled_at);

-- Learning paths
CREATE INDEX idx_learning_paths_certificate ON learning_paths(certificate_id);

-- Practice exams
CREATE INDEX idx_practice_exams_course ON practice_exams(course_id);
CREATE INDEX idx_practice_exams_bank ON practice_exams(practice_exam_bank_id);

-- =====================================================
-- TRIGGERS
-- =====================================================

-- Trigger 1: Prevent non-admin from creating MENTOR account
CREATE OR REPLACE FUNCTION fn_check_mentor_insert()
    RETURNS TRIGGER AS $$
DECLARE
    v_role_name VARCHAR(50);
    v_current_admin_id UUID;
BEGIN
    SELECT role_name INTO v_role_name FROM roles WHERE role_id = NEW.role_id;

    IF v_role_name = 'MENTOR' THEN
        v_current_admin_id := current_setting('app.current_user_id', TRUE)::UUID;

        IF v_current_admin_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM users u
                              JOIN roles r ON u.role_id = r.role_id
            WHERE u.user_id = v_current_admin_id AND r.role_name = 'ADMIN'
        ) THEN
            RAISE EXCEPTION 'Only admins can create mentor accounts';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_mentor_insert_admin_only
    BEFORE INSERT ON users
    FOR EACH ROW
EXECUTE FUNCTION fn_check_mentor_insert();

-- Trigger 2: Prevent non-admin from changing role to MENTOR
CREATE OR REPLACE FUNCTION fn_check_mentor_update()
    RETURNS TRIGGER AS $$
DECLARE
    v_new_role_name VARCHAR(50);
    v_current_admin_id UUID;
BEGIN
    IF NEW.role_id != OLD.role_id THEN
        SELECT role_name INTO v_new_role_name FROM roles WHERE role_id = NEW.role_id;

        IF v_new_role_name = 'MENTOR' THEN
            v_current_admin_id := current_setting('app.current_user_id', TRUE)::UUID;

            IF v_current_admin_id IS NULL OR NOT EXISTS (
                SELECT 1 FROM users u
                                  JOIN roles r ON u.role_id = r.role_id
                WHERE u.user_id = v_current_admin_id AND r.role_name = 'ADMIN'
            ) THEN
                RAISE EXCEPTION 'Only admins can assign mentor role';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_mentor_update_admin_only
    BEFORE UPDATE ON users
    FOR EACH ROW
EXECUTE FUNCTION fn_check_mentor_update();

-- Trigger 3: Prevent non-admin from creating courses
CREATE OR REPLACE FUNCTION fn_check_course_creator()
    RETURNS TRIGGER AS $$
DECLARE
    v_creator_role VARCHAR(50);
BEGIN
    SELECT r.role_name INTO v_creator_role
    FROM users u
             JOIN roles r ON u.role_id = r.role_id
    WHERE u.user_id = NEW.created_by_user_id AND r.role_name = 'ADMIN'
    LIMIT 1;

    IF v_creator_role IS NULL THEN
        RAISE EXCEPTION 'Only admins can create courses';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_courses_admin_only
    BEFORE INSERT ON courses
    FOR EACH ROW
EXECUTE FUNCTION fn_check_course_creator();

-- Trigger 4: Only admins can create trial exams
CREATE OR REPLACE FUNCTION fn_check_trial_exam_creator()
    RETURNS TRIGGER AS $$
DECLARE
    v_creator_role VARCHAR(50);
BEGIN
    IF NEW.exam_scope = 'trial' THEN
        SELECT r.role_name INTO v_creator_role
        FROM users u
                 JOIN roles r ON u.role_id = r.role_id
        WHERE u.user_id = NEW.created_by_user_id AND r.role_name = 'ADMIN'
        LIMIT 1;

        IF v_creator_role IS NULL THEN
            RAISE EXCEPTION 'Only admins can create trial exams';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_exams_trial_admin_only
    BEFORE INSERT ON exams
    FOR EACH ROW
EXECUTE FUNCTION fn_check_trial_exam_creator();

-- Trigger 5: Prevent payment rollback
CREATE OR REPLACE FUNCTION fn_prevent_payment_rollback()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.transaction_status = 'success' AND NEW.transaction_status != 'success' THEN
        INSERT INTO payment_audit (
            transaction_id, action_type, old_status, new_status,
            old_amount, new_amount, blocked, error_message
        ) VALUES (
                     OLD.transaction_id, 'UPDATE_ATTEMPT',
                     OLD.transaction_status::VARCHAR, NEW.transaction_status::VARCHAR,
                     OLD.amount, NEW.amount, TRUE,
                     'Rollback blocked: Cannot change status from success'
                 );
        RAISE EXCEPTION 'Cannot rollback successful payment transaction';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_payment_rollback
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION fn_prevent_payment_rollback();

-- Trigger 6: Prevent deleting successful payments
CREATE OR REPLACE FUNCTION fn_prevent_payment_delete()
    RETURNS TRIGGER AS $$
BEGIN
    IF OLD.transaction_status = 'success' THEN
        INSERT INTO payment_audit (
            transaction_id, action_type, old_status, blocked, error_message
        ) VALUES (
                     OLD.transaction_id, 'DELETE_ATTEMPT', OLD.transaction_status::VARCHAR,
                     TRUE, 'Delete blocked: Cannot delete successful payment'
                 );
        RAISE EXCEPTION 'Cannot delete successful payment transaction';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_payment_delete
    BEFORE DELETE ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION fn_prevent_payment_delete();

-- Trigger 7: Auto-create confirmation code on payment success
CREATE OR REPLACE FUNCTION fn_create_confirmation_code()
    RETURNS TRIGGER AS $$
DECLARE
    v_code VARCHAR(50);
BEGIN
    IF NEW.transaction_status = 'success'
        AND (OLD.transaction_status IS NULL OR OLD.transaction_status != 'success') THEN

        v_code := 'CC-' || TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDD') || '-' ||
                  UPPER(SUBSTRING(REPLACE(NEW.transaction_id::TEXT, '-', '') FROM 1 FOR 6)) || '-' ||
                  UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 6));

        NEW.confirmation_code := v_code;
        NEW.confirmation_code_sent := TRUE;
        NEW.confirmation_code_sent_at := CURRENT_TIMESTAMP;
        NEW.confirmation_recipient_email :=
                (SELECT email FROM users WHERE user_id = NEW.student_id);
        NEW.confirmation_email_status := 'pending';
        NEW.confirmation_expires_at := CURRENT_TIMESTAMP + INTERVAL '30 days';
        NEW.order_details := jsonb_build_object(
                'transaction_id', NEW.transaction_id,
                'amount', NEW.amount,
                'payment_date', NEW.payment_date
                             );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_create_confirmation_code
    BEFORE UPDATE ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION fn_create_confirmation_code();

-- Trigger 8: Track question usage when added to exam
CREATE OR REPLACE FUNCTION fn_track_question_usage()
    RETURNS TRIGGER AS $$
BEGIN
    UPDATE question_bank
    SET usage_count = usage_count + 1,
        last_used_date = CURRENT_TIMESTAMP
    WHERE question_id = NEW.question_bank_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_question_usage
    AFTER INSERT ON exam_questions
    FOR EACH ROW
EXECUTE FUNCTION fn_track_question_usage();

-- =====================================================
-- STORED PROCEDURES
-- =====================================================

CREATE OR REPLACE FUNCTION sp_check_mentor_course_permission(
    p_mentor_id UUID,
    p_course_id UUID
)
    RETURNS BOOLEAN AS $$
DECLARE
    v_has_permission BOOLEAN;
BEGIN
    SELECT EXISTS(
        SELECT 1
        FROM courses
        WHERE course_id = p_course_id AND mentor_id = p_mentor_id
    ) INTO v_has_permission;

    RETURN v_has_permission;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- VIEWS
-- =====================================================

CREATE OR REPLACE VIEW v_available_courses AS
SELECT
    c.course_id,
    c.title,
    c.description,
    c.level,
    c.price,
    c.thumbnail_url,
    u.full_name AS mentor_name,
    cp.part_name AS certificate_part,
    cert.certificate_name
FROM courses c
         LEFT JOIN users u ON c.mentor_id = u.user_id
         LEFT JOIN certificate_parts cp ON c.certificate_part_id = cp.part_id
         LEFT JOIN certificates cert ON c.certificate_id = cert.certificate_id
WHERE c.status = 'active';

CREATE OR REPLACE VIEW v_available_trial_exams AS
SELECT
    e.exam_id,
    e.exam_code,
    e.exam_title,
    e.description,
    e.duration_minutes,
    e.is_free,
    e.price,
    e.difficulty_level,
    e.total_questions,
    e.average_score,
    c.certificate_name
FROM exams e
         JOIN certificates c ON e.certificate_id = c.certificate_id
WHERE e.exam_scope = 'trial' AND e.status = 'published';

CREATE OR REPLACE VIEW v_locked_payments AS
SELECT
    pt.transaction_id,
    pt.student_id,
    u.email AS student_email,
    pt.transaction_type,
    pt.amount,
    pt.payment_date,
    pt.confirmation_code
FROM payment_transactions pt
         JOIN users u ON pt.student_id = u.user_id
WHERE pt.transaction_status = 'success';

-- View tổng hợp user + profile (tiện query) - password excluded for security
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
    mp.department,
    mp.avatar_url,
    mp.hourly_rate,
    mp.rating_average,
    mp.mentor_registered_at
FROM users u
         JOIN roles r ON u.role_id = r.role_id
         LEFT JOIN student_profiles sp ON u.user_id = sp.user_id
         LEFT JOIN mentor_profiles mp ON u.user_id = mp.user_id;

-- =====================================================
-- SEED DATA
-- =====================================================

-- 1. Roles (3 role cố định, UPPERCASE - chuẩn Spring Security)
INSERT INTO roles (role_name, description) VALUES
                                               ('ADMIN', 'System administrator - 1 tài khoản duy nhất'),
                                               ('MENTOR', 'Instructor - do admin cấp tài khoản'),
                                               ('STUDENT', 'Learner - tự đăng ký trên web');

-- 2. Seed 1 tài khoản admin duy nhất
-- Password: Admin@123 (BCrypt hash, cost 10)
-- ⚠️ ĐỔI PASSWORD NGAY SAU KHI DEPLOY
INSERT INTO users (
    email, password, full_name, phone, is_active, role_id
) VALUES (
             'admin@gmail.com',
             '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
             'System Administrator',
             '0900000000',
             TRUE,
             (SELECT role_id FROM roles WHERE role_name = 'ADMIN')
         );

-- =====================================================
-- END OF MIGRATION v12.0
-- Total: 24 tables (All IDs are UUID)
--   - 20 main
--   -  1 auth (auth_tokens)
--   -  1 audit (payment_audit)
--   -  2 profiles (student_profiles, mentor_profiles)
-- =====================================================

COMMENT ON TABLE users IS 'Users - identity only (all roles)';
COMMENT ON TABLE student_profiles IS 'Student-specific profile fields';
COMMENT ON TABLE mentor_profiles IS 'Mentor-specific profile fields (includes department and avatar)';
COMMENT ON COLUMN mentor_profiles.department IS 'Department or faculty that the mentor belongs to';
COMMENT ON COLUMN mentor_profiles.avatar_url IS 'URL to mentor profile avatar image';
COMMENT ON VIEW v_users_full IS 'User information view - password excluded for security';
COMMENT ON TABLE roles IS 'System roles (ADMIN, MENTOR, STUDENT) - UPPERCASE';
COMMENT ON TABLE auth_tokens IS 'Refresh tokens + revoked access tokens';
COMMENT ON TABLE certificates IS 'Language certificates (TOEIC, IELTS, ...)';
COMMENT ON TABLE certificate_parts IS 'Certificate sections with properties (JSONB)';
COMMENT ON TABLE courses IS 'Courses linked to certificates and mentors';
COMMENT ON TABLE course_content IS 'Lessons, vocabulary and homework';
COMMENT ON TABLE homework_submissions IS 'Student homework answers and grades';
COMMENT ON TABLE question_bank IS 'Shared question bank for all exams';
COMMENT ON TABLE question_bank_options IS 'Answer options for questions';
COMMENT ON TABLE exams IS 'Trial exams and course exams (exam_scope)';
COMMENT ON TABLE exam_questions IS 'Questions assigned to exams';
COMMENT ON TABLE exam_attempts IS 'Student exam attempts with scores';
COMMENT ON TABLE exam_attempt_details IS 'Per-question answers in an attempt';
COMMENT ON TABLE payment_transactions IS 'Payments with confirmation codes';
COMMENT ON TABLE payment_audit IS 'Audit log for payment changes';
COMMENT ON TABLE enrollments IS 'Student course enrollments';
COMMENT ON TABLE mentor_schedules IS 'Mentor 1-on-1 session slots';
COMMENT ON TABLE study_schedules IS 'Student self-study schedules';
COMMENT ON TABLE learning_paths IS 'Learning paths with items and progress (JSONB)';
COMMENT ON TABLE notifications IS 'Email notifications for all event types';
COMMENT ON TABLE practice_exams IS 'Practice exams with AI evaluation (answers JSONB)';
