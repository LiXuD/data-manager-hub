-- V053 seeded API Key permissions under the tenant-view row's historical ID.
-- Resolve the parent by permission code so the catalogue hierarchy is stable
-- across fresh and upgraded databases.
UPDATE permission AS child
SET parent_id = parent.id,
    updated_at = CURRENT_TIMESTAMP
FROM permission AS parent
WHERE parent.permission_code = 'caller:view'
  AND child.permission_code IN (
      'apikey:view', 'apikey:add', 'apikey:edit', 'apikey:delete'
  )
  AND child.parent_id IS DISTINCT FROM parent.id;
