INSERT INTO billing_plan (
  plan_code, version, plan_name, vendor_id, vendor_code, vendor_name,
  interface_id, interface_code, interface_name, template_code,
  accounting_purpose, currency, timezone, settlement_cycle,
  pricing_config, metering_config, adjustment_config, status, effective_from,
  created_at, updated_at
)
SELECT
  :'plan_code', 1, 'Connector E2E 按次计费方案', vi.id,
  vi.vendor_code, vi.vendor_name, ai.id, ai.interface_code, ai.interface_name,
  'PER_CALL', 'VENDOR_PAYABLE', 'CNY', 'Asia/Shanghai', 'MONTH',
  '{"unitPrice":0.25,"packageFee":0,"includedUnits":0,"overageUnitPrice":0,"tierMode":"GRADUATED","durationUnit":"SECOND","durationRounding":"CEILING","carryOver":false}',
  '{"logic":"AND","conditions":[],"quantity":{"type":"FIXED","fixedValue":1,"unit":"CALL"},"missingFieldPolicy":"PENDING_REVIEW","cacheBillingPolicy":"FREE","aggregationScope":"VENDOR_INTERFACE"}',
  '{"noChargeOnFailure":true,"requireValidContract":false,"slaEnabled":false}',
  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM vendor_config vc
JOIN vendor_info vi ON vi.id = vc.vendor_id
JOIN api_interface ai ON ai.id = vc.interface_id
WHERE vc.vendor_id = :'vendor_id'::bigint
  AND vc.interface_id = :'interface_id'::bigint
RETURNING id;
