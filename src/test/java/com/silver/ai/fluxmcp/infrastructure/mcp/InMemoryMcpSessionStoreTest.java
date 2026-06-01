package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.silver.ai.fluxmcp.domain.model.McpAgentSession;
import com.silver.ai.fluxmcp.infrastructure.config.McpSessionProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryMcpSessionStoreTest {

    @Test
    void shouldSaveFindAndRemoveSession() {
        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(5));
        InMemoryMcpSessionStore store = new InMemoryMcpSessionStore(properties);

        McpAgentSession session = McpAgentSession.builder()
                .sourceId(1L)
                .sessionId("session-1")
                .createdAt(LocalDateTime.now())
                .clientName("tester")
                .build();

        store.save(session);

        McpAgentSession restored = store.find(1L, "session-1").orElseThrow();
        assertEquals("tester", restored.getClientName());

        store.removeSession(1L, "session-1");
        assertFalse(store.find(1L, "session-1").isPresent());
    }

    @Test
    void shouldEvictAllSessionsForSource() {
        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(5));
        InMemoryMcpSessionStore store = new InMemoryMcpSessionStore(properties);

        store.save(McpAgentSession.builder().sourceId(2L).sessionId("s-1").createdAt(LocalDateTime.now()).build());
        store.save(McpAgentSession.builder().sourceId(2L).sessionId("s-2").createdAt(LocalDateTime.now()).build());
        store.save(McpAgentSession.builder().sourceId(3L).sessionId("s-3").createdAt(LocalDateTime.now()).build());

        store.evictSource(2L);

        assertFalse(store.find(2L, "s-1").isPresent());
        assertFalse(store.find(2L, "s-2").isPresent());
        assertTrue(store.find(3L, "s-3").isPresent());
    }
}