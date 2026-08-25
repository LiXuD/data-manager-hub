DO $$
BEGIN
    RAISE EXCEPTION
        'V045 rollback is intentionally refused: retired request columns cannot be reconstructed from mutable data. Restore a verified pre-V045 backup with its matching application release.';
END;
$$;
