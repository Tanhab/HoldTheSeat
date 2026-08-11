CREATE TABLE shows
(
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    name       TEXT        NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- seat_row, not row: row is reserved in SQL and would need quoting at every use site.
CREATE TABLE seats
(
    id          UUID   PRIMARY KEY DEFAULT uuidv7(),
    show_id     UUID   NOT NULL REFERENCES shows (id) ON DELETE CASCADE,
    seat_row    TEXT   NOT NULL,
    seat_number INT    NOT NULL,
    price_cents BIGINT NOT NULL,
    status      TEXT   NOT NULL DEFAULT 'AVAILABLE',

    CONSTRAINT seats_price_non_negative CHECK (price_cents >= 0),
    CONSTRAINT seats_number_positive CHECK (seat_number > 0),
    -- HELD is absent on purpose: a hold lives only in Redis, keyed by its TTL.
    CONSTRAINT seats_status_valid CHECK (status IN ('AVAILABLE', 'SOLD')),
    CONSTRAINT seats_unique_position UNIQUE (show_id, seat_row, seat_number)
);

CREATE INDEX idx_seats_show_id_status ON seats (show_id, status);
