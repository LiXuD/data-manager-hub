-- 计费规则改为按“厂商 + 接口”唯一匹配。
-- data_type 保留为历史/统计快照，不再参与定价查询。

ALTER TABLE billing_rule
    ADD COLUMN IF NOT EXISTS interface_id BIGINT,
    ADD COLUMN IF NOT EXISTS interface_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS interface_name VARCHAR(100);

-- 仅自动迁移能唯一映射到一个接口的旧规则，避免将类型规则错误地绑定到多个接口。
WITH unique_interface AS (
    SELECT br.id AS rule_id,
           br.vendor_id,
           MIN(ai.id) AS interface_id,
           MIN(ai.interface_code) AS interface_code,
           MIN(ai.interface_name) AS interface_name
    FROM billing_rule br
    JOIN data_type dt ON dt.data_type_code = br.data_type
    JOIN api_interface ai
      ON ai.vendor_id = br.vendor_id
     AND ai.data_type_id = dt.id
     AND COALESCE(ai.deleted, false) = false
    WHERE br.interface_id IS NULL
      AND br.vendor_id IS NOT NULL
    GROUP BY br.id
    HAVING COUNT(*) = 1
), ranked_rule AS (
    SELECT ui.*,
           ROW_NUMBER() OVER (
               PARTITION BY ui.vendor_id, ui.interface_id
               ORDER BY CASE WHEN br.status = 'active' THEN 0 ELSE 1 END,
                        br.created_at DESC NULLS LAST,
                        br.id DESC
           ) AS row_num
    FROM unique_interface ui
    JOIN billing_rule br ON br.id = ui.rule_id
)
UPDATE billing_rule br
SET interface_id = rr.interface_id,
    interface_code = rr.interface_code,
    interface_name = rr.interface_name,
    updated_at = CURRENT_TIMESTAMP
FROM ranked_rule rr
WHERE br.id = rr.rule_id
  AND rr.row_num = 1;

-- 无法唯一映射的旧类型规则必须停用，防止继续按数据类型定价。
UPDATE billing_rule
SET status = 'inactive',
    updated_at = CURRENT_TIMESTAMP
WHERE interface_id IS NULL
  AND status = 'active';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_billing_rule_active_interface'
          AND conrelid = 'billing_rule'::regclass
    ) THEN
        ALTER TABLE billing_rule
            ADD CONSTRAINT chk_billing_rule_active_interface
            CHECK (status <> 'active' OR (
                vendor_id IS NOT NULL
                AND interface_id IS NOT NULL
                AND interface_code IS NOT NULL
            ));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_rule_vendor_interface
    ON billing_rule(vendor_id, interface_id)
    WHERE vendor_id IS NOT NULL AND interface_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_billing_rule_interface_code
    ON billing_rule(interface_code);

COMMENT ON COLUMN billing_rule.interface_id IS '接口ID，与vendor_id共同唯一确定计费规则';
COMMENT ON COLUMN billing_rule.interface_code IS '接口编码快照，用于跨服务按编码匹配';
COMMENT ON COLUMN billing_rule.interface_name IS '接口名称快照';
COMMENT ON COLUMN billing_rule.data_type IS '接口数据类型快照，仅用于统计展示，不参与定价匹配';
