package com.silver.ai.fluxmcp.infrastructure.mcp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class SourceScopedMcpRouterConfiguration {

    @Bean
    RouterFunction<ServerResponse> faviconRouter() {
        return RouterFunctions.route(GET("/favicon.ico"), request -> ServerResponse.noContent().build());
    }

    @Bean
    RouterFunction<ServerResponse> sourceScopedMcpRouter(SourceScopedMcpServerRegistry registry) {
        return registry::route;
    }
}