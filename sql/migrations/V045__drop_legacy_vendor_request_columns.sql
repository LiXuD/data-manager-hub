-- Destructive cleanup after V044 has made every executable vendor configuration plugin-only.
DO $$
DECLARE
    invalid_ids TEXT;
BEGIN
    SELECT string_agg(config.id::TEXT, ',' ORDER BY config.id)
    INTO invalid_ids
    FROM vendor_config config
    LEFT JOIN vendor_connector_version version
      ON version.id = config.active_connector_version_id
     AND version.vendor_config_id = config.id
     AND version.status = 'ACTIVE'
    WHERE COALESCE(config.deleted, FALSE) = FALSE
      AND (
          config.runtime_mode <> 'PLUGIN'
          OR (config.status = 'active' AND version.id IS NULL)
      );
    IF invalid_ids IS NOT NULL THEN
        RAISE EXCEPTION USING
            MESSAGE = 'V045 blocked: plugin-only vendor invariant is not satisfied: ' || invalid_ids,
            ERRCODE = 'check_violation';
    END IF;
END;
$$;

ALTER TABLE vendor_config
    DROP COLUMN IF EXISTS api_url,
    DROP COLUMN IF EXISTS method,
    DROP COLUMN IF EXISTS header_config,
    DROP COLUMN IF EXISTS request_template,
    DROP COLUMN IF EXISTS response_mapping,
    DROP COLUMN IF EXISTS auth_type,
    DROP COLUMN IF EXISTS auth_config,
    DROP COLUMN IF EXISTS param_mapping;

COMMENT ON TABLE vendor_config IS
    'Vendor identity/routing and platform execution policy; outbound protocol configuration lives in immutable connector versions.';
