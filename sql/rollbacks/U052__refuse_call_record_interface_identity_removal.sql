-- Removing interface_id would make connector migration observations ambiguous
-- and would discard the canonical identity of persisted runtime evidence.
DO $$
BEGIN
    RAISE EXCEPTION
        'U052 refused: call_record.interface_id is part of the persisted connector migration evidence contract';
END $$;
