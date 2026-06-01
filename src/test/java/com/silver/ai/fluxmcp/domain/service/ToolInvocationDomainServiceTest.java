package com.silver.ai.fluxmcp.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.AuthType;
import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.HttpClientPort;
import com.silver.ai.fluxmcp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ToolInvocationDomainServiceTest {

    @Test
    void invokeShouldBuildUrlHeadersAndQueryParamsForGetRequest() {
        HttpClientPort httpClient = mock(HttpClientPort.class);
        ToolInvocationDomainService service = new ToolInvocationDomainService(httpClient, new ObjectMapper());
        ApiSource source = ApiSource.builder()
                .name("demo")
                .baseUrl("https://api.example.com/")
                .authType(AuthType.API_KEY)
                .authConfig("{\"headerName\":\"X-Token\",\"apiKey\":\"abc\"}")
                .build();
        ToolMapping mapping = ToolMapping.builder()
                .toolName("getUser")
                .httpMethod("GET")
                .path("/users/{id}")
                .build();
        when(httpClient.execute(any(), any(), any(), any(), any())).thenReturn("ok");

        String result = service.invoke(source, mapping, "{\"id\":123,\"q\":\"name\"}");

        assertEquals("ok", result);
        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).execute(methodCaptor.capture(), urlCaptor.capture(), headerCaptor.capture(), queryCaptor.capture(), bodyCaptor.capture());
        assertEquals("GET", methodCaptor.getValue());
        assertEquals("https://api.example.com/users/123", urlCaptor.getValue());
        assertEquals("abc", headerCaptor.getValue().get("X-Token"));
        assertEquals("name", queryCaptor.getValue().get("q"));
        assertNull(bodyCaptor.getValue());
    }

    @Test
    void invokeShouldRejectInactiveSource() {
        ToolInvocationDomainService service = new ToolInvocationDomainService(mock(HttpClientPort.class), new ObjectMapper());
        ApiSource source = ApiSource.builder().name("disabled").active(false).build();
        ToolMapping mapping = ToolMapping.builder().toolName("tool").httpMethod("POST").path("/path").build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.invoke(source, mapping, "{}"));

        assertEquals("MCP API源不存在", exception.getErrorCode().getMessage());
    }

    @Test
    void invokeShouldUseExplicitMappingsAndPassthroughTransportHeaders() {
        HttpClientPort httpClient = mock(HttpClientPort.class);
        ToolInvocationDomainService service = new ToolInvocationDomainService(httpClient, new ObjectMapper());
        ApiSource source = ApiSource.builder()
                .name("demo")
                .baseUrl("https://api.example.com")
                .authType(AuthType.BEARER_TOKEN)
                .authConfig("{\"token\":\"server-token\"}")
                .build();
        ToolMapping mapping = ToolMapping.builder()
                .toolName("updateUser")
                .httpMethod("POST")
                .path("/users/{id}")
                .build();
        when(httpClient.execute(any(), any(), any(), any(), any())).thenReturn("ok");

        String arguments = """
            {
              "_pathVariables": {"id": "42"},
              "_query": {"verbose": "true"},
              "_headers": {"x-request-id": "req-1"},
              "_body": {"name": "Alice"},
              "_mcp": {"transportHeaders": {"traceparent": "00-abc"}}
            }
            """;

        String result = service.invoke(source, mapping, arguments);

        assertEquals("ok", result);
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).execute(eq("POST"), eq("https://api.example.com/users/42"),
            headerCaptor.capture(), queryCaptor.capture(), bodyCaptor.capture());
        assertEquals("server-token", headerCaptor.getValue().get("Authorization").replace("Bearer ", ""));
        assertEquals("req-1", headerCaptor.getValue().get("x-request-id"));
        assertEquals("00-abc", headerCaptor.getValue().get("traceparent"));
        assertEquals("true", queryCaptor.getValue().get("verbose"));
        assertEquals("{\"name\":\"Alice\"}", bodyCaptor.getValue());
        }

        @Test
        void invokeShouldNormalizeBaseUrlWithoutScheme() {
        HttpClientPort httpClient = mock(HttpClientPort.class);
        ToolInvocationDomainService service = new ToolInvocationDomainService(httpClient, new ObjectMapper());
        ApiSource source = ApiSource.builder()
            .name("demo")
            .baseUrl("192.168.9.148:8080/api")
            .authType(AuthType.NONE)
            .build();
        ToolMapping mapping = ToolMapping.builder()
            .toolName("alarmListUsingGET")
            .httpMethod("GET")
            .path("/alarm/list")
            .build();

        // 192.168.x is a site-local address — SSRF protection should block it
        BusinessException exception = assertThrows(BusinessException.class, () -> service.invoke(source, mapping, "{}"));
        assertEquals("MCP工具调用失败", exception.getErrorCode().getMessage());
        verify(httpClient, never()).execute(any(), any(), any(), any(), any());
        }

        @Test
        void invokeShouldRejectInvalidBaseUrlBeforeCallingHttpClient() {
        HttpClientPort httpClient = mock(HttpClientPort.class);
        ToolInvocationDomainService service = new ToolInvocationDomainService(httpClient, new ObjectMapper());
        ApiSource source = ApiSource.builder()
            .name("demo")
            .baseUrl("http://bad host")
            .authType(AuthType.NONE)
            .build();
        ToolMapping mapping = ToolMapping.builder()
            .toolName("alarmListUsingGET")
            .httpMethod("GET")
            .path("/alarm/list")
            .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> service.invoke(source, mapping, "{}"));

        assertEquals("MCP工具调用失败", exception.getErrorCode().getMessage());
        verify(httpClient, never()).execute(any(), any(), any(), any(), any());
        }
}