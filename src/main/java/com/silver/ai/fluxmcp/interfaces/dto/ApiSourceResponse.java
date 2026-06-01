package com.silver.ai.fluxmcp.interfaces.dto;

import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.AuthType;
import com.silver.ai.fluxmcp.domain.model.HealthStatus;
import com.silver.ai.fluxmcp.domain.model.ProtocolType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API 源响应 DTO — 过滤掉 openApiSpec 等大字段和 authConfig 敏感信息
 */
@Getter
@Builder
public class ApiSourceResponse {

    private Long id;
    private String name;
    private String description;
    private ProtocolType protocolType;
    private String baseUrl;
    private AuthType authType;
    private boolean active;
    private HealthStatus healthStatus;
    private LocalDateTime lastHealthCheckAt;
    private LocalDateTime lastHealthyAt;
    private int consecutiveFailures;
    private String lastErrorMessage;
    private int toolCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApiSourceResponse from(ApiSource source) {
        return ApiSourceResponse.builder()
                .id(source.getId())
                .name(source.getName())
                .description(source.getDescription())
                .protocolType(source.getProtocolType())
                .baseUrl(source.getBaseUrl())
                .authType(source.getAuthType())
                .active(source.isActive())
                .healthStatus(source.getHealthStatus())
                .lastHealthCheckAt(source.getLastHealthCheckAt())
                .lastHealthyAt(source.getLastHealthyAt())
                .consecutiveFailures(source.getConsecutiveFailures())
                .lastErrorMessage(source.getLastErrorMessage())
                .toolCount(source.getToolMappings() != null ? source.getToolMappings().size() : 0)
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    public static List<ApiSourceResponse> fromList(List<ApiSource> sources) {
        return sources.stream().map(ApiSourceResponse::from).toList();
    }
}
