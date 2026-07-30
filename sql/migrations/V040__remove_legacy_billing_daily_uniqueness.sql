-- The legacy projection allowed only one row per caller and day. The current
-- mapper aggregates independently by tenant, caller, vendor and data type, so
-- retaining that constraint rejects otherwise valid second aggregates.
DO $$
DECLARE
    legacy_constraint RECORD;
BEGIN
    FOR legacy_constraint IN
        SELECT constraint_info.conname
        FROM pg_constraint constraint_info
        WHERE constraint_info.conrelid = 'public.billing_daily'::regclass
          AND constraint_info.contype = 'u'
          AND pg_get_constraintdef(constraint_info.oid) = 'UNIQUE (caller_id, billing_date)'
    LOOP
        EXECUTE format(
            'ALTER TABLE public.billing_daily DROP CONSTRAINT %I',
            legacy_constraint.conname
        );
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        WHERE constraint_info.conrelid = 'public.billing_daily'::regclass
          AND constraint_info.contype = 'u'
          AND pg_get_constraintdef(constraint_info.oid) = 'UNIQUE (caller_id, billing_date)'
    ) THEN
        RAISE EXCEPTION 'billing_daily 旧版 caller/day 唯一约束删除失败';
    END IF;
END $$;
