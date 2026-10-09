create table booking (
    id                  uuid primary key,
    reference           text        not null,
    status              text        not null,
    user_id             uuid,
    customer_name       text        not null,
    customer_email      text        not null,
    customer_phone      text        not null,
    pickup              jsonb       not null,
    dropoff             jsonb       not null,
    pickup_at           timestamptz not null,
    passengers          integer     not null,
    luggage             integer     not null,
    flight_number       text,
    driver_notes        text,
    category_code       text        not null,
    currency            text        not null,
    vehicle_price_minor integer     not null,
    extras_total_minor  integer     not null,
    total_minor         integer     not null,
    distance_meters     integer     not null,
    duration_seconds    integer     not null,
    quote_id            uuid        not null,
    manage_token_hash   text        not null,
    created_at          timestamptz not null,
    updated_at          timestamptz not null,
    constraint booking_email_lowercase check (customer_email = lower(customer_email))
);

create unique index booking_reference on booking (reference);
create index booking_user on booking (user_id, pickup_at desc) where user_id is not null;
create index booking_unclaimed_email on booking (customer_email) where user_id is null;
create index booking_pending on booking (created_at) where status = 'PENDING_PAYMENT';

create table booking_extra (
    booking_id        uuid    not null references booking (id) on delete cascade,
    code              text    not null,
    quantity          integer not null check (quantity > 0),
    unit_price_minor  integer not null,
    primary key (booking_id, code)
);

-- Every status change, for support and for the customer's timeline.
create table booking_status_change (
    booking_id  uuid        not null references booking (id) on delete cascade,
    from_status text,
    to_status   text        not null,
    reason      text        not null,
    changed_at  timestamptz not null
);

create index booking_status_change_booking on booking_status_change (booking_id, changed_at);
