# GitHub Actions deployment configuration

## Frontend repository

Repository variables:

- `NEXT_PUBLIC_GOOGLE_CLIENT_ID`
- `NEXT_PUBLIC_KAKAO_REST_API_KEY`

Repository secrets:

- `CI_GITHUB_TOKEN`: fine-grained PAT targeting `rktclgh/ieum_BE` with
  repository Contents write permission, used only for `repository_dispatch`.

The `frontend-updated` dispatch must include the commit that produced the
frontend export in `client_payload.frontend_sha`. The value must be the full
40-character lowercase Git commit SHA. The backend workflow checks out exactly
that SHA and verifies it again after checkout. Backend push and manual runs use
the frontend `main` branch as their fallback source.

The app-main deployment performs one final comparison with the current
frontend `main` SHA immediately before SSH deployment. A newer frontend commit
therefore makes an older run fail closed instead of publishing stale assets.
Production deployment concurrency uses `cancel-in-progress: false`, so an
in-flight migration or deployment is never interrupted by a newer run.

## Backend repository

Repository secrets:

- `CI_GITHUB_TOKEN`: token that can read `rktclgh/ieum_FE` when the
  frontend repository is private.
- `DOCKERHUB_USERNAME`: Docker Hub account or organization name.
- `DOCKERHUB_TOKEN`: Docker Hub access token with read/write permission.

Create private Docker Hub repositories named `ieum-app-main` and `ieum-app-ai`.

Create both GitHub Environments before enabling the workflows.

### `app-main-production`

Variables:

- `SSH_HOST=54.116.123.11`
- `SSH_USER=ubuntu`
- `SSH_PORT=22`
- `DEPLOY_PATH=/home/ubuntu/ieum/app-main`
- `APP_MAIN_PRIVATE_BIND_ADDRESS=172.31.38.97`
- `LETSENCRYPT_EMAIL`: optional when the origin certificate already exists;
  required only for initial Let's Encrypt issuance
- `NEXT_PUBLIC_GOOGLE_CLIENT_ID`
- `NEXT_PUBLIC_KAKAO_REST_API_KEY`

Secrets:

- `SSH_PRIVATE_KEY`: complete PEM contents
- `SSH_KNOWN_HOSTS`: verified known_hosts line for `54.116.123.11`
- `PGHOST`: production PostgreSQL host used by the migration helper
- `PGPORT`: production PostgreSQL port, normally `5432`
- `PGDATABASE`: production PostgreSQL database name
- `PGUSER`: production PostgreSQL migration user
- `PGPASSWORD`: password for the migration user; it is passed through the
  standard libpq environment and never placed in a command-line argument
- `APP_MAIN_ENV_FILE`: completed `deploy/env/app-main.env.example`; its
  `SPRING_DATASOURCE_URL` must use the production database host, never
  `localhost`, `127.0.0.1`, or the example placeholder, and it must include
  `INQUIRY_ADMIN_EMAIL`

### `app-ai-production`

Variables:

- `SSH_HOST=54.116.69.21`
- `SSH_USER=ubuntu`
- `SSH_PORT=22`
- `DEPLOY_PATH=/home/ubuntu/ieum/app-ai`
- `APP_AI_BIND_ADDRESS=172.31.33.42`

Secrets:

- `SSH_PRIVATE_KEY`: complete PEM contents
- `SSH_KNOWN_HOSTS`: verified known_hosts line for `54.116.69.21`
- `PGHOST`: the same production PostgreSQL host configured for app-main
- `PGPORT`: the same production PostgreSQL port configured for app-main
- `PGDATABASE`: the same production PostgreSQL database configured for app-main
- `PGUSER`: production PostgreSQL migration user
- `PGPASSWORD`: password for the migration user
- `APP_AI_ENV_FILE`: completed `deploy/env/app-ai.env.example`

Both binary workflows run `deploy/scripts/apply-admin-dashboard-migrations.sh`
before either application binary is built. Missing libpq connection values,
an unavailable `psql`, a schema mismatch, or any migration error stops the
workflow before image build or SSH deployment. The GitHub workflows use the
`PGPASSWORD` environment secret; controlled manual execution may instead use a
permission-restricted libpq `.pgpass` file.

Generate candidate host-key lines with `ssh-keyscan -H <host>`, but verify the
fingerprint through AWS or another trusted channel before saving the result as
`SSH_KNOWN_HOSTS`. The workflow never disables host-key verification.

The app-main workflow installs an Nginx reverse proxy for
`https://ieum.rktclgh.site` and keeps app-main reachable only through loopback
and its private address. Allow inbound ports 80 and 443 publicly. Allow port
8080 only from the app-ai security group or `172.31.33.42`; do not expose it to
the internet. On app-ai, allow port 8081 only from the app-main security group
or `172.31.38.97`. Restrict SSH port 22 to an administrator CIDR after initial
setup.

Before the first deployment, prepare the RDS schema/extensions, S3 and Bedrock
permissions, app-ai private-port security-group rule, and TLS. Deploy app-ai
first, then deploy app-main after the frontend static export and Spring static
serving changes are ready.
