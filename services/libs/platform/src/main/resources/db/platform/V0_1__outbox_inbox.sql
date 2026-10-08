-- Transactional outbox and inbox, created in every service's own schema by the platform library.
-- Versions below 1 keep these ahead of each service's own migrations (V1, V2, ...).

create table outbox_event (
    id              uuid primary key,
    aggregate_type  text        not null,
    aggregate_id    text        not null,
    event_type      text        not null,
    event_version   integer     not null,
    payload         jsonb       not null,
    request_id      text,
    occurred_at     timestamptz not null,
    published_at    timestamptz
);

-- The relay only ever scans unpublished rows.
create index outbox_event_unpublished on outbox_event (occurred_at, id) where published_at is null;
create index outbox_event_published_at on outbox_event (published_at) where published_at is not null;

create table inbox_message (
    message_id   uuid        not null,
    consumer     text        not null,
    processed_at timestamptz not null default now(),
    primary key (message_id, consumer)
);

create index inbox_message_processed_at on inbox_message (processed_at);
