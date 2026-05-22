package com.silver.ai.mcpgateway.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.mcp.session")
public class McpSessionProperties {

    private String storeType = "memory";
    private Duration ttl = Duration.ofMinutes(30);
    private Duration cleanupInterval = Duration.ofMinutes(5);
    private List<String> allowedPassthroughHeaders = new ArrayList<>();
    private List<String> blockedPassthroughHeaders = new ArrayList<>();
}