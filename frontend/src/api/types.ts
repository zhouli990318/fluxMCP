export interface ApiResponse<T> {
  code: number;
  data: T;
  message: string | null;
  timestamp?: string;
}

export interface McpApiSource {
  id: number;
  name: string;
  description: string;
  baseUrl: string;
  authType: string;
  active: boolean;
  toolMappings: McpToolMapping[];
  healthStatus?: HealthStatus;
  lastHealthCheckAt?: string;
  lastHealthyAt?: string;
  consecutiveFailures?: number;
  lastErrorMessage?: string;
}

export interface CreateMcpApiSourcePayload {
  name: string;
  description: string;
  baseUrl: string;
  authType: string;
  authConfig: string;
  openApiSpec?: string;
}

export interface UpdateMcpApiSourcePayload {
  name: string;
  description: string;
  baseUrl: string;
  authType: string;
  authConfig: string;
}

export type ParseSourceType = 'SPEC' | 'URL';

export interface ParseOpenApiPayload {
  sourceType: ParseSourceType;
  content: string;
}

export type HealthStatus = 'UNKNOWN' | 'HEALTHY' | 'DEGRADED' | 'UNREACHABLE';

export interface SourceHealth {
  id: number;
  name: string;
  baseUrl: string;
  active: boolean;
  healthStatus: HealthStatus;
  lastHealthCheckAt?: string;
  lastHealthyAt?: string;
  consecutiveFailures: number;
  lastErrorMessage?: string;
}

export interface McpConnectionInfo {
  serverName: string;
  version: string;
  streamableHttpUrl: string;
}

export interface McpToolMapping {
  id: number;
  operationId: string;
  toolName: string;
  toolDescription: string;
  httpMethod: string;
  path: string;
  parameterSchema?: string;
  responseSchema?: string;
  examplePayload?: string;
  enabled: boolean;
}
