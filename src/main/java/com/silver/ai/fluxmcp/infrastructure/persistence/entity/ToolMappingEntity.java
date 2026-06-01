package com.silver.ai.fluxmcp.infrastructure.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("flux_mcp.tool_mapping")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ToolMappingEntity {

    @Id
    private Long id;

    @Column("api_source_id")
    private Long apiSourceId;

    @Column("operation_id")
    private String operationId;

    @Column("tool_name")
    private String toolName;

    @Column("tool_description")
    private String toolDescription;

    @Column("http_method")
    private String httpMethod;

    private String path;

    @Column("parameter_schema")
    private String parameterSchema;

    @Column("response_schema")
    private String responseSchema;

    @Column("example_payload")
    private String examplePayload;

    @Builder.Default
    private boolean enabled = true;

    @Column("created_at")
    private LocalDateTime createdAt;
}
