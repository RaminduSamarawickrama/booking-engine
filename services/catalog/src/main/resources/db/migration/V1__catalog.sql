-- What customers can book. Prices live in pricing-service; this is the descriptive side.

create table vehicle_category (
    code            text primary key,
    name            text    not null,
    description     text    not null,
    example_models  text    not null,
    max_passengers  integer not null check (max_passengers > 0),
    max_luggage     integer not null check (max_luggage >= 0),
    sort_order      integer not null,
    active          boolean not null default true
);

create table extra (
    code          text primary key,
    name          text    not null,
    description   text    not null,
    max_quantity  integer not null check (max_quantity > 0),
    sort_order    integer not null,
    active        boolean not null default true
);

create table airport (
    iata       text primary key check (iata ~ '^[A-Z]{3}$'),
    name       text not null,
    city       text not null,
    latitude   double precision not null,
    longitude  double precision not null,
    active     boolean not null default true
);

create table terminal (
    airport_iata  text not null references airport (iata),
    code          text not null,
    name          text not null,
    latitude      double precision not null,
    longitude     double precision not null,
    primary key (airport_iata, code)
);
