-- Complete the columns and conflict target used by BillingDailyMapper for
-- databases adopted from the legacy daily billing projection.
ALTER TABLE public.billing_daily
    ADD COLUMN IF NOT EXISTS avg_latency INTEGER;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.billing_daily
        WHERE tenant_id IS NOT NULL
          AND caller_id IS NOT NULL
          AND vendor_id IS NOT NULL
          AND data_type IS NOT NULL
          AND billing_date IS NOT NULL
        GROUP BY tenant_id, caller_id, vendor_id, data_type, billing_date
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'billing_daily 存在重复运行时汇总键，拒绝创建唯一索引';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_daily_runtime_projection
    ON public.billing_daily(tenant_id, caller_id, vendor_id, data_type, billing_date);

COMMENT ON COLUMN public.billing_daily.avg_latency IS '平均调用耗时（毫秒）';
