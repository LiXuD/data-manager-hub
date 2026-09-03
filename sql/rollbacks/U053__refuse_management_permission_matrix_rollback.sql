-- Permission vocabulary is an additive security contract. Removing it would
-- make a downgraded application fail open, so rollback is intentionally refused.
DO $$
BEGIN
    RAISE EXCEPTION 'V053 is forward-only; restore the application at a compatible revision instead of removing permissions';
END $$;
