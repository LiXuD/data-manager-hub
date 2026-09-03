-- Billing reconciliation uses a descriptive alert type longer than the legacy
-- VARCHAR(20) contract. Preserve the domain value instead of truncating it.
ALTER TABLE alert_record
    ALTER COLUMN alert_type TYPE VARCHAR(50);
