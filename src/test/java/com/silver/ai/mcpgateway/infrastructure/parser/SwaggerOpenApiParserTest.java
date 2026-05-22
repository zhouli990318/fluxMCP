package com.silver.ai.mcpgateway.infrastructure.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.domain.model.ToolMapping;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwaggerOpenApiParserTest {

    private final SwaggerOpenApiParser parser = new SwaggerOpenApiParser(new ObjectMapper());

    @Test
    void parseShouldExtractToolMappingsFromOpenApiSpec() {
        String spec = """
                openapi: 3.0.1
                info:
                  title: Demo API
                  version: 1.0.0
                paths:
                  /users/{id}:
                    get:
                      operationId: getUser
                      summary: Get user
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: string
                        - name: expand
                          in: query
                          schema:
                            type: string
                  /orders:
                    post:
                      summary: Create order
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [name]
                              properties:
                                name:
                                  type: string
                                  description: order name
                """;

        List<ToolMapping> result = parser.parse(spec, 3L);

        assertEquals(2, result.size());
        assertEquals("getUser", result.get(0).getOperationId());
        assertEquals("GET", result.get(0).getHttpMethod());
        assertTrue(result.get(0).getParameterSchema().contains("expand"));
        assertEquals("post_orders", result.get(1).getToolName());
        assertTrue(result.get(1).getParameterSchema().contains("order name"));
    }

    @Test
    void parseShouldThrowWhenSpecIsInvalid() {
        assertThrows(BusinessException.class, () -> parser.parse("not-a-valid-openapi", 1L));
    }

    @Test
    void parseShouldExtractToolMappingsFromSwaggerTwoSpec() {
        String spec = """
                swagger: '2.0'
                info:
                  title: Legacy Demo API
                  version: 1.0.0
                paths:
                  /pets:
                    get:
                      operationId: listPets
                      summary: List pets
                      parameters:
                        - name: limit
                          in: query
                          type: integer
                    post:
                      operationId: createPet
                      consumes:
                        - application/x-www-form-urlencoded
                      parameters:
                        - name: name
                          in: formData
                          required: true
                          type: string
                          description: pet name
                        - name: age
                          in: formData
                          type: integer
                  /pets/search:
                    post:
                      operationId: searchPets
                      parameters:
                        - in: body
                          name: body
                          required: true
                          schema:
                            type: object
                            required:
                              - tag
                            properties:
                              tag:
                                type: string
                                description: pet tag
                              page:
                                type: integer
                """;

        List<ToolMapping> result = parser.parse(spec, 5L);

        assertEquals(3, result.size());
        assertEquals("listPets", result.get(0).getOperationId());
        assertTrue(result.get(1).getParameterSchema().contains("pet name"));
        assertTrue(result.get(1).getParameterSchema().contains("name"));
        assertTrue(result.get(2).getParameterSchema().contains("pet tag"));
        assertTrue(result.get(2).getParameterSchema().contains("tag"));
    }

    @Test
    void parseShouldReportSwaggerVersionWhenSwaggerTwoSpecIsInvalid() {
        BusinessException exception = assertThrows(BusinessException.class,
          () -> parser.parse("swagger: '2.0'\npaths: [", 1L));

        assertTrue(exception.getMessage().contains("Swagger 2.0"));
    }
}