-- Releases every hold a booking still owns. The compensating transaction of the saga.
--
-- KEYS[1]   booking_holds:{bookingId}
-- ARGV[1]   bookingId
--
-- returns   the number of holds actually released
--
-- The ownership check is not an optimisation. A booking's hold can lapse, the seat be
-- reclaimed by someone else, and only then its cancellation arrive — deleting blindly would
-- take a seat that is no longer ours. Skipping those makes a repeat release, or a release
-- of an expired hold, a no-op that returns 0.

local holds = redis.call('SMEMBERS', KEYS[1])
local bookingId = ARGV[1]
local count = 0

for i = 1, #holds do
    if redis.call('GET', holds[i]) == bookingId then
        redis.call('DEL', holds[i])
        count = count + 1
    end
end

redis.call('DEL', KEYS[1])

return count
