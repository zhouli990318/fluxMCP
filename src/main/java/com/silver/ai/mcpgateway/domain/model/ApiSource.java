package com.silver.ai.mcpgateway.domain.model;

import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.common.result.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * API 数据源 — 聚合根
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSource {

    private Long id;
    private String name;
    private String description;
    @Builder.Default
    private ProtocolType protocolType = ProtocolType.HTTP;
    private String baseUrl;
    /** 原始 OpenAPI 规范文本 */
    private String openApiSpec;
    @Builder.Default
    private AuthType authType = AuthType.NONE;
    /** JSON 格式的认证配置 */
    private String authConfig;
    @Builder.Default
    private boolean active = true;
    @Builder.Default
    private List<ToolMapping> toolMappings = new ArrayList<>();
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;
    private LocalDateTime lastHealthCheckAt;
    private LocalDateTime lastHealthyAt;
    @Builder.Default
    private int consecutiveFailures = 0;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void markHealthy() {
        LocalDateTime now = LocalDateTime.now();
        this.healthStatus = HealthStatus.HEALTHY;
        this.lastHealthCheckAt = now;
        this.lastHealthyAt = now;
        this.consecutiveFailures = 0;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    public void markDegraded(String reason) {
        LocalDateTime now = LocalDateTime.now();
        this.healthStatus = HealthStatus.DEGRADED;
        this.lastHealthCheckAt = now;
        this.lastHealthyAt = now;
        this.consecutiveFailures = 0;
        this.lastErrorMessage = reason;
        this.updatedAt = now;
    }

    public void markUnreachable(String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        this.healthStatus = HealthStatus.UNREACHABLE;
        this.lastHealthCheckAt = now;
        this.consecutiveFailures = this.consecutiveFailures + 1;
        this.lastErrorMessage = errorMessage;
        this.updatedAt = now;
    }

    public void updateSpec(String openApiSpec) {
        this.openApiSpec = openApiSpec;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateInfo(String name, String description, String baseUrl, AuthType authType, String authConfig) {
        this.name = name;
        this.description = description;
        this.baseUrl = baseUrl;
        this.authType = authType;
        this.authConfig = authConfig;
        this.updatedAt = LocalDateTime.now();
    }

    public void setToolMappings(List<ToolMapping> mappings) {
        this.toolMappings = mappings;
        this.updatedAt = LocalDateTime.now();
    }

    public void ensureActive() {
        if (!active) {
            throw new BusinessException(ErrorCode.MCP_SOURCE_NOT_FOUND, "API源已停用: " + name);
        }
    }
}
