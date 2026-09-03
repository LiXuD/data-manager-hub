-- V060__separate_call_record_cache_payload.sql
-- Keep the sanitized audit payload separate from the exact payload used for cache replay.
-- Existing response_data values are intentionally not backfilled: they may already be sanitized.

ALTER TABLE call_record
    ADD COLUMN IF NOT EXISTS cache_response_data JSONB;

COMMENT ON COLUMN call_record.cache_response_data IS
    '未经脱敏的成功缓存回放载荷；仅由授权缓存路径读取，普通调用记录查询不选取该列';
