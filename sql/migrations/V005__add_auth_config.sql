-- 为 vendor_config 表添加认证配置字段
-- 支持多种认证方式：无认证、Basic Auth、Bearer Token、API Key

ALTER TABLE vendor_config
ADD COLUMN IF NOT EXISTS auth_type VARCHAR(20) DEFAULT 'NONE' COMMENT '认证类型: NONE, BASIC, BEARER, API_KEY';

ALTER TABLE vendor_config
ADD COLUMN IF NOT EXISTS auth_config TEXT COMMENT 'JSON格式认证配置';

-- 添加注释说明
COMMENT ON COLUMN vendor_config.auth_type IS '认证类型: NONE-无认证, BASIC-Basic Auth, BEARER-Bearer Token, API_KEY-API密钥';
COMMENT ON COLUMN vendor_config.auth_config IS 'JSON格式认证配置，包含用户名密码、Token或API Key等信息';

-- 示例 auth_config JSON 格式:
-- Basic Auth: {"username": "user", "password": "pass"}
-- Bearer Token: {"token": "${token}"}
-- API Key: {"apiKeyName": "X-API-Key", "apiKeyValue": "${apiKey}", "apiKeyLocation": "header"}
