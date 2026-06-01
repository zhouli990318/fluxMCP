package com.silver.ai.fluxmcp.interfaces.dto;

import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 源健康状态 DTO（不暴露 openApiSpec 等大字段）。
 */
@Data
@Builder
@AllArgsConstructor
public class SourceHealthDto {

    private Long id;
    private String name;
    private String baseUrl;
    private boolean active;
    private HealthStatus healthStatus;
    private LocalDateTime lastHealthCheckAt;
    private LocalDateTime lastHealthyAt;
    private int consecutiveFailures;
    private String lastErrorMessage;

    public static SourceHealthDto from(ApiSource source) {
        return SourceHealthDto.builder()
                .id(source.getId())
                .name(source.getName())
                .baseUrl(source.getBaseUrl())
                .active(source.isActive())
                .healthStatus(source.getHealthStatus() == null ? HealthStatus.UNKNOWN : source.getHealthStatus())
                .lastHealthCheckAt(source.getLastHealthCheckAt())
                .lastHealthyAt(source.getLastHealthyAt())
                .consecutiveFailures(source.getConsecutiveFailures())
                .lastErrorMessage(source.getLastErrorMessage())
                .build();
    }
}
