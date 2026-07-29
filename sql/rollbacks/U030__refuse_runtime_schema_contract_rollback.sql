-- V030 is an additive forward-recovery migration for databases whose legacy
-- columns and Liquibase history diverged. Once services write to the repaired
-- columns/tables, dropping them cannot be proven safe. Restore the pre-update
-- backup if the old physical schema must be recovered.

DO $$
BEGIN
    RAISE EXCEPTION
        'V030 is a forward-only runtime schema repair; restore the pre-update backup to revert it';
END $$;

