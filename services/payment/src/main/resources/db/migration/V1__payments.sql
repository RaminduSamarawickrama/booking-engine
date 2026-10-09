-- One row per payment attempt. A booking can have several attempts (declined card, retry)
-- but only one successful payment.
create table payment (
    id                 uuid primary key,
    booking_id         uuid        not null,
    booking_reference  text        not null,
    attempt            integer     not null,
    amount_minor       integer     not null check (amount_minor > 0),
    currency           text        not null,
    customer_email     text,
    provider           text        not null,            -- mock | stripe
    provider_reference text,                            -- mock id / Stripe Checkout Session id
    provider_payment_id text,                           -- Stripe PaymentIntent id, once paid
    status             text        not null,            -- PENDING, SUCCEEDED, FAILED, REFUNDED
    failure_code       text,
    failure_message    text,
    created_at         timestamptz not null,
    updated_at         timestamptz not null,
    unique (booking_id, attempt)
);

create index payment_booking on payment (booking_id, attempt desc);
create index payment_provider_reference on payment (provider_reference);
-- A booking is paid at most once; a second successful payment is refunded.
create unique index payment_one_success_per_booking on payment (booking_id) where status = 'SUCCEEDED';
