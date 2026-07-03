package com.silver.ai.fluxmcp.interfaces.dto;

import lombok.Data;

@Data
public class ToolMappingCreateRequest {
    private String toolName;
    private String toolDescription;
    private String httpMethod;
    private String path;
    private String parameterSchema;
    private String responseSchema;
    private String examplePayload;
    private Boolean enabled;
}