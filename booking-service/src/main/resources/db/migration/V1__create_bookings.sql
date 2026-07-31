CREATE TABLE bookings
(
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    show_id      UUID        NOT NULL,
    seat_ids     UUID[]      NOT NULL,
    customer_id  TEXT        NOT NULL,
    amount_cents BIGINT      NOT NULL,
    status       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT bookings_amount_non_negative CHECK (amount_cents >= 0),
    CONSTRAINT bookings_seats_not_empty CHECK (cardinality(seat_ids) > 0),
    CONSTRAINT bookings_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_bookings_customer_id ON bookings (customer_id);
CREATE INDEX idx_bookings_status_created_at ON bookings (status, created_at);
