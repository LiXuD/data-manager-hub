DO $$
BEGIN
    RAISE EXCEPTION
        'user_role runtime contract normalization is forward-only; restore a verified backup to roll it back';
END $$;
