-- =====================================================
-- D3 - DỮ LIỆU MẪU CHO MODULE EXAM (chạy tay, KHÔNG phải Flyway migration)
--
-- Điều kiện: đã chạy migration V1..V5 (khởi động app 1 lần là đủ).
-- Cách chạy:
--   psql -h localhost -U postgres -d englishcenter -f src/main/resources/db/init/D3_exam_sample_data.sql
--   (hoặc mở file trong DBeaver / pgAdmin rồi Execute Script)
--
-- Script idempotent: chạy lại nhiều lần không lỗi, không nhân bản dữ liệu (ID cố định + ON CONFLICT).
-- Người tạo là tài khoản admin seed của V1 (admin@gmail.com) - trigger của V1 yêu cầu đề trial / khóa học
-- phải do ADMIN tạo.
--
-- Tạo ra:
--   1 chứng chỉ "IELTS Academic (Sample)" + 3 phần Listening / Reading / Writing
--   1 đề thi thử (trial) ĐÃ PUBLISH  : D3-IELTS-MINI-01   (3 section, 7 câu, đủ 5 loại câu hỏi)
--   1 khóa học mẫu + 1 đề của khóa học (course, cần enrollment) ĐÃ PUBLISH : D3-COURSE-QUIZ-01
--
-- ID cố định để dùng trong Postman:
--   exam trial  : d3000000-0000-4000-8000-000000000100
--   exam course : d3000000-0000-4000-8000-000000000200
-- =====================================================

-- File lưu UTF-8; psql trên Windows mặc định WIN1252 nên phải khai báo để không lỗi tiếng Việt
SET client_encoding = 'UTF8';

BEGIN;

