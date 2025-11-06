package com.fraud.engine.redis;

import com.fraud.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisState {

    private final StringRedisTemplate redis;

    // Store this tx in a per-user ZSET scored by epoch seconds
    public void recordTransactionTime(String userId, long epochSec) {
        String key = "user:%s:tx_times".formatted(userId);
        redis.opsForZSet().add(key, String.valueOf(epochSec), epochSec);
        // Keep only recent time range (e.g., last 24h)
        long cutoff = epochSec - 24 * 3600;
        redis.opsForZSet().removeRangeByScore(key, 0, cutoff);
        redis.expire(key, 2, TimeUnit.DAYS);
    }

    // Count how many tx in last N seconds
    public long recentCount(String userId, long nowSec, long windowSec) {
        String key = "user:%s:tx_times".formatted(userId);
        return redis.opsForZSet().count(key, nowSec - windowSec, nowSec);
    }

    public boolean firstSeenDevice(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank())
            return false;
        String key = "user:%s:devices".formatted(userId);
        Long added = redis.opsForSet().add(key, deviceId); // returns Long
        redis.expire(key, 90, TimeUnit.DAYS);
        return added != null && added > 0; // true if newly added
    }

    public boolean firstSeenIp(String userId, String ip) {
        if (ip == null || ip.isBlank())
            return false;
        String key = "user:%s:ips".formatted(userId);
        Long added = redis.opsForSet().add(key, ip); // returns Long
        redis.expire(key, 90, TimeUnit.DAYS);
        return added != null && added > 0; // true if newly added
    }

    // Store and fetch last location
    public static record LastLoc(double lat, double lon, long epochSec) {
    }

    public LastLoc getLastLoc(String userId) {
        String key = "user:%s:last_loc".formatted(userId);
        var lat = redis.opsForHash().get(key, "lat");
        var lon = redis.opsForHash().get(key, "lon");
        var ts = redis.opsForHash().get(key, "ts");
        if (lat == null || lon == null || ts == null)
            return null;
        try {
            return new LastLoc(Double.parseDouble(lat.toString()),
                    Double.parseDouble(lon.toString()),
                    Long.parseLong(ts.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    public void setLastLoc(String userId, double lat, double lon, long epochSec) {
        String key = "user:%s:last_loc".formatted(userId);
        redis.opsForHash().put(key, "lat", String.valueOf(lat));
        redis.opsForHash().put(key, "lon", String.valueOf(lon));
        redis.opsForHash().put(key, "ts", String.valueOf(epochSec));
        redis.expire(key, 30, TimeUnit.DAYS);
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ─── Level 4: Spend Spike Detection ───────────────────────────────────

    /**
     * Store transaction amount in a Redis LIST (last N amounts per user)
     * @param userId user identifier
     * @param amount transaction amount
     * @param maxSize maximum number of amounts to keep (e.g., 10)
     */
    public void recordAmount(String userId, double amount, int maxSize) {
        String key = "user:%s:amounts".formatted(userId);
        redis.opsForList().leftPush(key, String.valueOf(amount));
        redis.opsForList().trim(key, 0, maxSize - 1); // Keep only last N
        redis.expire(key, 90, TimeUnit.DAYS);
    }

    /**
     * Get median of last N transaction amounts for spend spike detection
     * @param userId user identifier
     * @return median amount, or 0 if no history
     */
    public double getMedianAmount(String userId) {
        String key = "user:%s:amounts".formatted(userId);
        var amounts = redis.opsForList().range(key, 0, -1);
        if (amounts == null || amounts.isEmpty()) {
            return 0.0;
        }

        var nums = amounts.stream()
                .map(s -> {
                    try { return Double.parseDouble(s); }
                    catch (Exception e) { return 0.0; }
                })
                .sorted()
                .toList();

        int size = nums.size();
        if (size % 2 == 0) {
            return (nums.get(size/2 - 1) + nums.get(size/2)) / 2.0;
        } else {
            return nums.get(size/2);
        }
    }

    // ─── Level 4: Time-Based Device/IP Freshness ──────────────────────────

    /**
     * Record device with timestamp for freshness tracking
     * @param userId user identifier
     * @param deviceId device identifier
     * @param epochSec current timestamp
     * @return true if device was first-seen (newly added)
     */
    public boolean recordDevice(String userId, String deviceId, long epochSec) {
        if (deviceId == null || deviceId.isBlank())
            return false;
        String key = "user:%s:device_times".formatted(userId);
        // Store device with timestamp as score
        Double existingScore = redis.opsForZSet().score(key, deviceId);
        redis.opsForZSet().add(key, deviceId, epochSec);
        redis.expire(key, 90, TimeUnit.DAYS);
        return existingScore == null; // true if newly added
    }

    /**
     * Check if device was first seen within X days
     * @param userId user identifier
     * @param deviceId device identifier
     * @param nowSec current timestamp
     * @param withinDays number of days to check
     * @return true if device first seen within X days
     */
    public boolean deviceSeenWithinDays(String userId, String deviceId, long nowSec, int withinDays) {
        if (deviceId == null || deviceId.isBlank())
            return false;
        String key = "user:%s:device_times".formatted(userId);
        Double firstSeenSec = redis.opsForZSet().score(key, deviceId);
        if (firstSeenSec == null)
            return false;
        long daysSinceFirstSeen = (nowSec - firstSeenSec.longValue()) / 86400;
        return daysSinceFirstSeen <= withinDays;
    }

    /**
     * Record IP with timestamp for freshness tracking
     * @param userId user identifier
     * @param ip IP address
     * @param epochSec current timestamp
     * @return true if IP was first-seen (newly added)
     */
    public boolean recordIp(String userId, String ip, long epochSec) {
        if (ip == null || ip.isBlank())
            return false;
        String key = "user:%s:ip_times".formatted(userId);
        Double existingScore = redis.opsForZSet().score(key, ip);
        redis.opsForZSet().add(key, ip, epochSec);
        redis.expire(key, 90, TimeUnit.DAYS);
        return existingScore == null; // true if newly added
    }

    /**
     * Check if IP was first seen within X days
     * @param userId user identifier
     * @param ip IP address
     * @param nowSec current timestamp
     * @param withinDays number of days to check
     * @return true if IP first seen within X days
     */
    public boolean ipSeenWithinDays(String userId, String ip, long nowSec, int withinDays) {
        if (ip == null || ip.isBlank())
            return false;
        String key = "user:%s:ip_times".formatted(userId);
        Double firstSeenSec = redis.opsForZSet().score(key, ip);
        if (firstSeenSec == null)
            return false;
        long daysSinceFirstSeen = (nowSec - firstSeenSec.longValue()) / 86400;
        return daysSinceFirstSeen <= withinDays;
    }
}
