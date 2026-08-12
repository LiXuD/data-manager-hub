-- V048: make interface vendor routing explicit and safe for existing data.
-- The first block is intentionally read-only. Any ambiguity aborts the whole changeset.
DO $$
DECLARE
    invalid_rows TEXT;
BEGIN
    SELECT string_agg(id::TEXT, ',' ORDER BY id)
    INTO invalid_rows
    FROM vendor_config
    WHERE interface_id IS NULL;
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: vendor_config.interface_id is NULL for rows: %', invalid_rows;
    END IF;

    SELECT string_agg(format('%s:%s', interface_id, vendor_id), ',' ORDER BY interface_id, vendor_id)
    INTO invalid_rows
    FROM (
        SELECT interface_id, vendor_id
        FROM vendor_config
        WHERE COALESCE(deleted, FALSE) = FALSE
        GROUP BY interface_id, vendor_id
        HAVING count(*) > 1
    ) duplicate_bindings;
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: duplicate active interface/vendor bindings: %', invalid_rows;
    END IF;

    SELECT string_agg(format('%s:%s', ai.id, ai.vendor_id), ',' ORDER BY ai.id)
    INTO invalid_rows
    FROM api_interface ai
    WHERE ai.vendor_id IS NOT NULL
      AND (
          SELECT count(*)
          FROM vendor_config vc
          WHERE vc.interface_id = ai.id
            AND vc.vendor_id = ai.vendor_id
            AND COALESCE(vc.deleted, FALSE) = FALSE
      ) <> 1;
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: legacy interface vendor does not resolve uniquely: %', invalid_rows;
    END IF;

    SELECT string_agg(format('%s:%s', ai.id, ai.vendor_id), ',' ORDER BY ai.id)
    INTO invalid_rows
    FROM api_interface ai
    JOIN vendor_config primary_config
      ON primary_config.interface_id = ai.id
     AND primary_config.vendor_id = ai.vendor_id
     AND COALESCE(primary_config.deleted, FALSE) = FALSE
    WHERE ai.vendor_id IS NOT NULL
      AND primary_config.fallback_vendor_id IS NOT NULL
      AND primary_config.fallback_vendor_id = ai.vendor_id;
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: legacy primary and fallback vendor are identical: %', invalid_rows;
    END IF;

    SELECT string_agg(format('%s:%s', vc.interface_id, vc.fallback_vendor_id), ','
                      ORDER BY vc.interface_id, vc.fallback_vendor_id)
    INTO invalid_rows
    FROM vendor_config vc
    WHERE vc.fallback_vendor_id IS NOT NULL
      AND (
          SELECT count(*)
          FROM vendor_config fallback_config
          WHERE fallback_config.interface_id = vc.interface_id
            AND fallback_config.vendor_id = vc.fallback_vendor_id
            AND COALESCE(fallback_config.deleted, FALSE) = FALSE
      ) <> 1;
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: legacy fallback vendor does not resolve uniquely: %', invalid_rows;
    END IF;

    SELECT string_agg(ai.id::TEXT, ',' ORDER BY ai.id)
    INTO invalid_rows
    FROM api_interface ai
    WHERE ai.vendor_id IS NULL
      AND EXISTS (
          SELECT 1
          FROM vendor_config vc
          WHERE vc.interface_id = ai.id
            AND vc.fallback_vendor_id IS NOT NULL
      );
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: fallback vendor exists without a legacy primary vendor: %', invalid_rows;
    END IF;

    SELECT string_agg(ai.id::TEXT, ',' ORDER BY ai.id)
    INTO invalid_rows
    FROM api_interface ai
    JOIN vendor_config primary_config
      ON primary_config.interface_id = ai.id
     AND primary_config.vendor_id = ai.vendor_id
     AND COALESCE(primary_config.deleted, FALSE) = FALSE
    WHERE ai.vendor_id IS NOT NULL
      AND EXISTS (
          SELECT 1
          FROM vendor_config other_config
          WHERE other_config.interface_id = ai.id
            AND other_config.fallback_vendor_id IS DISTINCT FROM primary_config.fallback_vendor_id
            AND (other_config.fallback_vendor_id IS NOT NULL
                 OR primary_config.fallback_vendor_id IS NOT NULL)
      );
    IF invalid_rows IS NOT NULL THEN
        RAISE EXCEPTION 'V048 blocked: inconsistent legacy fallback vendors within interface: %', invalid_rows;
    END IF;
END;
$$;

ALTER TABLE api_interface
    ADD COLUMN IF NOT EXISTS primary_vendor_config_id BIGINT,
    ADD COLUMN IF NOT EXISTS fallback_vendor_config_id BIGINT;

COMMENT ON COLUMN api_interface.primary_vendor_config_id IS
    'Explicit primary vendor_config binding for this interface';
COMMENT ON COLUMN api_interface.fallback_vendor_config_id IS
    'Explicit fallback vendor_config binding for this interface';

-- Backfill only from a uniquely validated legacy route. Interfaces without a legacy
-- vendor remain UNBOUND and must be configured through the new API.
UPDATE api_interface ai
SET primary_vendor_config_id = primary_config.id,
    fallback_vendor_config_id = fallback_config.id
FROM vendor_config primary_config
LEFT JOIN vendor_config fallback_config
  ON fallback_config.interface_id = primary_config.interface_id
 AND fallback_config.vendor_id = primary_config.fallback_vendor_id
 AND COALESCE(fallback_config.deleted, FALSE) = FALSE
