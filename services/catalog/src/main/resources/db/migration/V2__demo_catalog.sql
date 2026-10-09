-- Demo catalog: London airports and a typical fleet. Admin screens edit these later.

insert into vehicle_category (code, name, description, example_models, max_passengers, max_luggage, sort_order) values
  ('STANDARD',  'Standard',  'Comfortable saloon for up to three.',              'Toyota Prius, Skoda Octavia',      3, 3, 10),
  ('ESTATE',    'Estate',    'Extra boot space for bigger cases.',               'Skoda Superb Estate, VW Passat',   4, 5, 20),
  ('EXECUTIVE', 'Executive', 'Business class saloon with a senior driver.',      'Mercedes E-Class, BMW 5 Series',   3, 3, 30),
  ('MPV',       'People carrier', 'Six seats and room for everyone''s luggage.', 'Ford Galaxy, VW Sharan',           6, 6, 40),
  ('MINIBUS',   'Minibus',   'Groups of up to eight with luggage.',              'Mercedes Vito, Ford Transit Custom', 8, 8, 50);

insert into extra (code, name, description, max_quantity, sort_order) values
  ('CHILD_SEAT',   'Child seat',        'Rear-facing or forward-facing seat for children up to 4 years.', 3, 10),
  ('BOOSTER_SEAT', 'Booster seat',      'For children aged 4 to 12.',                                     3, 20),
  ('MEET_GREET',   'Meet and greet',    'Your driver waits in arrivals holding a name board.',            1, 30),
  ('PET',          'Small pet',         'A small pet in a carrier.',                                      1, 40);

insert into airport (iata, name, city, latitude, longitude) values
  ('LHR', 'Heathrow',      'London', 51.4700, -0.4543),
  ('LGW', 'Gatwick',       'London', 51.1537, -0.1821),
  ('STN', 'Stansted',      'London', 51.8860,  0.2389),
  ('LTN', 'Luton',         'London', 51.8747, -0.3683),
  ('LCY', 'London City',   'London', 51.5048,  0.0495);

insert into terminal (airport_iata, code, name, latitude, longitude) values
  ('LHR', 'T2', 'Terminal 2', 51.4697, -0.4513),
  ('LHR', 'T3', 'Terminal 3', 51.4711, -0.4568),
  ('LHR', 'T4', 'Terminal 4', 51.4588, -0.4466),
  ('LHR', 'T5', 'Terminal 5', 51.4723, -0.4877),
  ('LGW', 'N',  'North Terminal', 51.1618, -0.1774),
  ('LGW', 'S',  'South Terminal', 51.1564, -0.1609),
  ('STN', 'MAIN', 'Terminal', 51.8899, 0.2627),
  ('LTN', 'MAIN', 'Terminal', 51.8790, -0.3760),
  ('LCY', 'MAIN', 'Terminal', 51.5033, 0.0553);
