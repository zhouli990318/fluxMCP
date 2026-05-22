package com.silver.ai.mcpgateway.infrastructure.persistence.entity;

import com.silver.ai.mcpgateway.domain.model.AuthType;
import com.silver.ai.mcpgateway.domain.model.HealthStatus;
import com.silver.ai.mcpgateway.domain.model.ProtocolType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("mcp_gateway.api_source")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiSourceEntity {

    @Id
    private Long id;

    private String name;

    private String description;

    @Column("protocol_type")
    @Builder.Default
    private ProtocolType protocolType = ProtocolType.HTTP;

    @Column("base_url")
    private String baseUrl;

    @Column("openapi_spec")
    private String openApiSpec;

    @Column("auth_type")
    @Builder.Default
    private AuthType authType = AuthType.NONE;

    @Column("auth_config")
    private String authConfig;

    @Builder.Default
    private boolean active = true;

    @Column("health_status")
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column("last_health_check_at")
    private LocalDateTime lastHealthCheckAt;

    @Column("last_healthy_at")
    private LocalDateTime lastHealthyAt;

    @Column("consecutive_failures")
    @Builder.Default
    private int consecutiveFailures = 0;

    @Column("last_error_message")
    private String lastErrorMessage;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
