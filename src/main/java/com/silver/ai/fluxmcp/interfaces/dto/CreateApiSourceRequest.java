package com.silver.ai.fluxmcp.interfaces.dto;

import com.silver.ai.fluxmcp.domain.model.AuthType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiSourceRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "baseUrl不能为空")
    private String baseUrl;

    private AuthType authType;

    private String authConfig;

    private String openApiSpec;
}