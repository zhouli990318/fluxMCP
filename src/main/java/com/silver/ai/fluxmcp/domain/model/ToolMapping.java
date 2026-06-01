package com.silver.ai.fluxmcp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具映射实体 — 一个 OpenAPI operation 映射为一个 MCP Tool
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMapping {

    private Long id;
    private Long apiSourceId;
    /** OpenAPI operationId */
    private String operationId;
    /** MCP Tool 名称 */
    private String toolName;
    /** MCP Tool 描述 */
    private String toolDescription;
    private String httpMethod;
    private String path;
    /** JSON Schema 字符串 - 参数定义 */
    private String parameterSchema;
    /** JSON Schema 字符串 - 响应定义 */
    private String responseSchema;
    /** JSON 字符串 - 调用示例 */
    private String examplePayload;
    @Builder.Default
    private boolean enabled = true;
    private LocalDateTime createdAt;

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateDescription(String toolName, String toolDescription) {
        this.toolName = toolName;
        this.toolDescription = toolDescription;
    }

    public void updateConfig(String toolName, String toolDescription, String httpMethod,
                             String path, String parameterSchema, String responseSchema,
                             String examplePayload) {
        if (toolName != null && !toolName.isBlank()) {
            this.toolName = toolName;
        }
        if (toolDescription != null) {
            this.toolDescription = toolDescription;
        }
        if (httpMethod != null && !httpMethod.isBlank()) {
            this.httpMethod = httpMethod;
        }
        if (path != null && !path.isBlank()) {
            this.path = path;
        }
        if (parameterSchema != null) {
            this.parameterSchema = parameterSchema;
        }
        if (responseSchema != null) {
            this.responseSchema = responseSchema;
        }
        if (examplePayload != null) {
            this.examplePayload = examplePayload;
        }
    }
}
