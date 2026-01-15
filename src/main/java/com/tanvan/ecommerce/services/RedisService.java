package com.tanvan.ecommerce.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long CACHE_TTL = 60 * 5;   // 5 phút

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveFlights(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(CACHE_TTL));
    }

    public Object getFlights(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}