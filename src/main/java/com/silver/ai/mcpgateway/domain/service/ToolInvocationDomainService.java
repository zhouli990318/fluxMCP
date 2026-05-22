package com.silver.ai.mcpgateway.domain.service;

import com.silver.ai.mcpgateway.domain.model.ApiSource;
import com.silver.ai.mcpgateway.domain.model.ToolMapping;
import com.silver.ai.mcpgateway.domain.port.HttpClientPort;
import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.common.result.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Map;

/**
 * 工具调用领域服务 — 根据 ToolMapping 路由并执行实际 API 调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolInvocationDomainService {

    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 调用工具
     * @param apiSource API 源（包含认证信息）
     * @param toolMapping 工具映射
     * @param arguments 调用参数（JSON 字符串）
     * @return 调用结果
     */
    public String invoke(ApiSource apiSource, ToolMapping toolMapping, String arguments) {
        apiSource.ensureActive();

        try {
            InvocationPayload payload = parsePayload(toolMapping, arguments);
            String url = buildUrl(apiSource.getBaseUrl(), toolMapping.getPath(), payload.pathVariables());
            Map<String, String> headers = buildHeaders(payload.headerOverrides(), payload.transportHeaders());
            Map<String, String> queryParams = extractQueryParams(toolMapping, payload);
            String body = buildBody(toolMapping, payload);

            String result = httpClient.execute(
                    toolMapping.getHttpMethod(),
                    url,
                    headers,
                    queryParams,
                    body
            );

            log.info("Tool invocation success: {} -> {}", toolMapping.getToolName(), toolMapping.getPath());
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Tool invocation failed: {}", toolMapping.getToolName(), e);
            throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED, e.getMessage(), e);
        }
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> pathVariables) {
        String fullPath = path;
        if (pathVariables != null) {
            for (Entry<String, String> entry : pathVariables.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                            "路径变量 '" + entry.getKey() + "' 不能为空");
                }
                String value = entry.getValue();
                if (value.contains("/") || value.contains("\\") || value.contains("..")) {
                    throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                            "路径变量 '" + entry.getKey() + "' 包含非法字符");
                }
                fullPath = fullPath.replace("{" + entry.getKey() + "}", value);
            }
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String normalizedPath = fullPath == null ? "" : fullPath.trim().replaceFirst("^/+", "");

        try {
            URI baseUri = URI.create(ensureTrailingSlash(normalizedBaseUrl));
            URI resolvedUrl = baseUri.resolve(normalizedPath).normalize();
            String resolvedStr = resolvedUrl.toString();

            // 防止路径穿越：最终 URL 必须以 baseUrl 为前缀
            if (!resolvedStr.startsWith(normalizedBaseUrl)) {
                throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                        "工具路径存在路径穿越风险: " + fullPath);
            }
            return resolvedStr;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                    "工具路径格式不合法: " + fullPath);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return UrlNormalizer.normalizeBaseUrl(baseUrl, ErrorCode.MCP_TOOL_INVOCATION_FAILED);
    }

    private String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private Map<String, String> buildHeaders(Map<String, String> headerOverrides,
                                             Map<String, String> transportHeaders) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (transportHeaders != null) {
            headers.putAll(transportHeaders);
        }
        if (headerOverrides != null) {
            headers.putAll(headerOverrides);
        }
        return headers;
    }

    private Map<String, String> extractQueryParams(ToolMapping mapping, InvocationPayload payload) {
        Map<String, String> params = new HashMap<>();
        if (payload.explicitQuery() != null && !payload.explicitQuery().isEmpty()) {
            params.putAll(payload.explicitQuery());
            return params;
        }
        if ("GET".equalsIgnoreCase(mapping.getHttpMethod()) && payload.genericArguments() != null) {
            payload.genericArguments().forEach((key, value) -> {
                if (!mapping.getPath().contains("{" + key + "}")) {
                    params.put(key, value);
                }
            });
        }
        return params;
    }

    private String buildBody(ToolMapping mapping, InvocationPayload payload) {
        if ("GET".equalsIgnoreCase(mapping.getHttpMethod()) || "DELETE".equalsIgnoreCase(mapping.getHttpMethod())) {
            return null;
        }
        if (payload.explicitBody() != null) {
            return payload.explicitBody();
        }
        return writeJson(payload.genericArguments());
    }

    private InvocationPayload parsePayload(ToolMapping mapping, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new InvocationPayload(Map.of(), Map.of(), Map.of(), Map.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(arguments);
            Map<String, String> pathVariables = toStringMap(root.get("_pathVariables"));
            Map<String, String> explicitQuery = toStringMap(root.get("_query"));
            Map<String, String> headerOverrides = toStringMap(root.get("_headers"));
            Map<String, String> transportHeaders = extractTransportHeaders(root.get("_mcp"));
            String explicitBody = root.has("_body") ? writeJson(root.get("_body")) : null;

            Map<String, String> genericArguments = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                if (isReservedKey(entry.getKey())) {
                    continue;
                }
                genericArguments.put(entry.getKey(), entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue().toString());
                if (mapping.getPath().contains("{" + entry.getKey() + "}")) {
                    pathVariables.putIfAbsent(entry.getKey(), entry.getValue().asText());
                }
            }

            return new InvocationPayload(pathVariables, explicitQuery, headerOverrides, transportHeaders, explicitBody, genericArguments);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Failed to parse MCP invocation payload, falling back to raw arguments", ex);
            return new InvocationPayload(Map.of(), Map.of(), Map.of(), Map.of(), arguments);
        }
    }

    private Map<String, String> extractTransportHeaders(JsonNode mcpNode) {
        if (mcpNode == null || mcpNode.isMissingNode()) {
            return Map.of();
        }
        return toStringMap(mcpNode.get("transportHeaders"));
    }

    private Map<String, String> toStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.properties().forEach(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        return map;
    }

    private boolean isReservedKey(String key) {
        return key.startsWith("_");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED, "工具请求体序列化失败", ex);
        }
    }

    private record InvocationPayload(Map<String, String> pathVariables,
                                     Map<String, String> explicitQuery,
                                     Map<String, String> headerOverrides,
                                     Map<String, String> transportHeaders,
                                     String explicitBody,
                                     Map<String, String> genericArguments) {

        private InvocationPayload(Map<String, String> pathVariables,
                                  Map<String, String> explicitQuery,
                                  Map<String, String> headerOverrides,
                                  Map<String, String> transportHeaders,
                                  String explicitBody) {
            this(pathVariables, explicitQuery, headerOverrides, transportHeaders, explicitBody, Map.of());
        }
    }
}
