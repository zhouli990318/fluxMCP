package com.silver.ai.fluxmcp.domain.port;

import com.silver.ai.fluxmcp.domain.model.ToolMapping;

import java.util.List;

/**
 * OpenAPI 规范解析端口
 */
public interface OpenApiParserPort {

    /**
     * 解析 OpenAPI 规范文本，生成工具映射列表
     */
    List<ToolMapping> parse(String openApiSpec, Long apiSourceId);

    /**
     * 从 URL 获取并解析 OpenAPI 规范
     */
    List<ToolMapping> parseFromUrl(String url, Long apiSourceId);
}
