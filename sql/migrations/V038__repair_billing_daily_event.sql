-- Repair legacy databases whose baseline was adopted after billing_daily
-- existed but before the idempotency ledger was added to init.sql.
CREATE TABLE IF NOT EXISTS public.billing_daily_event (
    request_id VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE public.billing_daily_event IS '日账单投影幂等事件记录表';
COMMENT ON COLUMN public.billing_daily_event.request_id IS '已投影的调用请求唯一标识';
COMMENT ON COLUMN public.billing_daily_event.created_at IS '事件首次投影时间';

DO $$
BEGIN
    IF to_regclass('public.billing_daily_event') IS NULL THEN
        RAISE EXCEPTION 'billing_daily_event 创建失败';
    END IF;
END $$;
