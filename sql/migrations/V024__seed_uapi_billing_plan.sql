-- UAPI 示例接口免费，直接创建新版零元方案以闭合真实调用链。
-- 单独放在 V024，确保 vendor_info、api_interface 与新版计费表均已创建。
INSERT INTO billing_plan(
    plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
    interface_id, interface_code, interface_name, template_code,
    accounting_purpose, currency, timezone, settlement_cycle,
    pricing_config, metering_config, adjustment_config, status, effective_from,
    created_at, updated_at
)
SELECT
    'UAPI-PROGRAMMER-HISTORY-TODAY', 1, 'UAPI 程序员历史免费方案', vi.id,
    vi.vendor_code, vi.vendor_name, ai.id, ai.interface_code, ai.interface_name,
    'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
    '{"unitPrice":0,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
    '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
    '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_info vi
JOIN api_interface ai ON ai.vendor_id = vi.id
WHERE vi.vendor_code = 'uapi'
  AND ai.interface_code = 'PROGRAMMER_HISTORY_TODAY'
  AND COALESCE(ai.deleted, false) = false
ON CONFLICT (plan_code, version) DO NOTHING;
