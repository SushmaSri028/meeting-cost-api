-- ============================================================
-- V1: Initial Schema for MeetingCost.io
-- Creates: users, meetings, participants, salary_lookup tables
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── USERS ─────────────────────────────────────────────────
CREATE TABLE users (
                       id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email                VARCHAR(255) NOT NULL UNIQUE,
                       password_hash        VARCHAR(255) NOT NULL,
                       display_name         VARCHAR(255),
                       google_access_token  TEXT,
                       google_refresh_token TEXT,
                       google_token_expiry  TIMESTAMP WITH TIME ZONE,
                       created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                       updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ── MEETINGS ──────────────────────────────────────────────
CREATE TABLE meetings (
                          id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          calendar_event_id  VARCHAR(500) NOT NULL,
                          title              VARCHAR(500) NOT NULL,
                          description        TEXT,
                          start_time         TIMESTAMP WITH TIME ZONE NOT NULL,
                          end_time           TIMESTAMP WITH TIME ZONE NOT NULL,
                          duration_minutes   INTEGER NOT NULL,
                          estimated_cost_usd DECIMAL(10, 2),
                          participant_count  INTEGER DEFAULT 0,
                          last_synced_at     TIMESTAMP WITH TIME ZONE,
                          created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                          CONSTRAINT uq_user_calendar_event UNIQUE (user_id, calendar_event_id)
);

CREATE INDEX idx_meetings_user_id ON meetings(user_id);
CREATE INDEX idx_meetings_start_time ON meetings(user_id, start_time DESC);
CREATE INDEX idx_meetings_title ON meetings(user_id, title);

-- ── PARTICIPANTS ───────────────────────────────────────────
CREATE TABLE participants (
                              id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              meeting_id              UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
                              email                   VARCHAR(255),
                              display_name            VARCHAR(255),
                              estimated_title         VARCHAR(255),
                              estimated_annual_salary DECIMAL(10, 2),
                              estimated_hourly_rate   DECIMAL(10, 4),
                              cost_contribution_usd   DECIMAL(10, 2),
                              created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_participants_meeting_id ON participants(meeting_id);
CREATE INDEX idx_participants_email ON participants(meeting_id, email);

-- ── SALARY LOOKUP ─────────────────────────────────────────
CREATE TABLE salary_lookup (
                               id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id       UUID REFERENCES users(id) ON DELETE CASCADE,
                               title_keyword VARCHAR(255) NOT NULL,
                               annual_salary DECIMAL(10, 2) NOT NULL,
                               created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index AFTER the table is created (was the bug)
CREATE INDEX idx_salary_lookup_keyword ON salary_lookup(title_keyword);

-- ── SEED: Default salary estimates ────────────────────────
INSERT INTO salary_lookup (title_keyword, annual_salary) VALUES
                                                             ('CEO',                    350000),
                                                             ('CTO',                    300000),
                                                             ('VP Engineering',         280000),
                                                             ('Director Engineering',   230000),
                                                             ('Engineering Manager',    200000),
                                                             ('Principal Engineer',     220000),
                                                             ('Staff Engineer',         200000),
                                                             ('Senior Engineer',        160000),
                                                             ('Software Engineer',      130000),
                                                             ('Junior Engineer',         95000),
                                                             ('Product Manager',        140000),
                                                             ('Senior Product Manager', 170000),
                                                             ('Designer',               110000),
                                                             ('Senior Designer',        140000),
                                                             ('Data Scientist',         145000),
                                                             ('Data Analyst',           100000),
                                                             ('Marketing',               90000),
                                                             ('Sales',                   85000),
                                                             ('Recruiter',               85000),
                                                             ('Default',                100000);