CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM billing_plan left_plan
    JOIN billing_plan right_plan
      ON left_plan.id < right_plan.id
     AND left_plan.vendor_id = right_plan.vendor_id
     AND left_plan.interface_id = right_plan.interface_id
     AND left_plan.accounting_purpose = right_plan.accounting_purpose
     AND left_plan.status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
     AND right_plan.status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW')
     AND (left_plan.effective_to IS NULL OR right_plan.effective_from < left_plan.effective_to)
     AND (right_plan.effective_to IS NULL OR left_plan.effective_from < right_plan.effective_to)
  ) THEN
    RAISE EXCEPTION 'billing_plan contains overlapping effective windows; resolve them before applying V041';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'public.billing_plan'::regclass
      AND conname = 'ex_billing_plan_effective_window'
  ) THEN
    ALTER TABLE billing_plan
      ADD CONSTRAINT ex_billing_plan_effective_window
      EXCLUDE USING gist (
        vendor_id WITH =,
        interface_id WITH =,
        accounting_purpose WITH =,
        tsrange(effective_from, COALESCE(effective_to, 'infinity'::timestamp), '[)') WITH &&
      )
      WHERE (status IN ('PUBLISHED', 'ACTIVE', 'NEEDS_REVIEW'));
  END IF;
END $$;

COMMENT ON CONSTRAINT ex_billing_plan_effective_window ON billing_plan IS
  '同一厂商、接口、计费方向的已发布方案采用左闭右开区间且禁止重叠';
