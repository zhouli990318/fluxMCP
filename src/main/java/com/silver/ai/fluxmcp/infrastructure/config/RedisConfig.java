package com.silver.ai.fluxmcp.infrastructure.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConditionalOnProperty(prefix = "app.mcp.session", name = "store-type", havingValue = "redis")
@ConfigurationProperties(prefix = "redis.sdk.config")
public class RedisConfig {

    private String host = "localhost";
    private int port = 6379;
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(20);
        
        return Redisson.create(config);
    }
}