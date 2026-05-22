package com.silver.ai.mcpgateway.infrastructure.http;

import com.silver.ai.mcpgateway.domain.port.HttpClientPort;
import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class OkHttpClientAdapter implements HttpClientPort {

    private final HttpClient httpClient;

    public OkHttpClientAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String execute(String method, String url, Map<String, String> headers,
                          Map<String, String> queryParams, String body) {
        try {
            StringBuilder uriStr = new StringBuilder(url);
            if (queryParams != null && !queryParams.isEmpty()) {
                uriStr.append(url.contains("?") ? "&" : "?");
                queryParams.forEach((k, v) -> {
                    if (uriStr.charAt(uriStr.length() - 1) != '?' && uriStr.charAt(uriStr.length() - 1) != '&') {
                        uriStr.append("&");
                    }
                    uriStr.append(java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8))
                            .append("=")
                            .append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
                });
            }
            URI uri = URI.create(uriStr.toString());

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(30));

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpRequest.BodyPublisher bodyPublisher = (body != null && needsBody(method))
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

            requestBuilder.method(method.toUpperCase(), bodyPublisher);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode >= 400) {
                log.warn("HTTP request failed: {} {} -> {} {}", method, url, statusCode, response.body());
                throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                        "HTTP " + statusCode + ": " + response.body());
            }

            return response.body() != null ? response.body() : "";
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                    "HTTP request failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MCP_TOOL_INVOCATION_FAILED,
                    "HTTP request failed: " + e.getMessage(), e);
        }
    }

    private boolean needsBody(String method) {
        String upper = method.toUpperCase();
        return "POST".equals(upper) || "PUT".equals(upper) || "PATCH".equals(upper);
    }
}
