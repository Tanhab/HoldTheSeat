-- Atomic all-or-nothing multi-seat hold.
--
-- KEYS[1]     booking_holds:{bookingId}          the reverse index
-- ARGV[1]     bookingId
-- ARGV[2]     ttl in milliseconds
-- ARGV[3]     sold:{showId}                      members are seat ids
-- ARGV[4..]   (holdKey, seatId) pairs            two entries per requested seat
--
-- returns     {1}                                granted
--             {0, <conflicting seatId>, ...}     refused, naming every clash
--
-- Two passes on purpose: the first only inspects, so a clash on the last seat cannot leave
-- the earlier ones held. Redis runs the whole script as one command, so nothing interleaves
-- between the passes and no lock is needed.
--
-- A seat the caller already holds is not a clash — Kafka delivery is at-least-once, so the
-- same request legitimately arrives twice.

local conflicting = {}
for i = 4, #ARGV, 2 do
    local holdKey, seatId = ARGV[i], ARGV[i + 1]
    local owner = redis.call('GET', holdKey)
    if (owner ~= false and owner ~= ARGV[1]) or redis.call('SISMEMBER', ARGV[3], seatId) == 1 then
        table.insert(conflicting, seatId)
    end
end

if #conflicting ~= 0 then
    return {0, unpack(conflicting)}
end

for i = 4, #ARGV, 2 do
    local holdKey = ARGV[i]
    redis.call('SET', holdKey, ARGV[1], 'PX', ARGV[2])
    redis.call('SADD', KEYS[1], holdKey)
end
redis.call('PEXPIRE', KEYS[1], ARGV[2])

return {1}
