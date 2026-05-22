import axios from 'axios';

const trimValue = (value?: string) => value?.trim();

const removeTrailingSlashes = (value: string) => value.replace(/\/+$/, '');

const resolveApiBaseUrl = () => {
  const explicitBaseUrl = trimValue(import.meta.env.VITE_API_BASE_URL);
  if (explicitBaseUrl) {
    return removeTrailingSlashes(explicitBaseUrl);
  }

  if (import.meta.env.DEV) {
    return '/api';
  }

  const proxyTarget = trimValue(import.meta.env.VITE_MCP_GATEWAY_PROXY_TARGET);
  if (proxyTarget) {
    return `${removeTrailingSlashes(proxyTarget)}/api`;
  }

  if (typeof window !== 'undefined') {
    const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
    const host = window.location.hostname || 'localhost';
    return `${protocol}//${host}:8092/api`;
  }

  return '/api';
};

const apiBaseUrl = resolveApiBaseUrl();

export const mcpApi = axios.create({
  baseURL: apiBaseUrl,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
});

mcpApi.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err.response?.data?.message || err.message;
    console.error('MCP API Error:', msg);
    return Promise.reject(err);
  },
);