-- ---------- Chứng chỉ + phần thi ----------
INSERT INTO certificates (certificate_id, created_by_user_id, certificate_name, description)
VALUES ('d3000000-0000-4000-8000-000000000001',
        (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
        'IELTS Academic (Sample)', 'Chứng chỉ mẫu cho module Exam (Dev 3)')
ON CONFLICT (certificate_id) DO NOTHING;

INSERT INTO certificate_parts (part_id, certificate_id, part_name, part_description, part_order) VALUES
    ('d3000000-0000-4000-8000-000000000011', 'd3000000-0000-4000-8000-000000000001', 'Listening', 'Nghe hiểu', 1),
    ('d3000000-0000-4000-8000-000000000012', 'd3000000-0000-4000-8000-000000000001', 'Reading',   'Đọc hiểu', 2),
    ('d3000000-0000-4000-8000-000000000013', 'd3000000-0000-4000-8000-000000000001', 'Writing',   'Viết',     3)
ON CONFLICT (part_id) DO NOTHING;

-- =====================================================
-- ĐỀ 1: THI THỬ (trial) - miễn phí, ai đăng nhập cũng làm được
-- =====================================================
INSERT INTO exams (exam_id, exam_scope, created_by_user_id, certificate_id, exam_code, exam_type, exam_title,
                   description, duration_minutes, passing_score, max_attempts, show_answers, show_score,
                   is_free, price, difficulty_level, recommended_level, status, published_date)
VALUES ('d3000000-0000-4000-8000-000000000100', 'trial',
        (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
        'd3000000-0000-4000-8000-000000000001', 'D3-IELTS-MINI-01', 'placement_test',
        'IELTS Mini Placement Test', 'Đề kiểm tra đầu vào rút gọn: Listening, Reading, Writing',
        45, 50.00, 3, TRUE, TRUE, TRUE, 0, 'medium', 'B1', 'published', CURRENT_TIMESTAMP)
ON CONFLICT (exam_id) DO NOTHING;

INSERT INTO exam_sections (section_id, exam_id, certificate_part_id, section_type, title, instructions,
                           passage_text, audio_url, section_order, duration_minutes) VALUES
    ('d3000000-0000-4000-8000-000000000111', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000011', 'LISTENING', 'Part 1 - Listening',
     'Nghe đoạn hội thoại và trả lời câu hỏi.', NULL,
     'https://example.com/audio/d3-listening-01.mp3', 1, 10),
    ('d3000000-0000-4000-8000-000000000112', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000012', 'READING', 'Part 2 - Reading',
     'Đọc đoạn văn và trả lời câu hỏi.',
     'Tom moved to London last year. He works in a small bakery and goes to an English class every evening.',
     NULL, 2, 20),
    ('d3000000-0000-4000-8000-000000000113', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000013', 'WRITING', 'Part 3 - Writing',
     'Viết đoạn văn khoảng 120-150 từ.', NULL, NULL, 3, 15)
ON CONFLICT (section_id) DO NOTHING;

-- Câu hỏi (ngân hàng câu hỏi dùng chung)
INSERT INTO question_bank (question_id, created_by_user_id, certificate_id, certificate_part_id, question_type,
                           question_text, difficulty_level, topic, points, explanation, audio_url) VALUES
    ('d3000000-0000-4000-8000-000000000121', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000011', 'multiple_choice',
     'Where does the woman want to go?', 'easy', 'travel', 1.00,
     'Người phụ nữ nói "I need to get to the train station".', NULL),
    ('d3000000-0000-4000-8000-000000000122', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000011', 'true_false',
     'The man offers to drive her there.', 'easy', 'travel', 1.00,
     'Người đàn ông chỉ chỉ đường, không chở cô ấy.', NULL),
    ('d3000000-0000-4000-8000-000000000123', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000012', 'multiple_choice',
     'Where does Tom work?', 'easy', 'daily life', 1.00, 'Đoạn văn: "He works in a small bakery".', NULL),
    ('d3000000-0000-4000-8000-000000000124', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000012', 'fill_blank',
     'Tom ______ to London last year. (move)', 'medium', 'grammar', 1.00,
     'Thì quá khứ đơn: move -> moved.', NULL),
    ('d3000000-0000-4000-8000-000000000125', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000012', 'matching',
     'Nối từ với nghĩa tiếng Việt.', 'medium', 'vocabulary', 2.00,
     'bakery = tiệm bánh, evening = buổi tối, class = lớp học.', NULL),
    ('d3000000-0000-4000-8000-000000000126', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000012', 'true_false',
     'Tom studies English in the morning.', 'easy', 'daily life', 1.00,
     'Tom học tiếng Anh vào buổi tối (every evening).', NULL),
    ('d3000000-0000-4000-8000-000000000127', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000013', 'essay',
     'Describe your hometown in 120-150 words.', 'medium', 'writing task', 6.00,
     'Tiêu chí: Task response, Coherence, Lexical resource, Grammar.', NULL)
ON CONFLICT (question_id) DO NOTHING;

-- Đáp án. fill_blank: mỗi dòng là một đáp án được chấp nhận. matching: "vế trái::vế phải".
INSERT INTO question_bank_options (option_id, question_id, option_text, is_correct, option_order, explanation) VALUES
    ('d3000000-0000-4000-8000-000000000131', 'd3000000-0000-4000-8000-000000000121', 'The airport',       FALSE, 1, NULL),
    ('d3000000-0000-4000-8000-000000000132', 'd3000000-0000-4000-8000-000000000121', 'The train station', TRUE,  2, NULL),
    ('d3000000-0000-4000-8000-000000000133', 'd3000000-0000-4000-8000-000000000121', 'The bus stop',      FALSE, 3, NULL),
    ('d3000000-0000-4000-8000-000000000134', 'd3000000-0000-4000-8000-000000000122', 'True',  FALSE, 1, NULL),
    ('d3000000-0000-4000-8000-000000000135', 'd3000000-0000-4000-8000-000000000122', 'False', TRUE,  2, NULL),
    ('d3000000-0000-4000-8000-000000000136', 'd3000000-0000-4000-8000-000000000123', 'In a bank',    FALSE, 1, NULL),
    ('d3000000-0000-4000-8000-000000000137', 'd3000000-0000-4000-8000-000000000123', 'In a bakery',  TRUE,  2, NULL),
    ('d3000000-0000-4000-8000-000000000138', 'd3000000-0000-4000-8000-000000000123', 'In a school',  FALSE, 3, NULL),
    ('d3000000-0000-4000-8000-000000000139', 'd3000000-0000-4000-8000-000000000123', 'In a library', FALSE, 4, NULL),
    ('d3000000-0000-4000-8000-000000000140', 'd3000000-0000-4000-8000-000000000124', 'moved',        TRUE,  1, NULL),
    ('d3000000-0000-4000-8000-000000000141', 'd3000000-0000-4000-8000-000000000125', 'bakery::tiệm bánh', TRUE, 1, NULL),
    ('d3000000-0000-4000-8000-000000000142', 'd3000000-0000-4000-8000-000000000125', 'evening::buổi tối', TRUE, 2, NULL),
    ('d3000000-0000-4000-8000-000000000143', 'd3000000-0000-4000-8000-000000000125', 'class::lớp học',    TRUE, 3, NULL),
    ('d3000000-0000-4000-8000-000000000144', 'd3000000-0000-4000-8000-000000000126', 'True',  FALSE, 1, NULL),
    ('d3000000-0000-4000-8000-000000000145', 'd3000000-0000-4000-8000-000000000126', 'False', TRUE,  2, NULL)
ON CONFLICT (option_id) DO NOTHING;

-- Gắn câu hỏi vào đề (trigger V1 tự tăng question_bank.usage_count)
INSERT INTO exam_questions (id, exam_id, section_id, question_bank_id, question_order, time_limit_seconds) VALUES
    ('d3000000-0000-4000-8000-000000000151', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000111', 'd3000000-0000-4000-8000-000000000121', 1, 60),
    ('d3000000-0000-4000-8000-000000000152', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000111', 'd3000000-0000-4000-8000-000000000122', 2, 60),
    ('d3000000-0000-4000-8000-000000000153', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000112', 'd3000000-0000-4000-8000-000000000123', 1, 90),
    ('d3000000-0000-4000-8000-000000000154', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000112', 'd3000000-0000-4000-8000-000000000124', 2, 90),
    ('d3000000-0000-4000-8000-000000000155', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000112', 'd3000000-0000-4000-8000-000000000125', 3, 120),
    ('d3000000-0000-4000-8000-000000000156', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000112', 'd3000000-0000-4000-8000-000000000126', 4, 60),
    ('d3000000-0000-4000-8000-000000000157', 'd3000000-0000-4000-8000-000000000100',
     'd3000000-0000-4000-8000-000000000113', 'd3000000-0000-4000-8000-000000000127', 1, 900)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- ĐỀ 2: ĐỀ CỦA KHÓA HỌC (course) - cần enrollment (Dev 4)
-- =====================================================
INSERT INTO courses (course_id, created_by_user_id, certificate_id, code, title, description, level, price, status)
VALUES ('d3000000-0000-4000-8000-000000000020',
        (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
        'd3000000-0000-4000-8000-000000000001', 'D3-SAMPLE-COURSE', 'IELTS Foundation (Sample)',
        'Khóa học mẫu để thử đề thi course-scope', 'BEGINNER', 990000, 'active')
ON CONFLICT (course_id) DO NOTHING;

INSERT INTO exams (exam_id, exam_scope, created_by_user_id, certificate_id, course_id, exam_code, exam_type,
                   exam_title, description, duration_minutes, passing_score, max_attempts, show_answers,
                   show_score, difficulty_level, recommended_level, status, published_date)
VALUES ('d3000000-0000-4000-8000-000000000200', 'course',
        (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
        'd3000000-0000-4000-8000-000000000001', 'd3000000-0000-4000-8000-000000000020',
        'D3-COURSE-QUIZ-01', 'quiz', 'Unit 1 Quiz', 'Bài kiểm tra nhanh Unit 1', 10, 60.00, 0,
        TRUE, TRUE, 'easy', 'A2', 'published', CURRENT_TIMESTAMP)
ON CONFLICT (exam_id) DO NOTHING;

INSERT INTO question_bank (question_id, created_by_user_id, certificate_id, question_type, question_text,
                           difficulty_level, topic, points, explanation) VALUES
    ('d3000000-0000-4000-8000-000000000221', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'multiple_choice', 'She ___ a student.', 'easy', 'grammar', 1.00,
     'Chủ ngữ số ít ngôi thứ 3 dùng "is".'),
    ('d3000000-0000-4000-8000-000000000222', (SELECT user_id FROM users WHERE email = 'admin@gmail.com'),
     'd3000000-0000-4000-8000-000000000001', 'fill_blank', 'I have two ______. (child)', 'easy', 'grammar', 1.00,
     'Số nhiều bất quy tắc: child -> children.')
ON CONFLICT (question_id) DO NOTHING;

INSERT INTO question_bank_options (option_id, question_id, option_text, is_correct, option_order) VALUES
    ('d3000000-0000-4000-8000-000000000231', 'd3000000-0000-4000-8000-000000000221', 'am',  FALSE, 1),
    ('d3000000-0000-4000-8000-000000000232', 'd3000000-0000-4000-8000-000000000221', 'is',  TRUE,  2),
    ('d3000000-0000-4000-8000-000000000233', 'd3000000-0000-4000-8000-000000000221', 'are', FALSE, 3),
    ('d3000000-0000-4000-8000-000000000234', 'd3000000-0000-4000-8000-000000000222', 'children', TRUE, 1)
ON CONFLICT (option_id) DO NOTHING;

INSERT INTO exam_questions (id, exam_id, section_id, question_bank_id, question_order) VALUES
    ('d3000000-0000-4000-8000-000000000251', 'd3000000-0000-4000-8000-000000000200', NULL,
     'd3000000-0000-4000-8000-000000000221', 1),
    ('d3000000-0000-4000-8000-000000000252', 'd3000000-0000-4000-8000-000000000200', NULL,
     'd3000000-0000-4000-8000-000000000222', 2)
ON CONFLICT (id) DO NOTHING;

-- ---------- Cập nhật số câu / số phần (service tự làm khi tạo qua API) ----------
UPDATE exams e
SET total_questions = (SELECT COUNT(*) FROM exam_questions q WHERE q.exam_id = e.exam_id),
    total_parts     = (SELECT COUNT(*) FROM exam_sections s WHERE s.exam_id = e.exam_id)
WHERE e.exam_id IN ('d3000000-0000-4000-8000-000000000100', 'd3000000-0000-4000-8000-000000000200');

COMMIT;
