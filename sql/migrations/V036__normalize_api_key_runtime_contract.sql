-- Normalize pre-Liquibase API-key schemas to the current runtime contract.
-- Older databases used key_value/expires_at. Keeping the legacy NOT NULL
-- key_value column makes current INSERT statements fail even after api_key was
-- added, because the service no longer writes the legacy column.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'api_key'
          AND column_name = 'key_value'
    ) THEN
        EXECUTE $sql$
            UPDATE public.api_key
            SET api_key = key_value
            WHERE api_key IS NULL OR BTRIM(api_key) = ''
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'api_key'
          AND column_name = 'expires_at'
    ) THEN
        EXECUTE $sql$
            UPDATE public.api_key
            SET expire_time = expires_at
            WHERE expire_time IS NULL
        $sql$;
    END IF;
END $$;

DO $$
DECLARE
    invalid_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO invalid_count
    FROM public.api_key
    WHERE api_key IS NULL OR BTRIM(api_key) = '';

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'api_key 中仍有 % 条记录缺少规范 API Key，拒绝删除兼容字段', invalid_count;
    END IF;

    SELECT COUNT(*)
    INTO invalid_count
    FROM public.api_key
    WHERE LENGTH(api_key) > 64;

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'api_key 中有 % 条记录超过 64 字符，拒绝收窄字段长度', invalid_count;
    END IF;

    SELECT COUNT(*)
    INTO invalid_count
    FROM (
        SELECT api_key
        FROM public.api_key
        GROUP BY api_key
        HAVING COUNT(*) > 1
    ) duplicates;

    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'api_key 中有 % 组重复 API Key，拒绝创建唯一索引', invalid_count;
    END IF;
END $$;

-- Drop the obsolete columns only after their values have been copied and
-- validated. Any old index named idx_api_key that still targets key_value is
-- removed with that column, allowing the canonical index to be recreated.
ALTER TABLE public.api_key
    DROP COLUMN IF EXISTS key_value,
    DROP COLUMN IF EXISTS expires_at;

ALTER TABLE public.api_key
    ALTER COLUMN api_key TYPE VARCHAR(64),
    ALTER COLUMN api_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_api_key ON public.api_key(api_key);

