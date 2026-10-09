insert into rate_card values
  ('STANDARD',  300, 140, 20, 2500, 3, 3),
  ('ESTATE',    400, 155, 22, 2900, 4, 5),
  ('EXECUTIVE', 600, 210, 30, 4500, 3, 3),
  ('MPV',       600, 190, 26, 3900, 6, 6),
  ('MINIBUS',   900, 240, 32, 5500, 8, 8);

insert into airport_charge values
  ('LHR', 700, 600),
  ('LGW', 600, 700),
  ('STN', 500, 700),
  ('LTN', 500, 600),
  ('LCY', 400, 0);

insert into extra_price values
  ('CHILD_SEAT', 800),
  ('BOOSTER_SEAT', 500),
  ('MEET_GREET', 1200),
  ('PET', 1000);

insert into surcharge_rule values ('night', 1500, 22, 6);
