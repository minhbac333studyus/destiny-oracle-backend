-- ============================================================
-- Destiny Oracle — Fresh DB Seed Script
-- ============================================================
-- Run after first startup (Hibernate creates tables via ddl-auto).
-- Safe to re-run — uses ON CONFLICT to skip existing rows.
--
-- Usage:
--   psql -U postgres -d destiny_oracle -f scripts/seed.sql
-- ============================================================

-- ── Cleanup legacy tables (removed features) ──────────────────
DROP TABLE IF EXISTS habit_completions CASCADE;
DROP TABLE IF EXISTS habits CASCADE;
DROP TABLE IF EXISTS milestones CASCADE;
DROP TABLE IF EXISTS goals CASCADE;

-- ── Drop legacy columns from card_stage_content ───────────────
ALTER TABLE card_stage_content DROP COLUMN IF EXISTS title;
ALTER TABLE card_stage_content DROP COLUMN IF EXISTS tagline;
ALTER TABLE card_stage_content DROP COLUMN IF EXISTS lore;
ALTER TABLE card_stage_content DROP COLUMN IF EXISTS action_scene;

-- ══════════════════════════════════════════════════════════════
-- 1. ASPECT DEFINITIONS (10 built-in aspects)
--    PK = aspect_key (no id column)
-- ══════════════════════════════════════════════════════════════
INSERT INTO aspect_definitions (aspect_key, label, icon, sort_order, is_active) VALUES
    ('health',        'Health & Body',          '⚔️',  1, true),
    ('career',        'Career & Purpose',       '🎖️', 2, true),
    ('finances',      'Finances',               '💴',  3, true),
    ('relationships', 'Relationships',          '🌸',  4, true),
    ('family',        'Family',                 '🏯',  5, true),
    ('learning',      'Mind & Learning',        '📜',  6, true),
    ('creativity',    'Creativity',             '🎴',  7, true),
    ('spirituality',  'Spirituality / Meaning', '⛩️',  8, true),
    ('lifestyle',     'Lifestyle & Freedom',    '🍃',  9, true),
    ('legacy',        'Legacy & Impact',        '⚜️',  10, true)
