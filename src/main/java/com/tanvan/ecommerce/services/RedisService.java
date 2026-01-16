package com.tanvan.ecommerce.services;

import com.tanvan.ecommerce.entity.Airline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis caching service for airline flight data
 */
@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TIMESTAMP_SUFFIX = ":TIMESTAMP";

    /**
     * Save flights with TTL (Time To Live)
     */
    public void saveFlightsWithTTL(String key, Map<String, List<Airline>> data, int ttlSeconds) {
        try {
            // Save data
            redisTemplate.opsForValue().set(key, data, ttlSeconds, TimeUnit.SECONDS);

            // Save timestamp separately for age calculation
            long timestamp = System.currentTimeMillis();
            redisTemplate.opsForValue().set(key + TIMESTAMP_SUFFIX, timestamp, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get flights with timestamp information
     * Returns CachedData wrapper containing data and age
     */
    public AirlineService.CachedData getFlightsWithTimestamp(String key) {
        try {
            Object data = redisTemplate.opsForValue().get(key);
            Object timestampObj = redisTemplate.opsForValue().get(key + TIMESTAMP_SUFFIX);

            if (data == null || timestampObj == null) {
                return null;
            }

            Map<String, List<Airline>> flights = (Map<String, List<Airline>>) data;
            long timestamp = (Long) timestampObj;

            return new AirlineService.CachedData(flights, timestamp);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get flights without timestamp (backward compatible)
     */
    public Object getFlights(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Save flights without TTL (uses Redis default)
     */
    public void saveFlights(String key, Map<String, List<Airline>> data) {
        try {
            redisTemplate.opsForValue().set(key, data);
            long timestamp = System.currentTimeMillis();
            redisTemplate.opsForValue().set(key + TIMESTAMP_SUFFIX, timestamp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete flights from cache
     */
    public void deleteFlights(String key) {
        try {
            redisTemplate.delete(key);
            redisTemplate.delete(key + TIMESTAMP_SUFFIX);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check if key exists
     */
    public boolean hasFlights(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Clear all flight caches
     */
    public void clearAllFlights() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Acquire a distributed lock using Redis SETNX with expiration.
     * Returns a unique token if lock is acquired, null otherwise.
     * @param key The lock key
     * @param expireSeconds Expiration time in seconds
     * @return Token if acquired, null if not
     */
    public String acquireLock(String key, int expireSeconds) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, token, expireSeconds, TimeUnit.SECONDS);
            return (success != null && success) ? token : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Release the lock only if the current value matches the provided token.
     * This prevents releasing locks held by others.
     * @param key The lock key
     * @param token The token from acquireLock
     */
    public void releaseLock(String key, String token) {
        try {
            Object currentValue = redisTemplate.opsForValue().get(key);
            if (token.equals(currentValue)) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}