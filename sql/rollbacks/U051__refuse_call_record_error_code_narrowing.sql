-- Narrowing error_code would make existing runtime evidence unwriteable.
DO $$
BEGIN
    RAISE EXCEPTION
        'U051 refused: call_record.error_code width is part of the persisted connector error contract';
END $$;
