package com.silver.ai.fluxmcp.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * MCP 源健康检查配置。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.mcp.health")
public class McpHealthProperties {

    /** 是否启用定时心跳 */
    private boolean enabled = true;

    /** 心跳间隔 */
    private Duration checkInterval = Duration.ofSeconds(60);

    /** 单次探测超时 */
    private Duration probeTimeout = Duration.ofSeconds(5);

    /** 慢响应阈值（低于超时），超过此时长视为 DEGRADED */
    private Duration slowThreshold = Duration.ofSeconds(3);

    /** 连续失败达到该阈值时自动停用源 */
    private int maxConsecutiveFailures = 5;

    /** 连续失败超过阈值时是否自动 deactivate */
    private boolean autoDeactivateOnMaxFailures = true;

    /** 并发探测数上限 */
    private int probeConcurrency = 5;
}
