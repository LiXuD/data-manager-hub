-- The legacy baseline defined operation_log.method/operation more narrowly
-- than the current runtime contract. Fully-qualified controller method names
-- exceed 50 characters and otherwise make remote audit persistence fail.
ALTER TABLE operation_log
    ALTER COLUMN method TYPE VARCHAR(200),
    ALTER COLUMN operation TYPE VARCHAR(200);
