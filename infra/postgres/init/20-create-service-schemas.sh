#!/usr/bin/env bash
# Creates one schema + one login role per microservice inside a single database.
#
# Why schema-per-service: free hosted Postgres (Supabase, Neon) gives you one
# database per project, so every environment uses the same layout:
#   database "booking"  ->  schema "booking" owned by role "booking_svc", etc.
# A service's role owns only its own schema and cannot read the others.
#
# Runs in two places:
#   1. Automatically on first start of the local Docker Postgres (initdb hook).
#   2. Against a remote demo database:
#        DATABASE_ADMIN_URL='postgresql://postgres:...@host:5432/postgres?sslmode=require' \
#        AUTH_DB_PASSWORD=... CATALOG_DB_PASSWORD=... ./create-service-schemas.sh
#
# Safe to re-run: existing roles/schemas are kept and passwords are re-applied.
set -euo pipefail

SERVICES="${SERVICES:-auth catalog pricing booking payment fleet dispatch tracking notification}"

if [[ -n "${DATABASE_ADMIN_URL:-}" ]]; then
  psql_cmd=(psql "$DATABASE_ADMIN_URL" -v ON_ERROR_STOP=1 -q)
else
  # Inside the postgres image's initdb phase.
  psql_cmd=(psql -v ON_ERROR_STOP=1 -q --username "${POSTGRES_USER:-postgres}" --dbname "${POSTGRES_DB:-booking}")
fi

password_for() {
  local svc="$1" var
  var="$(tr '[:lower:]' '[:upper:]' <<< "$svc")_DB_PASSWORD"
  if [[ -n "${!var:-}" ]]; then
    printf '%s' "${!var}"
  elif [[ -n "${SERVICE_DB_PASSWORD_PREFIX:-}" ]]; then
    printf '%s%s' "$SERVICE_DB_PASSWORD_PREFIX" "$svc"   # local development only
  else
    echo "error: $var is not set" >&2
    return 1
  fi
}

echo "Preparing shared extensions schema"
"${psql_cmd[@]}" <<'SQL'
CREATE SCHEMA IF NOT EXISTS extensions;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
-- Install only what the server offers, so this also works on minimal Postgres builds.
SELECT format('CREATE EXTENSION IF NOT EXISTS %I WITH SCHEMA extensions', name)
FROM pg_available_extensions
WHERE name IN ('postgis', 'pg_trgm', 'btree_gist')
  AND name NOT IN (SELECT extname FROM pg_extension)
\gexec
SQL

for svc in $SERVICES; do
  [[ "$svc" =~ ^[a-z][a-z0-9_]*$ ]] || { echo "bad service name: $svc" >&2; exit 1; }
  role="${svc}_svc"
  pw="$(password_for "$svc")"
  echo "Service $svc: role $role, schema $svc"
  "${psql_cmd[@]}" -v svc="$svc" -v role="$role" -v pw="$pw" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role')
\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'role', :'pw')
\gexec
-- Lets a non-superuser admin (Supabase/Neon "postgres") create the schema on the role's behalf.
SELECT format('GRANT %I TO %I', :'role', current_user)
WHERE NOT pg_has_role(current_user, :'role', 'MEMBER')
\gexec
CREATE SCHEMA IF NOT EXISTS :"svc" AUTHORIZATION :"role";
REVOKE ALL ON SCHEMA :"svc" FROM PUBLIC;
GRANT USAGE ON SCHEMA extensions TO :"role";
SELECT format('ALTER ROLE %I SET search_path = %I, extensions, public', :'role', :'svc')
\gexec
SQL
done

echo "Done: $(wc -w <<< "$SERVICES") service schemas ready."
