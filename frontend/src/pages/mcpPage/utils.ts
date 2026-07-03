import type { ParameterRow, SourceAuthFields, ExamplePayloadData } from './types';

export const DEFAULT_PARAMETER_SCHEMA = '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}';

export function parseParameterRows(parameterSchema?: string): ParameterRow[] {
  try {
    const parsed = JSON.parse(parameterSchema || DEFAULT_PARAMETER_SCHEMA) as {
      properties?: Record<string, { type?: string; description?: string }>;
      required?: string[];
    };
    const props = parsed.properties || {};
    const req = new Set(parsed.required || []);
    return Object.entries(props).map(([name, v], i) => ({
      key: `${name}-${i}`, name, type: v?.type || 'string', description: v?.description || '', required: req.has(name),
    }));
  } catch { return []; }
}

export function buildParameterSchema(rows: ParameterRow[]): string {
  const properties = rows.reduce<Record<string, { type: string; description: string }>>((acc, r) => {
    const n = r.name.trim();
    if (!n) return acc;
    acc[n] = { type: r.type || 'string', description: r.description || '' };
    return acc;
  }, {});
  const required = rows.filter((r) => r.required && r.name.trim()).map((r) => r.name.trim());
  return JSON.stringify({ type: 'object', properties, required }, null, 2);
}

export function buildResponseSchema(rows: ParameterRow[]): string {
  const properties = rows.reduce<Record<string, { type: string; description: string }>>((acc, r) => {
    const n = r.name.trim();
    if (!n) return acc;
    acc[n] = { type: r.type || 'string', description: r.description || '' };
    return acc;
  }, {});
  return JSON.stringify({ type: 'object', properties }, null, 2);
}

export function parseExamplePayload(raw?: string): ExamplePayloadData[] | null {
  try {
    const parsed = JSON.parse(raw || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    if (Object.keys(parsed).length === 0) return [];
    if (Array.isArray(parsed.examples)) return parsed.examples;
    if (typeof parsed.description === 'string' || parsed.request) {
      return [{ description: parsed.description || '', request: parsed.request || {} }];
    }
    return null;
  } catch { return null; }
}

export function buildExamplePayload(items: ExamplePayloadData[]): string {
  return JSON.stringify({ examples: items }, null, 2);
}

export function createEmptyAuthFields(): SourceAuthFields {
  return { apiKey: '', headerName: '', token: '', username: '', password: '' };
}

export function buildAuthConfig(authType: string, authFields: SourceAuthFields): string {
  switch (authType) {
    case 'API_KEY':
      return JSON.stringify({
        apiKey: authFields.apiKey.trim(),
        ...(authFields.headerName.trim() ? { headerName: authFields.headerName.trim() } : {}),
      });
    case 'BEARER_TOKEN':
      return JSON.stringify({ token: authFields.token.trim() });
    case 'BASIC_AUTH':
      return JSON.stringify({ username: authFields.username.trim(), password: authFields.password.trim() });
    default:
      return '';
  }
}

export function validateAuthFields(authType: string, authFields: SourceAuthFields): string | null {
  if (authType === 'API_KEY') return authFields.apiKey.trim() ? null : '请填写 API Key';
  if (authType === 'BEARER_TOKEN') return authFields.token.trim() ? null : '请填写 Bearer Token';
  if (authType === 'BASIC_AUTH') {
    if (!authFields.username.trim()) return '请填写用户名';
    return authFields.password.trim() ? null : '请填写密码';
  }
  return null;
}
