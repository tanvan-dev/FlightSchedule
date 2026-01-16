package com.tanvan.ecommerce.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.username:default}")
    private String redisUsername;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * Connection Factory for Production (Redis Cloud with SSL)
     * Only active when spring.data.redis.ssl.enabled=true
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.data.redis.ssl.enabled", havingValue = "true")
    public RedisConnectionFactory redisCloudConnectionFactory() {
        log.info("🔴 Configuring Redis Cloud connection (SSL enabled)...");
        log.info("Host: {}, Port: {}, Username: {}", redisHost, redisPort, redisUsername);

        // Redis standalone configuration
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setUsername(redisUsername);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        // Client options with SSL
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(
                        SocketOptions.builder()
                                .connectTimeout(Duration.ofSeconds(10))
                                .keepAlive(true)
                                .build()
                )
                .sslOptions(SslOptions.builder().build())
                .build();

        // Lettuce client configuration with SSL
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(5))
                .clientOptions(clientOptions)
                .useSsl()
                .disablePeerVerification()  // For Redis Cloud
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();

        log.info("✅ Redis Cloud connection factory created successfully");
        return factory;
    }

    /**
     * Connection Factory for Local Development (No SSL)
     * Only active when spring.data.redis.ssl.enabled=false or not set
     */
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.ssl.enabled", havingValue = "false", matchIfMissing = true)
    public RedisConnectionFactory redisLocalConnectionFactory() {
        log.info("🔴 Configuring Local Redis connection (SSL disabled)...");
        log.info("Host: {}, Port: {}", redisHost, redisPort);

        // Simple configuration for local Redis
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.setPassword(redisPassword);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        log.info("✅ Local Redis connection factory created successfully");
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("🔴 Configuring RedisTemplate...");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key is String
        template.setKeySerializer(new StringRedisSerializer());

        // Value is JSON
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        valueSerializer.setObjectMapper(mapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();

        log.info("✅ RedisTemplate configured successfully");
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("🔴 Configuring RedisCacheManager...");

        // Key serializer
        RedisSerializationContext.SerializationPair<String> keySerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());

        // Value serializer (JSON)
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        valueSerializer.setObjectMapper(mapper);

        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer);

        // Cache config with TTL
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(keySerializer)
                .serializeValuesWith(valuePair)
                .entryTtl(Duration.ofMinutes(10)); // TTL for cache

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();

        log.info("✅ RedisCacheManager configured successfully");
        return cacheManager;
    }
}