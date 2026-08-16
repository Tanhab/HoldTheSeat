CREATE TABLE outbox
(
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    aggregate_id UUID        NOT NULL,
    event_type   TEXT        NOT NULL,
    topic        TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

-- Partial: the poller only ever reads unpublished rows, so published history costs the
-- index nothing as the table grows.
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events
(
    consumer_group TEXT        NOT NULL,
    event_id       UUID        NOT NULL,
    processed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (consumer_group, event_id)
);

-- The price is the seat service's to state, and it has not spoken when a booking is first
-- written. A placeholder zero would be a lie that survives into the payment.
ALTER TABLE bookings ALTER COLUMN amount_cents DROP NOT NULL;
