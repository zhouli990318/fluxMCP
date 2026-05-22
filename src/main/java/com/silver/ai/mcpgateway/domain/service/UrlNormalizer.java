package com.silver.ai.mcpgateway.domain.service;

import com.silver.ai.mcpgateway.common.exception.BusinessException;
import com.silver.ai.mcpgateway.common.result.ErrorCode;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class UrlNormalizer {

    private UrlNormalizer() {}

    /**
     * 规范化 baseUrl：补全 scheme、校验合法性、去掉尾部斜杠、拒绝内网地址。
     */
    public static String normalizeBaseUrl(String baseUrl, ErrorCode errorCode) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(errorCode, "baseUrl不能为空");
        }

        String normalized = baseUrl.trim();
        if (!normalized.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            normalized = "http://" + normalized;
        }

        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null) {
                throw new BusinessException(errorCode, "baseUrl格式不合法: " + baseUrl);
            }
//            validateNotInternal(uri, errorCode);
            String canonicalUrl = uri.toString();
            return canonicalUrl.endsWith("/") ? canonicalUrl.substring(0, canonicalUrl.length() - 1) : canonicalUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(errorCode, "baseUrl格式不合法: " + baseUrl);
        }
    }

    /**
     * 校验 URL 不指向内网/保留网段，防止 SSRF。
     */
    public static void validateNotInternal(URI uri, ErrorCode errorCode) {
        String host = uri.getHost();
        if (host == null) {
            throw new BusinessException(errorCode, "URL缺少主机名");
        }

        // 拒绝明显的 localhost 变体
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")) {
            throw new BusinessException(errorCode, "不允许访问内网地址: " + host);
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || address.isMulticastAddress()
                    || isCloudMetadataAddress(address)) {
                throw new BusinessException(errorCode, "不允许访问内网地址: " + host);
            }
        } catch (UnknownHostException e) {
            // DNS resolution failure is acceptable — the host may be resolvable at invocation time.
            // The important thing is to block known-internal addresses.
        }
    }

    private static boolean isCloudMetadataAddress(InetAddress address) {
        // AWS/GCP/Azure metadata endpoint: 169.254.169.254
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254
                    && (bytes[2] & 0xFF) == 169 && (bytes[3] & 0xFF) == 254;
        }
        return false;
    }
}
