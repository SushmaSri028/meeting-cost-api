-- V5__add_microsoft_auth.sql
-- Add Microsoft OAuth2 support and provider tracking

ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(20) DEFAULT 'GOOGLE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_access_token  TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_refresh_token TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS microsoft_token_expiry  TIMESTAMP;

-- Update existing Google users
UPDATE users SET provider = 'GOOGLE' WHERE provider IS NULL;
