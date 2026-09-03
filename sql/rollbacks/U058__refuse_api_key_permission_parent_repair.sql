-- The API Key permission hierarchy is a published catalogue contract.
DO $$
BEGIN
    RAISE EXCEPTION 'V058 is forward-only; do not restore ID-dependent permission parents';
END $$;
