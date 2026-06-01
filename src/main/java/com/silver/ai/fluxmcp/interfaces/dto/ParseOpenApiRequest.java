package com.silver.ai.fluxmcp.interfaces.dto;

import lombok.Data;

@Data
public class ParseOpenApiRequest {

    private ParseSourceType sourceType;
    private String content;

    // Legacy compatibility for existing callers.
    private String openApiSpec;
    private String openApiUrl;

    public boolean isUrlSource() {
        return resolveSourceType() == ParseSourceType.URL;
    }

    public String requireContent() {
        String resolved = resolveContent();
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("解析内容不能为空");
        }
        return resolved;
    }

    private ParseSourceType resolveSourceType() {
        if (sourceType != null) {
            return sourceType;
        }
        if (openApiUrl != null && !openApiUrl.isBlank()) {
            return ParseSourceType.URL;
        }
        return ParseSourceType.SPEC;
    }

    private String resolveContent() {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (openApiUrl != null && !openApiUrl.isBlank()) {
            return openApiUrl;
        }
        return openApiSpec;
    }
}