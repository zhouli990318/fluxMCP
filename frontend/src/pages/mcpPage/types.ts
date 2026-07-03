import React from 'react';

export type ParameterRow = {
  key: string; name: string; type: string; description: string; required: boolean;
};

export type ExamplePayloadData = {
  description: string;
  request: {
    url?: string;
    headers?: Record<string, string>;
    body?: unknown;
  };
};

export type ExampleItem = {
  key: string;
  description: string;
  url: string;
  body: string;
};

export type SourceFormState = {
  name: string;
  description: string;
  baseUrl: string;
  authType: string;
  updateAuthConfig: boolean;
  authFields: SourceAuthFields;
};

export type SourceAuthFields = {
  apiKey: string;
  headerName: string;
  token: string;
  username: string;
  password: string;
};

export type ToolUpdatePayload = {
  toolName: string;
  toolDescription: string;
  httpMethod: string;
  path: string;
  parameterSchema: string;
  responseSchema: string;
  examplePayload: string;
  enabled: boolean;
};

export type CodeEditorComponent = React.NamedExoticComponent<{ value: string; onChange: (v: string) => void; height?: number }>;

export const METHOD_COLORS: Record<string, string> = { GET: '#2e7d32', POST: '#1565c0', PUT: '#e65100', DELETE: '#c62828', PATCH: '#6a1b9a' };
