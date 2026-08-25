-- V047 protects immutable artifact, execution and billing history. Removing the guards would make
-- already-recorded facts unverifiable, so in-place rollback is intentionally refused.
-- Recovery procedure: stop writers, restore a verified pre-V047 full backup into a new database,
-- validate Liquibase checksums and connector history, then atomically switch the application; or
-- deploy a new forward migration that preserves every historical fact while correcting the defect.
DO $$
BEGIN
    RAISE EXCEPTION 'V047 rollback refused: restore a verified pre-V047 backup or apply forward recovery';
END $$;
