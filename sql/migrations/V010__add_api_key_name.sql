-- V010__add_api_key_name.sql
-- Align api_key schema with ApiKey.keyName entity mapping.

ALTER TABLE api_key ADD COLUMN IF NOT EXISTS key_name VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_apikey_key_name ON api_key(key_name);
