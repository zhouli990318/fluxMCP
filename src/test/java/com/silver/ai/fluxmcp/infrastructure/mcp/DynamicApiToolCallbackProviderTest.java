package com.silver.ai.fluxmcp.infrastructure.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.ApiSourceRepository;
import com.silver.ai.fluxmcp.domain.port.ToolMappingRepository;
import com.silver.ai.fluxmcp.domain.service.ToolInvocationDomainService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicApiToolCallbackProviderTest {

    @Test
    void getToolCallbacksShouldExposeEnabledToolsFromActiveSources() {
    ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
    ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
    DynamicApiToolCallbackProvider provider = new DynamicApiToolCallbackProvider(
        sourceRepository,
        toolRepository,
        mock(ToolInvocationDomainService.class),
        new ObjectMapper()
    );

        ApiSource activeSource = ApiSource.builder().id(1L).name("User API").active(true).build();
        ApiSource inactiveSource = ApiSource.builder().id(2L).name("Order API").active(false).build();
        ToolMapping activeTool = ToolMapping.builder()
                .id(10L)
                .apiSourceId(1L)
                .toolName("get-user")
                .toolDescription("load user")
                .parameterSchema("{\"type\":\"object\"}")
                .enabled(true)
                .build();
        ToolMapping disabledTool = ToolMapping.builder()
                .id(11L)
                .apiSourceId(1L)
                .toolName("delete-user")
                .enabled(false)
                .build();

        when(sourceRepository.findByActive(true)).thenReturn(Flux.just(activeSource, inactiveSource));
        when(toolRepository.findByApiSourceId(1L)).thenReturn(Flux.just(activeTool, disabledTool));
        when(toolRepository.findByApiSourceId(2L)).thenReturn(Flux.empty());

        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertEquals(1, callbacks.length);
        assertEquals("get-user", callbacks[0].getToolDefinition().name());
        assertEquals("[User API] load user", callbacks[0].getToolDefinition().description());
    }

    @Test
    void getToolCallbacksShouldEnsureUniqueToolNames() {
        ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
        ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
        DynamicApiToolCallbackProvider provider = new DynamicApiToolCallbackProvider(
            sourceRepository,
            toolRepository,
            mock(ToolInvocationDomainService.class),
            new ObjectMapper()
        );

        ApiSource sourceA = ApiSource.builder().id(1L).name("User API").active(true).build();
        ApiSource sourceB = ApiSource.builder().id(2L).name("Order API").active(true).build();
        ToolMapping toolA = ToolMapping.builder().id(10L).apiSourceId(1L).toolName("search").enabled(true).build();
        ToolMapping toolB = ToolMapping.builder().id(20L).apiSourceId(2L).toolName("search").enabled(true).build();

        when(sourceRepository.findByActive(true)).thenReturn(Flux.just(sourceA, sourceB));
        when(toolRepository.findByApiSourceId(1L)).thenReturn(Flux.just(toolA));
        when(toolRepository.findByApiSourceId(2L)).thenReturn(Flux.just(toolB));

        ToolCallback[] callbacks = provider.getToolCallbacks();

        assertEquals(2, callbacks.length);
        assertArrayEquals(new String[]{"search", "Order_API__search"},
                new String[]{callbacks[0].getToolDefinition().name(), callbacks[1].getToolDefinition().name()});
    }
}