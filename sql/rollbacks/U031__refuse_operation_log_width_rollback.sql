DO $$
BEGIN
    RAISE EXCEPTION
        'operation_log width normalization is forward-only; restore a verified backup to roll it back';
END $$;
