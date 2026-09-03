-- A durable row per billing publish business key gives PostgreSQL a lock fact
-- even when no published plan exists yet. This closes the empty-table race
-- between two concurrent first publishes for the same vendor/interface/direction.
CREATE TABLE IF NOT EXISTS billing_plan_publish_lock (
    vendor_id BIGINT NOT NULL,
    interface_id BIGINT NOT NULL,
    accounting_purpose VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (vendor_id, interface_id, accounting_purpose)
);

COMMENT ON TABLE billing_plan_publish_lock IS
    'Durable serialization facts for billing-plan publish business keys';
