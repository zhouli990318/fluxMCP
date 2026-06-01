package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.silver.ai.fluxmcp.domain.model.McpAgentSession;

import java.util.Optional;

public interface McpSessionStore {

    Optional<McpAgentSession> find(Long sourceId, String sessionId);

    void save(McpAgentSession session);

    void removeSession(Long sourceId, String sessionId);

    void evictSource(Long sourceId);
}