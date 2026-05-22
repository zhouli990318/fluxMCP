package com.silver.ai.mcpgateway.domain.model;

import com.silver.ai.mcpgateway.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSourceToolMappingTest {

    @Test
    void apiSourceShouldUpdateStateAndGuardInactiveAccess() {
        ApiSource source = ApiSource.builder().name("source").active(true).build();

        source.deactivate();
        assertFalse(source.isActive());
        assertThrows(BusinessException.class, source::ensureActive);

        source.activate();
        source.updateSpec("spec");
        source.updateInfo("updated", "desc", "https://api.example.com");
        source.setToolMappings(List.of(ToolMapping.builder().toolName("tool").build()));

        assertTrue(source.isActive());
        assertEquals("updated", source.getName());
        assertEquals("spec", source.getOpenApiSpec());
        assertEquals(1, source.getToolMappings().size());
    }

    @Test
    void toolMappingShouldToggleAndUpdateDescription() {
        ToolMapping mapping = ToolMapping.builder().toolName("old").enabled(true).build();

        mapping.disable();
        mapping.updateDescription("new", "desc");
        mapping.enable();

        assertTrue(mapping.isEnabled());
        assertEquals("new", mapping.getToolName());
        assertEquals("desc", mapping.getToolDescription());
    }
}