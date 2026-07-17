ALTER TABLE api_key
    ADD COLUMN IF NOT EXISTS rate_limit_enabled BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN api_key.rate_limit_enabled IS '是否启用 API Key 每分钟限流';
COMMENT ON COLUMN api_key.rate_limit IS 'API Key 每分钟最大请求数';
