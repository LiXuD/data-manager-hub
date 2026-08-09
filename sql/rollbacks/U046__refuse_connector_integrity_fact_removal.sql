DO $$
BEGIN
    RAISE EXCEPTION
        'Rollback refused: connector integrity columns contain immutable historical call and billing facts';
END $$;

