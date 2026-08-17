-- Turns a validated hold into a permanent sale in Redis.
--
-- KEYS[1]     booking_holds:{bookingId}          the reverse index
-- ARGV[1]     sold:{showId}                      members are seat ids
-- ARGV[2..]   (holdKey, seatId) pairs            two entries per seat
--
-- returns     the number of hold keys deleted
--
-- Unconditional, because Postgres has already committed these seats as SOLD to this booking
-- by the time this runs. There is nothing left to arbitrate; Redis is being brought into
-- agreement with the durable record.
--
-- The sold set carries no TTL. It is the claim script's fast "already paid for" check, and
-- it is rebuilt from Postgres on startup because losing it would let a paid seat be sold
-- again.


local count = 0
for i = 2, #ARGV, 2 do
    local holdKey, seatId = ARGV[i], ARGV[i + 1]
    redis.call("SADD", ARGV[1], seatId)
    count = count + redis.call("DEL", holdKey)
end
redis.call("DEL", KEYS[1])
return count

