create table app_user (
    id             uuid primary key,
    email          text        not null,
    password_hash  text        not null,
    full_name      text        not null,
    phone          text,
    roles          text[]      not null,
    enabled        boolean     not null default true,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    constraint app_user_email_lowercase check (email = lower(email))
);

create unique index app_user_email on app_user (email);

-- Refresh tokens are opaque random strings; only their SHA-256 is stored. Every rotation
-- stays in the same family, so reuse of an old token can revoke the whole chain.
create table refresh_token (
    id           uuid primary key,
    user_id      uuid        not null references app_user (id) on delete cascade,
    family_id    uuid        not null,
    token_hash   text        not null,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    replaced_by  uuid
);

create unique index refresh_token_hash on refresh_token (token_hash);
create index refresh_token_family on refresh_token (family_id) where revoked_at is null;
create index refresh_token_expiry on refresh_token (expires_at);
