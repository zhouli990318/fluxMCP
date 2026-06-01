package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.McpAgentSession;
import com.silver.ai.fluxmcp.infrastructure.config.McpSessionProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSessionServiceTest {

    @Test
    void provisionSessionShouldPersistFilteredHeaders() {
        McpSessionStore sessionStore = mock(McpSessionStore.class);
        when(sessionStore.find(7L, "s-1")).thenReturn(Optional.empty());

        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(30));
        properties.setAllowedPassthroughHeaders(List.of("X-Request-Id", "Traceparent"));
        properties.setBlockedPassthroughHeaders(List.of("Authorization"));

        McpSessionService service = new McpSessionService(sessionStore, new ObjectMapper(), properties);
        ServerRequest request = ServerRequest.create(
                MockServerWebExchange.from(
                        MockServerHttpRequest.post("/api/v1/mcp/sources/7/mcp/message?sessionId=s-1")
                                .header("X-Request-Id", "req-1")
                                .header("Traceparent", "00-abc")
                                .header("Authorization", "ignored")
                                .build()
                ),
                List.<HttpMessageReader<?>>of()
        );

        McpAgentSession session = service.provisionSession(7L, "s-1", request);

        assertNotNull(session);
        assertEquals("s-1", session.getSessionId());
        assertEquals("req-1", session.getTransportHeaders().get("x-request-id"));
        assertEquals("00-abc", session.getTransportHeaders().get("traceparent"));
                verify(sessionStore).save(any(McpAgentSession.class));
    }

    @Test
    void recordToolCallShouldMergeExchangeMetadataIntoSession() {
                McpSessionStore sessionStore = mock(McpSessionStore.class);
                when(sessionStore.find(9L, "session-9")).thenReturn(Optional.empty());

        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setAllowedPassthroughHeaders(List.of("X-Request-Id"));

                McpSessionService service = new McpSessionService(sessionStore, new ObjectMapper(), properties);
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("tester", "1.0.0"));
        when(exchange.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpTransportMetadataKeys.REQUEST_HEADERS, Map.of("x-request-id", "req-9")
        )));

        McpAgentSession session = service.recordToolCall(9L, "session-9", "tool-a", Map.of("id", 1), exchange);

        assertEquals("tester", session.getClientName());
        assertEquals("tool-a", session.getLastToolName());
        assertEquals(1L, session.getToolCallCount());
        assertEquals("req-9", session.getTransportHeaders().get("x-request-id"));
                verify(sessionStore).save(any(McpAgentSession.class));
    }

    @Test
    void recordToolCallShouldNotPersistBlockedHeadersFromTransportContext() {
                McpSessionStore sessionStore = mock(McpSessionStore.class);
                when(sessionStore.find(10L, "session-10")).thenReturn(Optional.empty());

        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setAllowedPassthroughHeaders(List.of("X-Request-Id", "Authorization"));
        properties.setBlockedPassthroughHeaders(List.of("Authorization"));

                McpSessionService service = new McpSessionService(sessionStore, new ObjectMapper(), properties);
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("tester", "1.0.0"));
        when(exchange.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpTransportMetadataKeys.REQUEST_HEADERS, Map.of(
                        "x-request-id", "req-10",
                        "authorization", "secret-token"
                )
        )));

        McpAgentSession session = service.recordToolCall(10L, "session-10", "tool-a", Map.of("id", 1), exchange);

        assertEquals("req-10", session.getTransportHeaders().get("x-request-id"));
        assertFalse(session.getTransportHeaders().containsKey("authorization"));
    }

    @Test
    void recordToolCallShouldReturnTransientSessionWhenSessionIdMissing() {
                McpSessionStore sessionStore = mock(McpSessionStore.class);
        McpSessionProperties properties = new McpSessionProperties();
        properties.setTtl(Duration.ofMinutes(10));

                McpSessionService service = new McpSessionService(sessionStore, new ObjectMapper(), properties);
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new McpSchema.Implementation("tester", "1.0.0"));
        when(exchange.getClientCapabilities()).thenReturn(mock(McpSchema.ClientCapabilities.class));
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpTransportMetadataKeys.REQUEST_HEADERS, Map.of("x-request-id", "req-11")
        )));

        McpAgentSession session = assertDoesNotThrow(() -> service.recordToolCall(11L, null, "tool-a", Map.of("id", 1), exchange));

        assertNotNull(session);
        assertNull(session.getSessionId());
        assertEquals("tool-a", session.getLastToolName());
        assertEquals(1L, session.getToolCallCount());
                verify(sessionStore, never()).find(eq(11L), any());
                verify(sessionStore, never()).save(any(McpAgentSession.class));
    }
}