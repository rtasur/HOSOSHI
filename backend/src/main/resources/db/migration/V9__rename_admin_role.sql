-- Rename the legacy ADMIN role to the final prototype role name.
-- Preserve the existing role/user relationship when upgrading an older local database.
DO $$
DECLARE
    old_role_id UUID;
    new_role_id UUID;
BEGIN
    SELECT id INTO old_role_id FROM roles WHERE code = 'ADMIN' LIMIT 1;
    SELECT id INTO new_role_id FROM roles WHERE code = 'SUPER_ADMIN' LIMIT 1;

    IF old_role_id IS NOT NULL AND new_role_id IS NULL THEN
        UPDATE roles
        SET code = 'SUPER_ADMIN',
            display_name = 'Super Admin',
            description = 'Full platform administration'
        WHERE id = old_role_id;
    ELSIF old_role_id IS NOT NULL AND new_role_id IS NOT NULL THEN
        UPDATE user_roles
        SET role_id = new_role_id
        WHERE role_id = old_role_id
          AND NOT EXISTS (
              SELECT 1
              FROM user_roles ur
              WHERE ur.user_id = user_roles.user_id
                AND ur.role_id = new_role_id
          );
        DELETE FROM user_roles WHERE role_id = old_role_id;
        DELETE FROM roles WHERE id = old_role_id;
    END IF;
END $$;

UPDATE app_users
SET requested_role = 'SUPER_ADMIN'
WHERE requested_role = 'ADMIN';
