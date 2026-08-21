-- Diary of saga events for one booking. Written as booking produces or consumes them —
-- not reconstructed by scanning Kafka.

CREATE TABLE booking_timeline
(
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    booking_id  UUID        NOT NULL REFERENCES bookings (id),
    event_id    UUID        NOT NULL,
    event_type  TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload     JSONB       NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (booking_id, event_id)
);

CREATE INDEX idx_booking_timeline_booking_occurred
    ON booking_timeline (booking_id, occurred_at);