ON CONFLICT (aspect_key) DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 2. DEMO USER
--    Includes all NOT NULL columns from users table
-- ══════════════════════════════════════════════════════════════
INSERT INTO users (
    id, email, display_name, onboarding_complete,
    daily_reminder_time, notifications_enabled, timezone, joined_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'demo@destinyoracle.app',
    'Oracle Seeker',
    true,
    '08:00',
    true,
    'America/New_York',
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 3. DEMO DESTINY CARDS (5 starter aspects)
--    Includes all NOT NULL columns: dream_original is required
-- ══════════════════════════════════════════════════════════════

-- Health (Storm stage)
INSERT INTO destiny_cards (
    id, user_id, aspect_key, aspect_label, aspect_icon, sort_order,
    fear_original, dream_original, current_stage, stage_progress_percent,
    total_check_ins, longest_streak, current_streak, days_at_current_stage,
    image_url, prompt_status, last_updated, is_custom_aspect
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'health', 'Health & Body', '⚔️', 1,
    'In 10 years I''ll be overweight, exhausted, and dependent on medication.',
    'I want to run a marathon and feel strong every single day.',
    'storm', 85, 24, 12, 8, 26,
    'assets/health-user1.png', 'NONE', NOW(), false
) ON CONFLICT (user_id, aspect_key) DO NOTHING;

-- Career (Fog stage)
INSERT INTO destiny_cards (
    id, user_id, aspect_key, aspect_label, aspect_icon, sort_order,
    fear_original, dream_original, current_stage, stage_progress_percent,
    total_check_ins, longest_streak, current_streak, days_at_current_stage,
    image_url, prompt_status, last_updated, is_custom_aspect
) VALUES (
    'a0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'career', 'Career & Purpose', '🎖️', 2,
    'Still doing the same job, same title, watching people I once mentored pass me by.',
    'I want to lead a team building something that actually matters.',
    'fog', 58, 34, 14, 8, 42,
    'assets/health-user1.png', 'NONE', NOW(), false
) ON CONFLICT (user_id, aspect_key) DO NOTHING;

-- Finances (Clearing stage)
INSERT INTO destiny_cards (
    id, user_id, aspect_key, aspect_label, aspect_icon, sort_order,
    fear_original, dream_original, current_stage, stage_progress_percent,
    total_check_ins, longest_streak, current_streak, days_at_current_stage,
    image_url, prompt_status, last_updated, is_custom_aspect
) VALUES (
    'a0000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'finances', 'Finances', '💴', 3,
    'No savings, maxed credit cards, unable to help my family when they need it.',
    'I want to be debt-free with 6 months of savings and invest for my kids'' future.',
    'clearing', 42, 78, 30, 18, 45,
    'assets/health-user1.png', 'NONE', NOW(), false
) ON CONFLICT (user_id, aspect_key) DO NOTHING;

-- Relationships (Clearing stage)
INSERT INTO destiny_cards (
    id, user_id, aspect_key, aspect_label, aspect_icon, sort_order,
    fear_original, dream_original, current_stage, stage_progress_percent,
    total_check_ins, longest_streak, current_streak, days_at_current_stage,
    image_url, prompt_status, last_updated, is_custom_aspect
) VALUES (
    'a0000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000001',
    'relationships', 'Relationships', '🌸', 4,
    'Growing old with no one who truly knows me. Surface-level friendships and a hollow phone.',
    'I want a circle of deep friendships where I can be fully myself.',
    'clearing', 70, 62, 21, 14, 91,
    'assets/health-user1.png', 'NONE', NOW(), false
) ON CONFLICT (user_id, aspect_key) DO NOTHING;

-- Family (Aura stage)
INSERT INTO destiny_cards (
    id, user_id, aspect_key, aspect_label, aspect_icon, sort_order,
    fear_original, dream_original, current_stage, stage_progress_percent,
    total_check_ins, longest_streak, current_streak, days_at_current_stage,
    image_url, prompt_status, last_updated, is_custom_aspect
) VALUES (
    'a0000000-0000-0000-0000-000000000005',
    '00000000-0000-0000-0000-000000000001',
    'family', 'Family', '🏯', 5,
    'My kids will remember me as the parent who was always on the phone, never really there.',
    'I want my kids to say I was the most present parent they know.',
    'aura', 60, 156, 60, 45, 88,
    'assets/health-user1.png', 'NONE', NOW(), false
) ON CONFLICT (user_id, aspect_key) DO NOTHING;

-- ══════════════════════════════════════════════════════════════
-- 4. STAGE CONTENT PLACEHOLDERS (6 rows per card)
--    image_prompt = NULL — generated by ImagePromptService
-- ══════════════════════════════════════════════════════════════
DO $$
DECLARE
    card_ids UUID[] := ARRAY[
        'a0000000-0000-0000-0000-000000000001'::UUID,
        'a0000000-0000-0000-0000-000000000002'::UUID,
        'a0000000-0000-0000-0000-000000000003'::UUID,
        'a0000000-0000-0000-0000-000000000004'::UUID,
        'a0000000-0000-0000-0000-000000000005'::UUID
    ];
    stages TEXT[] := ARRAY['storm', 'fog', 'clearing', 'aura', 'radiance', 'legend'];
    cid UUID;
    s TEXT;
BEGIN
    FOREACH cid IN ARRAY card_ids LOOP
        FOREACH s IN ARRAY stages LOOP
            INSERT INTO card_stage_content (id, card_id, stage, generated_at)
            VALUES (gen_random_uuid(), cid, s, NOW())
            ON CONFLICT (card_id, stage) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- ══════════════════════════════════════════════════════════════
-- Verify:
-- ══════════════════════════════════════════════════════════════
SELECT 'aspect_definitions: ' || count(*) FROM aspect_definitions
UNION ALL SELECT 'users: ' || count(*) FROM users
UNION ALL SELECT 'destiny_cards: ' || count(*) FROM destiny_cards
UNION ALL SELECT 'card_stage_content: ' || count(*) FROM card_stage_content;
