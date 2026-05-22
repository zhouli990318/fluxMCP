package com.silver.ai.mcpgateway.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INTERNAL_ERROR(50000, "系统内部错误"),
    INVALID_PARAMETER(40000, "参数校验失败"),
    MCP_SOURCE_NOT_FOUND(44001, "MCP API源不存在"),
    MCP_PARSE_FAILED(44002, "OpenAPI规范解析失败"),
    MCP_TOOL_INVOCATION_FAILED(44003, "MCP工具调用失败"),
    MCP_TOOL_NOT_FOUND(44004, "MCP工具不存在"),
    REQUEST_TIMEOUT(50004, "请求超时"),
    DATA_INTEGRITY_CONFLICT(40901, "数据完整性冲突"),
    PAYLOAD_TOO_LARGE(40013, "文件大小超过限制");

    private final int code;
    private final String message;
}