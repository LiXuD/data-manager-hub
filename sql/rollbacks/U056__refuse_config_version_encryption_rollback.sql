-- Encryption metadata is part of the configuration history contract. Removing
-- it would make rollback restore plaintext/ciphertext with the wrong mode.
DO $$
BEGIN
    RAISE EXCEPTION 'V056 is forward-only; do not remove config_version.is_encrypted';
END $$;
