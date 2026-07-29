-- V007 created the admin role and granted it all permissions, but did not
-- connect the conventional bootstrap admin account to that role.
INSERT INTO user_role (user_id, role_id, created_at, deleted)
SELECT user_info.id, role_info.id, CURRENT_TIMESTAMP, FALSE
FROM user_info
JOIN role_info ON LOWER(role_info.role_code) = 'admin'
WHERE LOWER(user_info.username) = 'admin'
  AND user_info.status = 'active'
  AND user_info.deleted = FALSE
  AND role_info.status = 'active'
  AND role_info.deleted = FALSE
ON CONFLICT (user_id, role_id) DO UPDATE
SET deleted = FALSE;
