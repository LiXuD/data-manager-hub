-- Legacy init.sql creates user_role without the audit and soft-delete fields
-- used by the current runtime entity and by bootstrap role repair.
ALTER TABLE user_role
    ADD COLUMN IF NOT EXISTS created_by BIGINT,
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN;

UPDATE user_role
SET deleted = FALSE
WHERE deleted IS NULL;

ALTER TABLE user_role
    ALTER COLUMN deleted SET DEFAULT FALSE,
    ALTER COLUMN deleted SET NOT NULL;
