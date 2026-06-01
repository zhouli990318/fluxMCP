import { mcpApi } from './client';
import {
  ApiResponse,
  CreateMcpApiSourcePayload,
  McpApiSource,
  McpConnectionInfo,
  McpToolMapping,
  ParseOpenApiPayload,
  SourceHealth,
  UpdateMcpApiSourcePayload,
} from './types';

const BASE = '/v1/mcp';

export const fluxMcpApi = {
  listSources: () => mcpApi.get<ApiResponse<McpApiSource[]>>(`${BASE}/sources`).then((r) => r.data.data),
  getConnectionInfo: () => mcpApi.get<ApiResponse<McpConnectionInfo>>(`${BASE}/connection-info`).then((r) => r.data.data),
  getSourceConnectionInfo: (id: number) =>
    mcpApi.get<ApiResponse<McpConnectionInfo>>(`${BASE}/sources/${id}/connection-info`).then((r) => r.data.data),
  getSource: (id: number) => mcpApi.get<ApiResponse<McpApiSource>>(`${BASE}/sources/${id}`).then((r) => r.data.data),
  createSource: (data: CreateMcpApiSourcePayload) =>
    mcpApi.post<ApiResponse<McpApiSource>>(`${BASE}/sources`, data).then((r) => r.data.data),
  updateSource: (id: number, data: UpdateMcpApiSourcePayload) =>
    mcpApi.put<ApiResponse<McpApiSource>>(`${BASE}/sources/${id}`, data).then((r) => r.data.data),
  deleteSource: (id: number) => mcpApi.delete(`${BASE}/sources/${id}`),
  toggleSourceActive: (id: number) =>
    mcpApi.patch<ApiResponse<McpApiSource>>(`${BASE}/sources/${id}/toggle-active`).then((r) => r.data.data),
  parseSpec: (id: number, data: ParseOpenApiPayload) =>
    mcpApi.post<ApiResponse<McpToolMapping[]>>(`${BASE}/sources/${id}/parse`, data).then((r) => r.data.data),
  getTools: (id: number) => mcpApi.get<ApiResponse<McpToolMapping[]>>(`${BASE}/sources/${id}/tools`).then((r) => r.data.data),
  updateTool: (id: number, data: Record<string, unknown>) =>
    mcpApi.put<ApiResponse<McpToolMapping>>(`${BASE}/tools/${id}`, data).then((r) => r.data.data),
  testTool: (id: number, args: string) =>
    mcpApi.post<ApiResponse<string>>(`${BASE}/tools/${id}/test`, { arguments: args }).then((r) => r.data.data),
  listSourcesHealth: () => mcpApi.get<ApiResponse<SourceHealth[]>>(`${BASE}/sources/health`).then((r) => r.data.data),
  triggerHealthCheck: (id: number) =>
    mcpApi.post<ApiResponse<SourceHealth>>(`${BASE}/sources/${id}/health-check`).then((r) => r.data.data),
};
