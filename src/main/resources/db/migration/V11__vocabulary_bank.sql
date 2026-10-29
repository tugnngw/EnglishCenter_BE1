-- =====================================================
-- Migration V6: Kho tu vung chung (Centralized Vocabulary Bank)
--
-- Muc tich:
--   1. Tach "tao tu vung" (global bank) ra khoi "gan tu vung vao khoa hoc".
--      - vocabulary_words     : tu vung DUNG CHUNG (khong thuoc khoa hoc nao)
--      - vocabulary_sets      : bo tu vung trong kho chung
--      - vocabulary_set_words : quan he nhieu-nhieu giua bo va tu
--      - course_vocabulary_sets: quan he gan bo tu vung chung vao khoa hoc
--   2. Migrate du lieu cu: moi course_content (content_type='vocabulary') cua
--      TAT CA khoa hoc -> thanh 1 bo trong kho chung + 1 lien kết vào khoa hoc.
--      => Khong mat du lieu cho hoc vien dang hoc.
--
-- Khong sua/xoa migration cu. Chi them bang moi + chuyen du lieu len.
-- =====================================================

-- ------------------------------------------------------------
-- 1. TU VUNG CHUNG (global word bank)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vocabulary_words (
    word_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    word             VARCHAR(255) NOT NULL,
    ipa              VARCHAR(100),
    meaning          VARCHAR(500),
    audio_url        VARCHAR(500),
    part_of_speech   VARCHAR(100),
    example          TEXT,
    created_by_user_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

-- Tu vung trong kho chung la duy nhat (case-insensitive) -> tranh trung khi ghep
-- nhieu bo vao cung mot khoa hoc.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vocabulary_words_word
    ON vocabulary_words(lower(word));

CREATE INDEX IF NOT EXISTS idx_vocabulary_words_created_by
    ON vocabulary_words(created_by_user_id);

COMMENT ON TABLE vocabulary_words IS 'Global vocabulary words - independent from any course';

-- ------------------------------------------------------------
-- 2. BO TU VUNG TRONG KHO CHUNG
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vocabulary_sets (
    set_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    created_by_user_id UUID NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_vocabulary_sets_created_by
    ON vocabulary_sets(created_by_user_id);

COMMENT ON TABLE vocabulary_sets IS 'Vocabulary sets stored in the global vocabulary bank';

-- ------------------------------------------------------------
-- 3. QUAN HE BO <-> tu
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vocabulary_set_words (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id           UUID NOT NULL,
    word_id          UUID NOT NULL,
    word_order       INT NOT NULL DEFAULT 0,
    FOREIGN KEY (set_id) REFERENCES vocabulary_sets(set_id) ON DELETE CASCADE,
    FOREIGN KEY (word_id) REFERENCES vocabulary_words(word_id) ON DELETE CASCADE
);

-- Một từ chỉ xuất hiện 1 lần trong 1 bộ.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vocabulary_set_words
    ON vocabulary_set_words(set_id, word_id);

CREATE INDEX IF NOT EXISTS idx_vocabulary_set_words_word
    ON vocabulary_set_words(word_id);

-- ------------------------------------------------------------
-- 4. GAN BO TU VUNG CHUNG VAO KHOA HOC
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_vocabulary_sets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id        UUID NOT NULL,
    set_id           UUID NOT NULL,
    display_order    INT NOT NULL DEFAULT 0,
    assigned_by_user_id UUID,
    assigned_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    FOREIGN KEY (set_id) REFERENCES vocabulary_sets(set_id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_by_user_id) REFERENCES users(user_id) ON DELETE SET NULL
);

-- Một bộ chỉ được gán vào 1 khóa học đúng 1 lần.
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_vocabulary_sets
    ON course_vocabulary_sets(course_id, set_id);

CREATE INDEX IF NOT EXISTS idx_course_vocabulary_sets_set
    ON course_vocabulary_sets(set_id);

COMMENT ON TABLE course_vocabulary_sets IS 'Vocabulary sets from the global bank assigned to a course';

-- ------------------------------------------------------------
-- 5. MIGRATE DU LIEU CU (course_content content_type = 'vocabulary')
--    Moi row cu -> 1 bo trong kho chung + link vao khoa hoc goc.
-- ------------------------------------------------------------
WITH legacy AS (
    SELECT
        cc.content_id,
        cc.course_id,
        cc.title,
        cc.description,
        cc.content_order,
        cc.created_at,
        cc.last_updated,
        c.created_by_user_id,
        CASE
            WHEN jsonb_typeof(cc.content_data->'items') = 'array'
                THEN cc.content_data->'items'
            WHEN jsonb_typeof(cc.content_data) = 'object' AND cc.content_data ? 'word'
                THEN jsonb_build_array(cc.content_data)
            ELSE '[]'::jsonb
        END AS items
    FROM course_content cc
             JOIN courses c ON c.course_id = cc.course_id
    WHERE cc.content_type = 'vocabulary'
),
cleaned AS (
    SELECT l.content_id, l.course_id, l.title, l.description, l.content_order,
           l.created_at, l.last_updated, l.created_by_user_id,
           lower(trim(t.item->>'word')) AS norm_word,
           t.ord,
           t.item
    FROM legacy l,
         LATERAL jsonb_array_elements(l.items) WITH ORDINALITY AS t(item, ord)
    WHERE nullif(trim(t.item->>'word'), '') IS NOT NULL
),
dedup AS (
    SELECT DISTINCT ON (norm_word) *
    FROM cleaned
    ORDER BY norm_word, content_id, ord
)
INSERT INTO vocabulary_words (word, ipa, meaning, audio_url, part_of_speech, example,
                              created_by_user_id, created_at, last_updated)
SELECT d.norm_word,
       NULLIF(COALESCE(NULLIF(d.item->>'ipa', ''), d.item->>'phonetic'), ''),
       NULLIF(d.item->>'meaning', ''),
       NULLIF(d.item->>'audioUrl', ''),
       NULLIF(d.item->>'partOfSpeech', ''),
       NULLIF(d.item->>'example', ''),
       d.created_by_user_id,
       d.created_at,
       d.last_updated
FROM dedup d
ON CONFLICT DO NOTHING;

-- Tao bo trong kho chung (set_id = content_id cu de giu trace)
WITH legacy AS (
    SELECT cc.content_id, cc.title, cc.description, cc.created_at, cc.last_updated,
           c.created_by_user_id
    FROM course_content cc
             JOIN courses c ON c.course_id = cc.course_id
    WHERE cc.content_type = 'vocabulary'
)
INSERT INTO vocabulary_sets (set_id, title, description, created_by_user_id, created_at, last_updated)
SELECT l.content_id,
       COALESCE(NULLIF(l.title, ''), 'Bo tu vung'),
       l.description,
       l.created_by_user_id,
       l.created_at,
       l.last_updated
FROM legacy l
ON CONFLICT DO NOTHING;

-- Gan tu vao bo
WITH legacy AS (
    SELECT cc.content_id, cc.course_id, c.created_by_user_id,
           CASE
               WHEN jsonb_typeof(cc.content_data->'items') = 'array'
                   THEN cc.content_data->'items'
               WHEN jsonb_typeof(cc.content_data) = 'object' AND cc.content_data ? 'word'
                   THEN jsonb_build_array(cc.content_data)
               ELSE '[]'::jsonb
           END AS items
    FROM course_content cc
             JOIN courses c ON c.course_id = cc.course_id
    WHERE cc.content_type = 'vocabulary'
),
cleaned AS (
    SELECT l.content_id, lower(trim(t.item->>'word')) AS norm_word, t.ord
    FROM legacy l,
         LATERAL jsonb_array_elements(l.items) WITH ORDINALITY AS t(item, ord)
    WHERE nullif(trim(t.item->>'word'), '') IS NOT NULL
)
INSERT INTO vocabulary_set_words (set_id, word_id, word_order)
SELECT c.content_id, v.word_id, c.ord
FROM cleaned c
         JOIN vocabulary_words v ON lower(v.word) = c.norm_word
ON CONFLICT DO NOTHING;

-- Lien ket bo vao khoa hoc goc
WITH legacy AS (
    SELECT cc.content_id, cc.course_id, cc.content_order, cc.created_at, c.created_by_user_id
    FROM course_content cc
             JOIN courses c ON c.course_id = cc.course_id
    WHERE cc.content_type = 'vocabulary'
)
INSERT INTO course_vocabulary_sets (course_id, set_id, display_order, assigned_by_user_id, assigned_at)
SELECT l.course_id, l.content_id, COALESCE(l.content_order, 0), l.created_by_user_id, l.created_at
FROM legacy l
ON CONFLICT DO NOTHING;

-- Xoa noi dung cu (da chuyen sang kho chung).
-- Tha parent_content_id truoc de khong lam mat bai hoc con.
UPDATE course_content
SET parent_content_id = NULL
WHERE parent_content_id IN (
    SELECT content_id FROM course_content WHERE content_type = 'vocabulary'
);

DELETE FROM course_content WHERE content_type = 'vocabulary';
