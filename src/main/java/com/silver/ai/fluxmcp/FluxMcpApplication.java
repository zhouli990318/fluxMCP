package com.silver.ai.fluxmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
    "org.springframework.ai.mcp.server.autoconfigure.McpServerSseWebFluxAutoConfiguration",
    "org.springframework.ai.mcp.server.autoconfigure.McpServerStreamableHttpWebFluxAutoConfiguration",
    "org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration",
    "org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration",
    "org.springframework.ai.mcp.server.common.autoconfigure.ToolCallbackConverterAutoConfiguration",
    "org.springframework.ai.mcp.server.common.autoconfigure.StatelessToolCallbackConverterAutoConfiguration"
})
@EnableScheduling
public class FluxMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxMcpApplication.class, args);
    }
}
