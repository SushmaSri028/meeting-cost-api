-- ============================================================
-- V1: Initial Schema for MeetingCost.io
-- Creates: users, meetings, participants, salary_lookup tables
-- ============================================================

-- Enable UUID extension (PostgreSQL built-in, needed for gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── USERS ─────────────────────────────────────────────────
CREATE TABLE users (
                       id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email               VARCHAR(255) NOT NULL UNIQUE,
                       password_hash       VARCHAR(255) NOT NULL,
                       display_name        VARCHAR(255),
    -- Google OAuth tokens (encrypted at rest in production)
                       google_access_token  TEXT,
                       google_refresh_token TEXT,
                       google_token_expiry  TIMESTAMP WITH TIME ZONE,
    -- Metadata
                       created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for fast email lookup during login
CREATE INDEX idx_users_email ON users(email);


-- ── MEETINGS ──────────────────────────────────────────────
CREATE TABLE meetings (
                          id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Google Calendar's own event ID — prevents duplicate syncs
                          calendar_event_id    VARCHAR(500) NOT NULL,
                          title                VARCHAR(500) NOT NULL,
                          description          TEXT,
                          start_time           TIMESTAMP WITH TIME ZONE NOT NULL,
                          end_time             TIMESTAMP WITH TIME ZONE NOT NULL,
                          duration_minutes     INTEGER NOT NULL,
    -- Computed by our cost engine
                          estimated_cost_usd   DECIMAL(10, 2),
                          participant_count    INTEGER DEFAULT 0,
    -- Sync metadata
                          last_synced_at       TIMESTAMP WITH TIME ZONE,
                          created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Prevent importing the same Google Calendar event twice for the same user
                          CONSTRAINT uq_user_calendar_event UNIQUE (user_id, calendar_event_id)
);

CREATE INDEX idx_meetings_user_id ON meetings(user_id);
CREATE INDEX idx_meetings_start_time ON meetings(user_id, start_time DESC);


-- ── PARTICIPANTS ───────────────────────────────────────────
CREATE TABLE participants (
                              id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              meeting_id               UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
                              email                    VARCHAR(255),
                              display_name             VARCHAR(255),
    -- Our cost estimation fields
                              estimated_title          VARCHAR(255),       -- e.g. "Senior Engineer"
                              estimated_annual_salary  DECIMAL(10, 2),     -- e.g. 150000.00
                              estimated_hourly_rate    DECIMAL(10, 4),     -- annual / 52 / 40
                              cost_contribution_usd    DECIMAL(10, 2),     -- their share of meeting cost
                              created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_participants_meeting_id ON participants(meeting_id);
-- V2: Add additional indexes for query performance

-- Index for finding meetings by title keyword (for recurring meeting detection)
CREATE INDEX idx_meetings_title ON meetings(user_id, title);

-- Index for salary lookup by keyword (used heavily by cost engine)
CREATE INDEX idx_salary_lookup_keyword ON salary_lookup(title_keyword);

-- Index for participants by email (for deduplication)
CREATE INDEX idx_participants_email ON participants(meeting_id, email);


-- ── SALARY LOOKUP ─────────────────────────────────────────
-- Default salary estimates by title keyword.
-- user_id NULL = global default, user_id set = user's own override.
CREATE TABLE salary_lookup (
                               id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id             UUID REFERENCES users(id) ON DELETE CASCADE,
                               title_keyword       VARCHAR(255) NOT NULL,    -- e.g. "Senior Engineer"
                               annual_salary       DECIMAL(10, 2) NOT NULL,  -- e.g. 150000
                               created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);


-- ── SEED: Default salary estimates ────────────────────────
-- These are used when no user-specific override exists
INSERT INTO salary_lookup (title_keyword, annual_salary) VALUES
                                                             ('CEO',                      350000),
                                                             ('CTO',                      300000),
                                                             ('VP Engineering',           280000),
                                                             ('Director Engineering',     230000),
                                                             ('Engineering Manager',      200000),
                                                             ('Principal Engineer',       220000),
                                                             ('Staff Engineer',           200000),
                                                             ('Senior Engineer',          160000),
                                                             ('Software Engineer',        130000),
                                                             ('Junior Engineer',           95000),
                                                             ('Product Manager',          140000),
                                                             ('Senior Product Manager',   170000),
                                                             ('Designer',                 110000),
                                                             ('Senior Designer',          140000),
                                                             ('Data Scientist',           145000),
                                                             ('Data Analyst',             100000),
                                                             ('Marketing',                 90000),
                                                             ('Sales',                     85000),
                                                             ('Recruiter',                  85000),
                                                             ('Default',                   100000);   -- fallback if no title matches