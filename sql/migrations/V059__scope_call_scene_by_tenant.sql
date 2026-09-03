-- Scope call-scene ownership to a tenant before the application starts using it.
-- Existing global rows cannot be assigned safely from scene_code alone. Stop the
-- migration instead of fabricating ownership or leaving a cross-tenant path.
ALTER TABLE call_scene
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM call_scene WHERE tenant_id IS NULL) THEN
        RAISE EXCEPTION 'V059 requires explicit tenant ownership for every existing call_scene row';
    END IF;
END $$;

ALTER TABLE call_scene
    ALTER COLUMN tenant_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.call_scene'::regclass
          AND conname = 'fk_call_scene_tenant'
    ) THEN
        ALTER TABLE call_scene
            ADD CONSTRAINT fk_call_scene_tenant
            FOREIGN KEY (tenant_id) REFERENCES tenant_info(id);
    END IF;
END $$;

-- Remove every legacy single-column uniqueness form, not only PostgreSQL's
-- default constraint name. A differently named constraint or standalone
-- unique index must not continue to prevent the tenant-scoped key below.
DO $$
DECLARE
    scene_code_attnum SMALLINT;
    constraint_name TEXT;
    index_name TEXT;
BEGIN
    SELECT attnum
    INTO scene_code_attnum
    FROM pg_attribute
    WHERE attrelid = 'public.call_scene'::regclass
      AND attname = 'scene_code'
      AND NOT attisdropped;

    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.call_scene'::regclass
          AND contype = 'u'
          AND conkey = ARRAY[scene_code_attnum]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE public.call_scene DROP CONSTRAINT %I', constraint_name);
    END LOOP;

    FOR index_name IN
        SELECT index_class.relname
        FROM pg_index index_metadata
        JOIN pg_class index_class ON index_class.oid = index_metadata.indexrelid
        WHERE index_metadata.indrelid = 'public.call_scene'::regclass
          AND index_metadata.indisunique
          AND index_metadata.indnkeyatts = 1
          AND index_metadata.indkey[0] = scene_code_attnum
          AND NOT EXISTS (
              SELECT 1
              FROM pg_constraint
              WHERE conindid = index_metadata.indexrelid
          )
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS public.%I', index_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_call_scene_tenant_code
    ON call_scene(tenant_id, scene_code);

CREATE INDEX IF NOT EXISTS idx_call_scene_tenant_status
    ON call_scene(tenant_id, status);

COMMENT ON COLUMN call_scene.tenant_id IS
    'Owning tenant; every newly managed or runtime-resolved scene is tenant-scoped';
