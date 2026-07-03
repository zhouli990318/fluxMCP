package com.silver.ai.fluxmcp.application;

import com.silver.ai.fluxmcp.domain.model.ApiSource;
import com.silver.ai.fluxmcp.domain.model.AuthType;
import com.silver.ai.fluxmcp.domain.model.ToolMapping;
import com.silver.ai.fluxmcp.domain.port.ApiSourceRepository;
import com.silver.ai.fluxmcp.domain.port.OpenApiParserPort;
import com.silver.ai.fluxmcp.domain.port.ToolMappingRepository;
import com.silver.ai.fluxmcp.domain.service.ToolInvocationDomainService;
import com.silver.ai.fluxmcp.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FluxMcpAppServiceTest {

    @Test
    void createApiSourceShouldParseAndSaveToolsWhenSpecProvided() {
        ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
        ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
        OpenApiParserPort parser = mock(OpenApiParserPort.class);
        ToolInvocationDomainService invocationService = mock(ToolInvocationDomainService.class);
        FluxMcpAppService service = new FluxMcpAppService(sourceRepository, toolRepository, parser, invocationService, new ObjectMapper());
        ApiSource savedSource = ApiSource.builder().id(10L).name("demo").build();
        ToolMapping mapping = ToolMapping.builder().toolName("tool-1").build();
        when(sourceRepository.save(any(ApiSource.class))).thenReturn(Mono.just(savedSource));
        when(parser.parse("spec", 10L)).thenReturn(List.of(mapping));
        when(toolRepository.save(mapping)).thenReturn(Mono.just(mapping));

        StepVerifier.create(service.createApiSource("demo", "desc", "https://api.example.com", AuthType.NONE, null, "spec"))
                .assertNext(result -> assertEquals(10L, result.getId()))
                .verifyComplete();

        verify(parser).parse("spec", 10L);
        verify(toolRepository).save(mapping);
    }

    @Test
    void parseOpenApiSpecShouldUpdateSpecDeleteOldMappingsAndSaveNewOnes() {
        ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
        ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
        OpenApiParserPort parser = mock(OpenApiParserPort.class);
        FluxMcpAppService service = new FluxMcpAppService(sourceRepository, toolRepository, parser, mock(ToolInvocationDomainService.class), new ObjectMapper());
        ApiSource source = ApiSource.builder().id(3L).name("demo").build();
        ToolMapping mapping = ToolMapping.builder().toolName("new-tool").build();
        when(sourceRepository.findById(3L)).thenReturn(Mono.just(source));
        when(sourceRepository.save(any(ApiSource.class))).thenReturn(Mono.just(source));
        when(parser.parse("new-spec", 3L)).thenReturn(List.of(mapping));
        when(toolRepository.deleteByApiSourceId(3L)).thenReturn(Mono.empty());
        when(toolRepository.save(mapping)).thenReturn(Mono.just(mapping));

        StepVerifier.create(service.parseOpenApiSpec(3L, "new-spec"))
                .assertNext(result -> {
                    assertEquals(1, result.size());
                    assertEquals("new-spec", source.getOpenApiSpec());
                })
                .verifyComplete();

        verify(toolRepository).deleteByApiSourceId(3L);
        verify(toolRepository).save(mapping);
    }

        @Test
        void createToolMappingShouldApplyDefaultsAndSaveToSource() {
                ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
                ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
                FluxMcpAppService service = new FluxMcpAppService(sourceRepository, toolRepository,
                                mock(OpenApiParserPort.class), mock(ToolInvocationDomainService.class), new ObjectMapper());
                ApiSource source = ApiSource.builder().id(7L).name("demo").baseUrl("https://api.example.com").build();
                String expectedParameterSchema = """
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """;
                String expectedParameterSchemaCompact = expectedParameterSchema.replaceAll("\\s", "");
                when(sourceRepository.findById(7L)).thenReturn(Mono.just(source));
                when(toolRepository.save(any(ToolMapping.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

                StepVerifier.create(service.createToolMapping(7L,
                                                "listPets",
                                                "列出宠物",
                                                "GET",
                                                "/pets",
                                                null,
                                                null,
                                                null,
                                                null))
                                .assertNext(mapping -> {
                                        assertEquals(7L, mapping.getApiSourceId());
                                        assertEquals("listPets", mapping.getOperationId());
                                        assertEquals("listPets", mapping.getToolName());
                                        assertEquals("列出宠物", mapping.getToolDescription());
                                        assertEquals("GET", mapping.getHttpMethod());
                                        assertEquals("/pets", mapping.getPath());
                                        assertEquals(expectedParameterSchemaCompact, mapping.getParameterSchema().replaceAll("\\s", ""));
                                        assertEquals("{}", mapping.getResponseSchema());
                                        assertEquals("{}", mapping.getExamplePayload());
                                        assertEquals(true, mapping.isEnabled());
                                })
                                .verifyComplete();

                verify(toolRepository).save(argThat(mapping ->
                                mapping.getApiSourceId().equals(7L)
                                                && "listPets".equals(mapping.getToolName())
                                && expectedParameterSchemaCompact.equals(mapping.getParameterSchema().replaceAll("\\s", ""))
                                                && "{}".equals(mapping.getResponseSchema())
                                                && "{}".equals(mapping.getExamplePayload())
                                                && mapping.isEnabled()));
        }

    @Test
    void invokeToolShouldFailWhenToolDoesNotExist() {
        ToolMappingRepository toolRepository = mock(ToolMappingRepository.class);
        FluxMcpAppService service = new FluxMcpAppService(mock(ApiSourceRepository.class), toolRepository,
                mock(OpenApiParserPort.class), mock(ToolInvocationDomainService.class), new ObjectMapper());
        when(toolRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.invokeTool(99L, "{}"))
                .expectErrorMatches(ex -> ex instanceof BusinessException
                        && "MCP工具不存在".equals(((BusinessException) ex).getErrorCode().getMessage()))
                .verify();
    }

    @Test
    void createApiSourceShouldNormalizeBaseUrlWithoutScheme() {
        ApiSourceRepository sourceRepository = mock(ApiSourceRepository.class);
        when(sourceRepository.save(any(ApiSource.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        FluxMcpAppService service = new FluxMcpAppService(sourceRepository, mock(ToolMappingRepository.class),
                mock(OpenApiParserPort.class), mock(ToolInvocationDomainService.class), new ObjectMapper());

        StepVerifier.create(Mono.defer(() ->
                        service.createApiSource("demo", "desc", "192.168.9.148:8080/api", AuthType.NONE, null, null)))
                .assertNext(result -> assertEquals("http://192.168.9.148:8080/api", result.getBaseUrl()))
                .verifyComplete();
    }

    @Test
    void createApiSourceShouldRejectInvalidBaseUrl() {
        FluxMcpAppService service = new FluxMcpAppService(mock(ApiSourceRepository.class), mock(ToolMappingRepository.class),
                mock(OpenApiParserPort.class), mock(ToolInvocationDomainService.class), new ObjectMapper());

        // normalizeBaseUrl throws synchronously before returning Mono
        StepVerifier.create(Mono.defer(() ->
                        service.createApiSource("demo", "desc", "http://bad host", AuthType.NONE, null, null)))
                .expectErrorMatches(ex -> ex instanceof BusinessException
                        && "参数校验失败".equals(((BusinessException) ex).getErrorCode().getMessage()))
                .verify();
    }

}
