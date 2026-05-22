package com.silver.ai.mcpgateway.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProtocolType {
    HTTP("HTTP"),
    GRPC("gRPC");

    private final String displayName;
}
