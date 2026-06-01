package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.McpAgentSession;
import com.silver.ai.fluxmcp.infrastructure.config.McpSessionProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class McpSessionService {

    private final McpSessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final McpSessionProperties sessionProperties;

    public McpSessionService(McpSessionStore sessionStore, ObjectMapper objectMapper,
                             McpSessionProperties sessionProperties) {
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.sessionProperties = sessionProperties;
    }

    public McpAgentSession provisionSession(Long sourceId, String sessionId, ServerRequest request) {
        if (sourceId == null || sessionId == null || sessionId.isBlank()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, String> headers = filterHeaders(request.headers().asHttpHeaders().toSingleValueMap());
        McpAgentSession session = sessionStore.find(sourceId, sessionId)
                .orElseGet(() -> McpAgentSession.builder()
                        .sourceId(sourceId)
                        .sessionId(sessionId)
                        .createdAt(now)
                        .build());
        session.touch(now, headers);
        sessionStore.save(session);
        return session;
    }

    public McpAgentSession recordToolCall(Long sourceId, String sessionId, String toolName,
                                          Map<String, Object> arguments, McpSyncServerExchange exchange) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean persistentSession = sourceId != null && sessionId != null && !sessionId.isBlank();
        McpAgentSession session = persistentSession
                ? sessionStore.find(sourceId, sessionId)
                .orElseGet(() -> McpAgentSession.builder()
                        .sourceId(sourceId)
                        .sessionId(sessionId)
                        .createdAt(now)
                        .build())
                : McpAgentSession.builder()
                .sourceId(sourceId)
                .sessionId(sessionId)
                .createdAt(now)
                .build();

        Map<String, String> headers = filterHeaders(extractHeaders(exchange.transportContext()));
        session.touch(now, headers);
        var clientInfo = exchange.getClientInfo();
        session.initialize(
                clientInfo != null ? clientInfo.name() : null,
                clientInfo != null ? clientInfo.version() : null,
                writeValue(exchange.getClientCapabilities()),
                now
        );
        session.recordToolCall(toolName, writeValue(arguments == null ? Map.of() : arguments), now);
        if (persistentSession) {
            sessionStore.save(session);
        }
        return session;
    }

    public Map<String, Object> buildInvocationMetadata(McpAgentSession session) {
        if (session == null) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sessionId", session.getSessionId());
        metadata.put("sourceId", session.getSourceId());
        metadata.put("clientName", session.getClientName());
        metadata.put("clientVersion", session.getClientVersion());
        metadata.put("toolCallCount", session.getToolCallCount());
        metadata.put("lastToolName", session.getLastToolName());
        metadata.put("lastToolCallAt", session.getLastToolCallAt() == null ? null : session.getLastToolCallAt().toString());
        metadata.put("transportHeaders", session.getTransportHeaders());
        return metadata;
    }

    public void evictSource(Long sourceId) {
        sessionStore.evictSource(sourceId);
    }

    public void removeSession(Long sourceId, String sessionId) {
        sessionStore.removeSession(sourceId, sessionId);
    }

    private Map<String, String> extractHeaders(McpTransportContext transportContext) {
        if (transportContext == null) {
            return Map.of();
        }
        Object rawHeaders = transportContext.get(McpTransportMetadataKeys.REQUEST_HEADERS);
        if (!(rawHeaders instanceof Map<?, ?> headerMap)) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headerMap.forEach((key, value) -> {
            if (key != null && value != null) {
                headers.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return headers;
    }

    private Map<String, String> filterHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> allowList = sessionProperties.getAllowedPassthroughHeaders();
        List<String> blockList = sessionProperties.getBlockedPassthroughHeaders();
        Map<String, String> filtered = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            boolean allowed = allowList.stream().anyMatch(item -> item.equalsIgnoreCase(name));
            boolean blocked = blockList.stream().anyMatch(item -> item.equalsIgnoreCase(name));
            if (allowed && !blocked && value != null && !value.isBlank()) {
                filtered.put(lowerName, value);
            }
        });
        return filtered;
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP session payload", ex);
        }
    }
}