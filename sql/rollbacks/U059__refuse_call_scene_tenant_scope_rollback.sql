-- Tenant ownership is part of the call-scene authorization contract. Reversing
-- it would re-open cross-tenant reads, so recovery must use a compatible backup
-- or a new forward migration.
DO $$
BEGIN
    RAISE EXCEPTION 'V059 is forward-only; do not remove call_scene tenant ownership';
END $$;
