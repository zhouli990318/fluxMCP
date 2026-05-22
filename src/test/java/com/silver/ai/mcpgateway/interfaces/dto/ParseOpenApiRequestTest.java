package com.silver.ai.mcpgateway.interfaces.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseOpenApiRequestTest {

    @Test
    void shouldResolveExplicitSpecPayload() {
        ParseOpenApiRequest request = new ParseOpenApiRequest();
        request.setSourceType(ParseSourceType.SPEC);
        request.setContent("openapi: 3.0.1");

        assertFalse(request.isUrlSource());
        assertEquals("openapi: 3.0.1", request.requireContent());
    }

    @Test
    void shouldResolveLegacyUrlPayload() {
        ParseOpenApiRequest request = new ParseOpenApiRequest();
        request.setOpenApiUrl("https://example.com/openapi.json");

        assertTrue(request.isUrlSource());
        assertEquals("https://example.com/openapi.json", request.requireContent());
    }

    @Test
    void shouldRejectEmptyPayload() {
        ParseOpenApiRequest request = new ParseOpenApiRequest();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::requireContent);
        assertEquals("解析内容不能为空", exception.getMessage());
    }
}