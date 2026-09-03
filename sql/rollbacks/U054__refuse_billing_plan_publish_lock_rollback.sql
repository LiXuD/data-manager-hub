-- The lock table is part of the billing consistency contract. Removing it
-- would re-open the concurrent first-publish race, so this migration is
-- intentionally forward-only.
DO $$
BEGIN
    RAISE EXCEPTION 'V054 is forward-only; do not remove billing_plan_publish_lock';
END $$;
