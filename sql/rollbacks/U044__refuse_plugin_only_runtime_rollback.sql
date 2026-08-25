DO $$
BEGIN
    RAISE EXCEPTION
        'V044 rollback is intentionally refused: restoring LEGACY data would require retired executable code. Restore a verified pre-V044 backup with the matching application release instead.';
END;
$$;
