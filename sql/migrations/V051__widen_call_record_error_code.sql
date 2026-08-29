-- Connector error categories are stable platform codes, not HTTP status values.
-- The longest built-in category is TRANSPORT_CONNECTION_ERROR (26 chars), so
-- the legacy VARCHAR(20) column could drop the call-record event after a real
-- circuit-breaker/failover failure.
ALTER TABLE call_record
    ALTER COLUMN error_code TYPE VARCHAR(64);
