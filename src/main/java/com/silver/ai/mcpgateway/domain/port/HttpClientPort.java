package com.silver.ai.mcpgateway.domain.port;

import java.util.Map;

/**
 * HTTP 请求执行端口
 */
public interface HttpClientPort {

    /**
     * 执行 HTTP 请求
     */
    String execute(String method, String url, Map<String, String> headers,
                   Map<String, String> queryParams, String body);
}
