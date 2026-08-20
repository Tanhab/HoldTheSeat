-- Why a booking was cancelled, so the two failure paths stay distinguishable at the API
-- rather than only in the event stream. Nullable: a live booking has no reason yet.
ALTER TABLE bookings ADD COLUMN cancellation_reason TEXT;
