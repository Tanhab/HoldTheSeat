-- Runs only on first boot of an empty pgdata volume.
-- Changing this file requires `docker compose down -v` to take effect.

CREATE DATABASE booking;
CREATE DATABASE seat;
CREATE DATABASE payment;
