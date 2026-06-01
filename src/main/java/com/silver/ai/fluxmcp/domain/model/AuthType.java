package com.silver.ai.fluxmcp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthType {
    NONE("无认证"),
    API_KEY("API Key"),
    BEARER_TOKEN("Bearer Token"),
    BASIC_AUTH("Basic Auth");

    private final String displayName;
}
