BEGIN;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.users
    DROP CONSTRAINT IF EXISTS ck_users_auth_version_nonnegative;
ALTER TABLE public.users
    ADD CONSTRAINT ck_users_auth_version_nonnegative CHECK (auth_version >= 0);

COMMIT;
