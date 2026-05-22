package com.silver.ai.mcpgateway.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class McpAgentSession {

    private Long sourceId;
    private String sessionId;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime initializedAt;
    @Builder.Default
    private Map<String, String> transportHeaders = new LinkedHashMap<>();
    private String clientName;
    private String clientVersion;
    private String clientCapabilitiesJson;
    private String lastToolName;
    private String lastToolArgumentsJson;
    private LocalDateTime lastToolCallAt;
    @Builder.Default
    private long toolCallCount = 0L;

    public void touch(LocalDateTime now, Map<String, String> headers) {
        if (createdAt == null) {
            createdAt = now;
        }
        lastSeenAt = now;
        if (headers != null && !headers.isEmpty()) {
            transportHeaders = new LinkedHashMap<>(headers);
        }
    }

    public void initialize(String clientName, String clientVersion, String capabilitiesJson, LocalDateTime now) {
        if (initializedAt == null) {
            initializedAt = now;
        }
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.clientCapabilitiesJson = capabilitiesJson;
        this.lastSeenAt = now;
    }

    public synchronized void recordToolCall(String toolName, String argumentsJson, LocalDateTime now) {
        lastToolName = toolName;
        lastToolArgumentsJson = argumentsJson;
        lastToolCallAt = now;
        lastSeenAt = now;
        toolCallCount++;
    }
}