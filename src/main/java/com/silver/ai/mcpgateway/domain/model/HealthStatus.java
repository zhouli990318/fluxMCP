package com.silver.ai.mcpgateway.domain.model;

/**
 * MCP API 源的健康状态。
 */
public enum HealthStatus {
    /** 尚未探测 */
    UNKNOWN,
    /** 健康 */
    HEALTHY,
    /** 响应缓慢或间歇性错误 */
    DEGRADED,
    /** 无法到达 */
    UNREACHABLE
}
