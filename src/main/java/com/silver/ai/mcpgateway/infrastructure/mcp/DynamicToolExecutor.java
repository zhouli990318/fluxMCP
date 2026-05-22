// package com.silver.ai.mcpgateway.infrastructure.mcp;
//
// import com.silver.ai.mcpgateway.application.McpGatewayAppService;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.ai.tool.annotation.Tool;
// import org.springframework.ai.tool.annotation.ToolParam;
// import org.springframework.stereotype.Component;
//
// /**
//  * 动态 MCP Tool 执行器 — 根据 toolId 路由到实际 API 调用
//  */
// @Slf4j
// @Component
// @RequiredArgsConstructor
// public class DynamicToolExecutor {
//
//     private final McpGatewayAppService mcpService;
//
//     @Tool(description = "Execute a dynamically registered API tool by tool ID with JSON arguments")
//     public String executeTool(
//             @ToolParam(description = "The tool mapping ID") Long toolId,
//             @ToolParam(description = "JSON arguments for the tool") String arguments) {
//         log.info("Executing dynamic tool: id={}", toolId);
//         return mcpService.invokeTool(toolId, arguments);
//     }
// }
