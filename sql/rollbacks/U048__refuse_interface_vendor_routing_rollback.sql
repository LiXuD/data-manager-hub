-- V048 changes routing identity and replaces a legacy uniqueness rule. Reversing it
-- in place could discard explicit route choices or fail when new bindings exist.
DO $$
BEGIN
    RAISE EXCEPTION
        'V048 rollback refused: restore a verified pre-V048 backup or apply a forward recovery migration';
END;
$$;
