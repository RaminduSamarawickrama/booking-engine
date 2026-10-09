-- Retried "create booking" requests (double clicks, flaky mobile networks) return the first
-- result instead of booking twice. Rows are kept for 24 hours.
create table idempotent_request (
    key           text        primary key,
    request_hash  text        not null,
    response      jsonb       not null,
    created_at    timestamptz not null
);

create index idempotent_request_created on idempotent_request (created_at);
