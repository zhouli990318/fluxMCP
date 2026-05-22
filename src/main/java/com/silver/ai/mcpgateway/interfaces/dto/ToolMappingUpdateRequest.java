package com.silver.ai.mcpgateway.interfaces.dto;

import lombok.Data;

@Data
public class ToolMappingUpdateRequest {
    private String toolName;
    private String toolDescription;
    private String httpMethod;
    private String path;
    private String parameterSchema;
    private String responseSchema;
    private String examplePayload;
    private Boolean enabled;
}
