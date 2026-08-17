-- Re-checks that a booking still owns every seat it is about to be sold.
--
-- KEYS[1]     booking_holds:{bookingId}          declared for slot routing; not read
-- ARGV[1]     bookingId
-- ARGV[2..]   hold keys, one per seat
--
-- returns     1   every hold key exists and still names this booking
--             0   at least one is missing or owned by someone else
--
-- Writes nothing. Payment can authorise after the hold TTL has lapsed, and by then the seat
-- may be free or already someone else's — confirming that sale would sell a seat twice.


local check = 1

for i = 2, #ARGV do
    local holdKey = ARGV[i]
    local owner = redis.call('GET', holdKey)
    if owner ~= ARGV[1] then
        check = 0
    end
end

return check