WHERE ai.vendor_id IS NOT NULL
  AND primary_config.interface_id = ai.id
  AND primary_config.vendor_id = ai.vendor_id
  AND COALESCE(primary_config.deleted, FALSE) = FALSE;

ALTER TABLE vendor_config
    ALTER COLUMN interface_id SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.vendor_config'::regclass
          AND conname = 'vendor_config_vendor_id_data_type_id_key'
    ) THEN
        ALTER TABLE vendor_config DROP CONSTRAINT vendor_config_vendor_id_data_type_id_key;
    ELSIF to_regclass('public.vendor_config_vendor_id_data_type_id_key') IS NOT NULL THEN
        DROP INDEX public.vendor_config_vendor_id_data_type_id_key;
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_vendor_config_interface_vendor_active_v048
    ON vendor_config(interface_id, vendor_id)
    WHERE COALESCE(deleted, FALSE) = FALSE;

CREATE INDEX IF NOT EXISTS idx_api_interface_primary_vendor_config_v048
    ON api_interface(primary_vendor_config_id);
CREATE INDEX IF NOT EXISTS idx_api_interface_fallback_vendor_config_v048
    ON api_interface(fallback_vendor_config_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.vendor_config'::regclass
          AND conname = 'fk_vendor_config_interface_v048'
    ) THEN
        ALTER TABLE vendor_config
            ADD CONSTRAINT fk_vendor_config_interface_v048
            FOREIGN KEY (interface_id) REFERENCES api_interface(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.vendor_config'::regclass
          AND conname = 'uq_vendor_config_id_interface_v048'
    ) THEN
        ALTER TABLE vendor_config
            ADD CONSTRAINT uq_vendor_config_id_interface_v048 UNIQUE (id, interface_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.api_interface'::regclass
          AND conname = 'fk_api_interface_primary_vendor_config_v048'
    ) THEN
        ALTER TABLE api_interface
            ADD CONSTRAINT fk_api_interface_primary_vendor_config_v048
            FOREIGN KEY (primary_vendor_config_id, id)
            REFERENCES vendor_config(id, interface_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.api_interface'::regclass
          AND conname = 'fk_api_interface_fallback_vendor_config_v048'
    ) THEN
        ALTER TABLE api_interface
            ADD CONSTRAINT fk_api_interface_fallback_vendor_config_v048
            FOREIGN KEY (fallback_vendor_config_id, id)
            REFERENCES vendor_config(id, interface_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.api_interface'::regclass
          AND conname = 'ck_api_interface_vendor_routing_distinct_v048'
    ) THEN
        ALTER TABLE api_interface
            ADD CONSTRAINT ck_api_interface_vendor_routing_distinct_v048
            CHECK (primary_vendor_config_id IS NULL
                   OR fallback_vendor_config_id IS NULL
                   OR primary_vendor_config_id <> fallback_vendor_config_id);
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION enforce_api_interface_vendor_routing_v048()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    config_interface_id BIGINT;
    config_deleted BOOLEAN;
BEGIN
    IF NEW.primary_vendor_config_id IS NOT NULL THEN
        SELECT interface_id, deleted
        INTO config_interface_id, config_deleted
        FROM vendor_config
        WHERE id = NEW.primary_vendor_config_id;
        IF NOT FOUND OR config_interface_id <> NEW.id OR COALESCE(config_deleted, FALSE) THEN
            RAISE EXCEPTION 'V048 primary vendor_config must be active and belong to interface %', NEW.id;
        END IF;
    END IF;

    IF NEW.fallback_vendor_config_id IS NOT NULL THEN
        SELECT interface_id, deleted
        INTO config_interface_id, config_deleted
        FROM vendor_config
        WHERE id = NEW.fallback_vendor_config_id;
        IF NOT FOUND OR config_interface_id <> NEW.id OR COALESCE(config_deleted, FALSE) THEN
            RAISE EXCEPTION 'V048 fallback vendor_config must be active and belong to interface %', NEW.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_api_interface_vendor_routing_v048 ON api_interface;
CREATE TRIGGER trg_api_interface_vendor_routing_v048
    BEFORE INSERT OR UPDATE OF primary_vendor_config_id, fallback_vendor_config_id
    ON api_interface
    FOR EACH ROW EXECUTE FUNCTION enforce_api_interface_vendor_routing_v048();

CREATE OR REPLACE FUNCTION protect_referenced_vendor_config_routing_v048()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       OR (NEW.deleted IS TRUE AND OLD.deleted IS DISTINCT FROM TRUE)
       OR NEW.interface_id IS DISTINCT FROM OLD.interface_id THEN
        IF EXISTS (
            SELECT 1 FROM api_interface
            WHERE primary_vendor_config_id = OLD.id
               OR fallback_vendor_config_id = OLD.id
        ) THEN
            RAISE EXCEPTION 'V048 vendor_config % is referenced by interface routing', OLD.id;
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_referenced_vendor_config_routing_v048 ON vendor_config;
CREATE TRIGGER trg_protect_referenced_vendor_config_routing_v048
    BEFORE UPDATE OF deleted, interface_id OR DELETE
    ON vendor_config
    FOR EACH ROW EXECUTE FUNCTION protect_referenced_vendor_config_routing_v048();

ALTER TABLE api_interface ALTER COLUMN status SET DEFAULT 'inactive';

COMMENT ON INDEX ux_vendor_config_interface_vendor_active_v048 IS
    'Only one non-deleted vendor binding per interface and vendor';
