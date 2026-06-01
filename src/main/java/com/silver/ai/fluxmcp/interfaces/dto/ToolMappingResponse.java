package com.silver.ai.fluxmcp.interfaces.dto;

import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具映射响应 DTO
 */
@Getter
@Builder
public class ToolMappingResponse {

    private Long id;
    private Long apiSourceId;
    private String operationId;
    private String toolName;
    private String toolDescription;
    private String httpMethod;
    private String path;
    private String parameterSchema;
    private String responseSchema;
    private String examplePayload;
    private boolean enabled;
    private LocalDateTime createdAt;

    public static ToolMappingResponse from(ToolMapping mapping) {
        return ToolMappingResponse.builder()
                .id(mapping.getId())
                .apiSourceId(mapping.getApiSourceId())
                .operationId(mapping.getOperationId())
                .toolName(mapping.getToolName())
                .toolDescription(mapping.getToolDescription())
                .httpMethod(mapping.getHttpMethod())
                .path(mapping.getPath())
                .parameterSchema(mapping.getParameterSchema())
                .responseSchema(mapping.getResponseSchema())
                .examplePayload(mapping.getExamplePayload())
                .enabled(mapping.isEnabled())
                .createdAt(mapping.getCreatedAt())
                .build();
    }

    public static List<ToolMappingResponse> fromList(List<ToolMapping> mappings) {
        return mappings.stream().map(ToolMappingResponse::from).toList();
    }
}
