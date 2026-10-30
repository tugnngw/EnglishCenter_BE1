-- V15: Link practice_exams to certificates (TASK-DEV1 Learner Onboarding T6)
ALTER TABLE practice_exams
ADD COLUMN IF NOT EXISTS certificate_id UUID REFERENCES certificates(certificate_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_practice_exams_certificate_id ON practice_exams(certificate_id);
