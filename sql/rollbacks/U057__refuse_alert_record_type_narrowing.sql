-- The widened alert type is part of the persisted alert contract and is forward-only.
DO $$
BEGIN
    RAISE EXCEPTION 'V057 is forward-only; do not narrow alert_record.alert_type';
END $$;
