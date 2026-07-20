-- V008__create_current_call_record_partitions.sql
-- Keep call_record writable as time advances by creating current and next month partitions.

CREATE OR REPLACE FUNCTION create_monthly_partition(partition_date DATE)
RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    start_date := DATE_TRUNC('month', partition_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'call_record_' || TO_CHAR(start_date, 'YYYY_MM');

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF call_record FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
END;
$$ LANGUAGE plpgsql;

SELECT create_monthly_partition(CURRENT_DATE::DATE);
SELECT create_monthly_partition((CURRENT_DATE + INTERVAL '1 month')::DATE);
