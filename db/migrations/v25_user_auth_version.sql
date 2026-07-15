BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_auth_version_nonnegative;
ALTER TABLE users
    ADD CONSTRAINT ck_users_auth_version_nonnegative CHECK (auth_version >= 0);

COMMIT;
