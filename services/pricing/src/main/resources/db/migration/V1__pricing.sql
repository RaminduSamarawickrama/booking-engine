-- Price rules. Amounts are in minor units (pence) of booking.pricing.currency.

create table rate_card (
    category_code        text primary key,
    base_fare_minor      integer not null,
    per_km_minor         integer not null,
    per_minute_minor     integer not null,
    minimum_fare_minor   integer not null,
    -- capacity copied from the catalog so quotes only offer vehicles that fit
    max_passengers       integer not null,
    max_luggage          integer not null
);

create table airport_charge (
    iata              text primary key,
    pickup_fee_minor  integer not null,   -- parking while the driver waits in arrivals
    dropoff_fee_minor integer not null    -- the airport's forecourt drop-off charge
);

create table extra_price (
    code         text primary key,
    price_minor  integer not null
);

create table surcharge_rule (
    id            text primary key,
    percent_bps   integer not null,        -- 1500 = 15 %
    from_hour     integer not null check (from_hour between 0 and 23),
    to_hour       integer not null check (to_hour between 0 and 23)
);

-- Quotes are kept so a booking pays exactly the price the customer saw.
create table quote (
    id           uuid primary key,
    created_at   timestamptz not null,
    expires_at   timestamptz not null,
    body         jsonb       not null
);

create index quote_expires_at on quote (expires_at);
