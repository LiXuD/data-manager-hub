-- Preserve whether each configuration version contains plaintext or ciphertext.
-- Older rows have NULL metadata and are conservatively classified from the
-- supported ciphertext envelope by the runtime when they are read or restored.
ALTER TABLE config_version
    ADD COLUMN IF NOT EXISTS is_encrypted BOOLEAN;
