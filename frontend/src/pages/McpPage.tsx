import { useEffect, useMemo, useState, lazy, Suspense } from 'react';
import {
  Box, Typography, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, IconButton, Select, MenuItem,
  FormControl, InputLabel, InputAdornment, LinearProgress, useMediaQuery,
  Tooltip, Drawer, CircularProgress, Chip, Switch,
  ToggleButton, ToggleButtonGroup, Card, CardContent,
  CardActions, Stack, Paper, TablePagination,
} from '@mui/material';
import {
  Add, Delete, PlayArrow, Edit, ContentCopy, Refresh, Api, Search,
} from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { mcpGatewayApi } from '../api/mcpApi';
import { CreateMcpApiSourcePayload, McpApiSource, McpToolMapping, ParseOpenApiPayload, UpdateMcpApiSourcePayload } from '../api/types';
import { useSnackbar } from 'notistack';

const LazyEditor = lazy(() => import('@monaco-editor/react'));

function MonacoEditor({ value, onChange, height = 180 }: { value: string; onChange: (v: string) => void; height?: number }) {
  return (
    <Box sx={{ height, borderRadius: 1, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
      <Suspense fallback={<Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}><CircularProgress size={24} /></Box>}>
        <LazyEditor height="100%" defaultLanguage="json" value={value} onChange={(v: string | undefined) => onChange(v || '')} options={{ minimap: { enabled: false }, fontSize: 13, scrollBeyondLastLine: false }} />
      </Suspense>
    </Box>
  );
}

function MobileTextarea({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <TextField multiline fullWidth minRows={4} maxRows={10} value={value} onChange={(e) => onChange(e.target.value)} sx={{ fontFamily: 'monospace' }} />;
}

type ParameterRow = {
  key: string; name: string; type: string; description: string; required: boolean;
};

const DEFAULT_PARAMETER_SCHEMA = '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}';

function parseParameterRows(parameterSchema?: string): ParameterRow[] {
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

function buildParameterSchema(rows: ParameterRow[]): string {
  const properties = rows.reduce<Record<string, { type: string; description: string }>>((acc, r) => {
    const n = r.name.trim();
    if (!n) return acc;
    acc[n] = { type: r.type || 'string', description: r.description || '' };
    return acc;
  }, {});
  const required = rows.filter((r) => r.required && r.name.trim()).map((r) => r.name.trim());
  return JSON.stringify({ type: 'object', properties, required }, null, 2);
}

function buildResponseSchema(rows: ParameterRow[]): string {
  const properties = rows.reduce<Record<string, { type: string; description: string }>>((acc, r) => {
    const n = r.name.trim();
    if (!n) return acc;
    acc[n] = { type: r.type || 'string', description: r.description || '' };
    return acc;
  }, {});
  return JSON.stringify({ type: 'object', properties }, null, 2);
}

type ExamplePayloadData = {
  description: string;
  request: {
    url?: string;
    headers?: Record<string, string>;
    body?: unknown;
  };
};

type ExampleItem = {
  key: string;
  description: string;
  url: string;
  body: string;
};

function parseExamplePayload(raw?: string): ExamplePayloadData[] | null {
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

function buildExamplePayload(items: ExamplePayloadData[]): string {
  return JSON.stringify({ examples: items }, null, 2);
}

const METHOD_COLORS: Record<string, string> = { GET: '#2e7d32', POST: '#1565c0', PUT: '#e65100', DELETE: '#c62828', PATCH: '#6a1b9a' };

type SourceFormState = {
  name: string;
  description: string;
  baseUrl: string;
};

type ToolUpdatePayload = {
  toolName: string;
  toolDescription: string;
  httpMethod: string;
  path: string;
  parameterSchema: string;
  responseSchema: string;
  examplePayload: string;
  enabled: boolean;
};

type CodeEditorComponent = typeof MonacoEditor;

function CreateEditSourceDialog({
  open,
  initialSource,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  initialSource: McpApiSource | null;
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: SourceFormState) => void;
}) {
  const [form, setForm] = useState<SourceFormState>({
    name: '',
    description: '',
    baseUrl: '',
  });

  useEffect(() => {
    if (!open) {
      return;
    }

    if (initialSource) {
      setForm({
        name: initialSource.name,
        description: initialSource.description || '',
        baseUrl: initialSource.baseUrl || '',
      });
      return;
    }

    setForm({ name: '', description: '', baseUrl: '' });
  }, [initialSource, open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{initialSource ? '编辑 API 源' : '添加 API 源'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="名称" required value={form.name} onChange={(e) => setForm((current) => ({ ...current, name: e.target.value }))} />
        <TextField label="描述" value={form.description} onChange={(e) => setForm((current) => ({ ...current, description: e.target.value }))} />
        <TextField label="Base URL" value={form.baseUrl} onChange={(e) => setForm((current) => ({ ...current, baseUrl: e.target.value }))} />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={() => onSubmit(form)} disabled={loading}>
          {initialSource ? '保存' : '创建'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function EditToolDialog({
  open,
  tool,
  EditorComponent,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  tool: McpToolMapping | null;
  EditorComponent: CodeEditorComponent;
  loading: boolean;
  onClose: () => void;
  onSubmit: (payload: ToolUpdatePayload) => void;
}) {
  const [localTool, setLocalTool] = useState<McpToolMapping | null>(tool);
  const [parameterEditMode, setParameterEditMode] = useState<string>('table');
  const [responseEditMode, setResponseEditMode] = useState<string>('table');
  const [paramRows, setParamRows] = useState<ParameterRow[]>([]);
  const [respRows, setRespRows] = useState<ParameterRow[]>([]);
  const [examples, setExamples] = useState<ExampleItem[]>([]);
  const [exampleIsLegacy, setExampleIsLegacy] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setLocalTool(tool);
    setParameterEditMode('table');
    setResponseEditMode('table');
    setParamRows(parseParameterRows(tool?.parameterSchema));
    setRespRows(parseParameterRows(tool?.responseSchema));
    const ex = parseExamplePayload(tool?.examplePayload);
    if (ex) {
      setExamples(ex.map((e, i) => ({
        key: `ex-${i}-${Date.now()}`,
        description: e.description || '',
        url: e.request?.url || '',
        body: e.request?.body ? JSON.stringify(e.request.body, null, 2) : '{}',
      })));
      setExampleIsLegacy(false);
    } else {
      setExamples([{ key: `ex-0-${Date.now()}`, description: '', url: '', body: tool?.examplePayload || '{}' }]);
      setExampleIsLegacy(true);
    }
  }, [open, tool]);

  const updateParameterRows = (updater: (rows: ParameterRow[]) => ParameterRow[]) => {
    setParamRows(updater);
  };

  const updateResponseRows = (updater: (rows: ParameterRow[]) => ParameterRow[]) => {
    setRespRows(updater);
  };

  const isBodyMethod = ['POST', 'PUT', 'PATCH'].includes(localTool?.httpMethod || '');

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>编辑工具</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="工具名" value={localTool?.toolName || ''} onChange={(e) => setLocalTool((current) => current ? { ...current, toolName: e.target.value } : current)} />
        <TextField label="描述" value={localTool?.toolDescription || ''} onChange={(e) => setLocalTool((current) => current ? { ...current, toolDescription: e.target.value } : current)} />
        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField label="HTTP 方法" value={localTool?.httpMethod || ''} onChange={(e) => setLocalTool((current) => current ? { ...current, httpMethod: e.target.value } : current)} />
          <TextField label="路径" fullWidth value={localTool?.path || ''} onChange={(e) => setLocalTool((current) => current ? { ...current, path: e.target.value } : current)} />
        </Box>

        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography sx={{ fontSize: 15, fontWeight: 600 }}>参数定义</Typography>
            <ToggleButtonGroup size="small" exclusive value={parameterEditMode} onChange={(_, v) => {
              if (!v) return;
              if (v === 'json') setLocalTool(c => c ? { ...c, parameterSchema: buildParameterSchema(paramRows) } : c);
              else setParamRows(parseParameterRows(localTool?.parameterSchema));
              setParameterEditMode(v);
            }}>
              <ToggleButton value="table">表格</ToggleButton>
              <ToggleButton value="json">JSON</ToggleButton>
            </ToggleButtonGroup>
          </Box>
          {parameterEditMode === 'table' ? (
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
              <Box sx={{
                display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1,
                borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'action.hover',
              }}>
                <Typography sx={{ flex: 1, fontSize: 13, fontWeight: 600 }}>参数名</Typography>
                <Typography sx={{ width: 100, fontSize: 13, fontWeight: 600 }}>类型</Typography>
                <Typography sx={{ flex: 2, fontSize: 13, fontWeight: 600 }}>描述</Typography>
                <Typography sx={{ width: 38, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>必填</Typography>
                <Typography sx={{ width: 28, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>操作</Typography>
              </Box>
              {paramRows.map((row) => (
                <Box key={row.key} sx={{
                  display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1.25,
                  borderBottom: '1px solid', borderColor: 'divider',
                }}>
                  <TextField size="small" placeholder="参数名" value={row.name} onChange={(e) => updateParameterRows((rows) => rows.map((item) => item.key === row.key ? { ...item, name: e.target.value } : item))} sx={{ flex: 1 }} />
                  <Select size="small" value={row.type} onChange={(e) => updateParameterRows((rows) => rows.map((item) => item.key === row.key ? { ...item, type: String(e.target.value) } : item))} sx={{ width: 100 }}>
                    {['string', 'number', 'integer', 'boolean', 'array', 'object'].map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                  </Select>
                  <TextField size="small" placeholder="描述" value={row.description} onChange={(e) => updateParameterRows((rows) => rows.map((item) => item.key === row.key ? { ...item, description: e.target.value } : item))} sx={{ flex: 2 }} />
                  <Switch size="small" checked={row.required} onChange={(_, value) => updateParameterRows((rows) => rows.map((item) => item.key === row.key ? { ...item, required: value } : item))} />
                  <IconButton size="small" color="error" onClick={() => updateParameterRows((rows) => rows.filter((item) => item.key !== row.key))}>
                    <Delete sx={{ fontSize: 16 }} />
                  </IconButton>
                </Box>
              ))}
              <Box sx={{ p: 1.5 }}>
                <Button size="small" startIcon={<Add />} onClick={() => updateParameterRows((rows) => [...rows, { key: `new-${Date.now()}`, name: '', type: 'string', description: '', required: false }])}>
                  添加参数
                </Button>
              </Box>
            </Paper>
          ) : (
            <EditorComponent value={localTool?.parameterSchema || DEFAULT_PARAMETER_SCHEMA} onChange={(value: string) => setLocalTool((current) => current ? { ...current, parameterSchema: value } : current)} height={200} />
          )}
        </Box>

        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography sx={{ fontSize: 15, fontWeight: 600 }}>响应 Schema</Typography>
            <ToggleButtonGroup size="small" exclusive value={responseEditMode} onChange={(_, v) => {
              if (!v) return;
              if (v === 'json') setLocalTool(c => c ? { ...c, responseSchema: buildResponseSchema(respRows) } : c);
              else setRespRows(parseParameterRows(localTool?.responseSchema));
              setResponseEditMode(v);
            }}>
              <ToggleButton value="table">表格</ToggleButton>
              <ToggleButton value="json">JSON</ToggleButton>
            </ToggleButtonGroup>
          </Box>
          {responseEditMode === 'table' ? (
            <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
              <Box sx={{
                display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1,
                borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'action.hover',
              }}>
                <Typography sx={{ flex: 1, fontSize: 13, fontWeight: 600 }}>字段名</Typography>
                <Typography sx={{ width: 100, fontSize: 13, fontWeight: 600 }}>类型</Typography>
                <Typography sx={{ flex: 2, fontSize: 13, fontWeight: 600 }}>描述</Typography>
                <Typography sx={{ width: 28, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>操作</Typography>
              </Box>
              {respRows.map((row) => (
                <Box key={row.key} sx={{
                  display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1.25,
                  borderBottom: '1px solid', borderColor: 'divider',
                }}>
                  <TextField size="small" placeholder="字段名" value={row.name} onChange={(e) => updateResponseRows((rows) => rows.map((item) => item.key === row.key ? { ...item, name: e.target.value } : item))} sx={{ flex: 1 }} />
                  <Select size="small" value={row.type} onChange={(e) => updateResponseRows((rows) => rows.map((item) => item.key === row.key ? { ...item, type: String(e.target.value) } : item))} sx={{ width: 100 }}>
                    {['string', 'number', 'integer', 'boolean', 'array', 'object'].map((type) => <MenuItem key={type} value={type}>{type}</MenuItem>)}
                  </Select>
                  <TextField size="small" placeholder="描述" value={row.description} onChange={(e) => updateResponseRows((rows) => rows.map((item) => item.key === row.key ? { ...item, description: e.target.value } : item))} sx={{ flex: 2 }} />
                  <IconButton size="small" color="error" onClick={() => updateResponseRows((rows) => rows.filter((item) => item.key !== row.key))}>
                    <Delete sx={{ fontSize: 16 }} />
                  </IconButton>
                </Box>
              ))}
              <Box sx={{ p: 1.5 }}>
                <Button size="small" startIcon={<Add />} onClick={() => updateResponseRows((rows) => [...rows, { key: `new-${Date.now()}`, name: '', type: 'string', description: '', required: false }])}>
                  添加字段
                </Button>
              </Box>
            </Paper>
          ) : (
            <EditorComponent value={localTool?.responseSchema || '{}'} onChange={(value: string) => setLocalTool((current) => current ? { ...current, responseSchema: value } : current)} height={160} />
          )}
        </Box>

        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography sx={{ fontSize: 15, fontWeight: 600 }}>调用示例</Typography>
            {!exampleIsLegacy && (
              <Button size="small" startIcon={<Add />} onClick={() => setExamples(prev => [...prev, { key: `ex-${Date.now()}`, description: '', url: '', body: '{}' }])}>
                添加示例
              </Button>
            )}
          </Box>
          {exampleIsLegacy ? (
            <Box>
              <EditorComponent value={localTool?.examplePayload || '{}'} onChange={(value: string) => setLocalTool((current) => current ? { ...current, examplePayload: value } : current)} height={140} />
              <Button size="small" sx={{ mt: 1 }} onClick={() => {
                setExampleIsLegacy(false);
                setExamples([{ key: `ex-${Date.now()}`, description: '', url: '', body: '{}' }]);
              }}>
                切换到结构化编辑
              </Button>
            </Box>
          ) : (
            <Stack spacing={2}>
              {examples.map((ex, idx) => (
                <Paper key={ex.key} variant="outlined" sx={{ p: 2 }}>
                  <Stack spacing={1.5}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography sx={{ fontSize: 13, fontWeight: 600, color: 'text.secondary' }}>示例 {idx + 1}</Typography>
                      {examples.length > 1 && (
                        <IconButton size="small" color="error" onClick={() => setExamples(prev => prev.filter(e => e.key !== ex.key))}>
                          <Delete sx={{ fontSize: 16 }} />
                        </IconButton>
                      )}
                    </Stack>
                    <TextField size="small" label="场景描述" placeholder="描述这个调用的典型使用场景" value={ex.description} onChange={(e) => setExamples(prev => prev.map(item => item.key === ex.key ? { ...item, description: e.target.value } : item))} fullWidth />
                    {isBodyMethod ? (
                      <>
                        <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>请求 Body</Typography>
                        <EditorComponent value={ex.body} onChange={(v: string) => setExamples(prev => prev.map(item => item.key === ex.key ? { ...item, body: v } : item))} height={100} />
                        {localTool?.path && (
                          <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                            <Typography sx={{ fontSize: 12, fontFamily: 'monospace', color: 'text.secondary', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                              {`curl -X ${localTool.httpMethod} '${localTool.path}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${ex.body}'`}
                            </Typography>
                          </Paper>
                        )}
                      </>
                    ) : (
                      <>
                        <TextField size="small" label="请求 URL 示例" placeholder="/pets?status=available&limit=10" value={ex.url} onChange={(e) => setExamples(prev => prev.map(item => item.key === ex.key ? { ...item, url: e.target.value } : item))} fullWidth slotProps={{ input: { sx: { fontFamily: 'monospace' } } }} />
                        {(ex.url || localTool?.path) && (
                          <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                            <Typography sx={{ fontSize: 13, fontFamily: 'monospace', color: 'text.secondary' }}>
                              <Box component="span" sx={{ fontWeight: 700, color: METHOD_COLORS[localTool?.httpMethod || ''] || '#666' }}>
                                {localTool?.httpMethod}
                              </Box>{' '}{ex.url || localTool?.path}
                            </Typography>
                          </Paper>
                        )}
                      </>
                    )}
                  </Stack>
                </Paper>
              ))}
            </Stack>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button
          variant="contained"
          onClick={() => {
            if (!localTool) return;
            let finalExample = localTool.examplePayload || '{}';
            if (!exampleIsLegacy) {
              finalExample = buildExamplePayload(examples.map(ex => {
                let bodyParsed: unknown = null;
                try { bodyParsed = JSON.parse(ex.body); } catch { bodyParsed = ex.body; }
                return {
                  description: ex.description,
                  request: {
                    ...(isBodyMethod ? { body: bodyParsed } : { url: ex.url || undefined }),
                  },
                };
              }));
            }
            onSubmit({
              toolName: localTool.toolName,
              toolDescription: localTool.toolDescription,
              httpMethod: localTool.httpMethod,
              path: localTool.path,
              parameterSchema: parameterEditMode === 'table' ? buildParameterSchema(paramRows) : (localTool.parameterSchema || DEFAULT_PARAMETER_SCHEMA),
              responseSchema: responseEditMode === 'table' ? buildResponseSchema(respRows) : (localTool.responseSchema || '{}'),
              examplePayload: finalExample,
              enabled: localTool.enabled,
            });
          }}
          disabled={loading || !localTool}
        >
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function TestToolDrawer({
  open,
  toolId,
  EditorComponent,
  onClose,
}: {
  open: boolean;
  toolId: number | null;
  EditorComponent: CodeEditorComponent;
  onClose: () => void;
}) {
  const [args, setArgs] = useState('{}');
  const [result, setResult] = useState('');

  useEffect(() => {
    if (!open) {
      return;
    }
    setArgs('{}');
    setResult('');
  }, [open, toolId]);

  const testToolMutation = useMutation({
    mutationFn: (payload: { id: number; args: string }) => mcpGatewayApi.testTool(payload.id, payload.args),
    onSuccess: (data) => setResult(typeof data === 'string' ? data : JSON.stringify(data, null, 2)),
    onError: (error: any) => setResult('Error: ' + (error.message || 'Unknown')),
  });

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: { width: { xs: '100%', md: 420 }, p: 3 },
      }}
    >
      <Typography variant="h6" sx={{ mb: 2 }}>测试工具调用</Typography>
      <EditorComponent value={args} onChange={setArgs} height={160} />
      <Button
        variant="contained"
        startIcon={<PlayArrow />}
        fullWidth
        onClick={() => toolId && testToolMutation.mutate({ id: toolId, args })}
        sx={{ mt: 2 }}
        disabled={testToolMutation.isPending || !toolId}
      >
        执行
      </Button>
      {result && (
        <Paper variant="outlined" sx={{ mt: 2, p: 2, maxHeight: 300, overflow: 'auto' }}>
          <Typography sx={{ fontSize: 13, fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>{result}</Typography>
        </Paper>
      )}
    </Drawer>
  );
}

export default function McpPage() {
  const isMobile = useMediaQuery('(max-width:899px)');
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedSource, setSelectedSource] = useState<McpApiSource | null>(null);
  const [testTool, setTestTool] = useState<McpToolMapping | null>(null);
  const [parseMode, setParseMode] = useState<string>('paste');
  const [specContent, setSpecContent] = useState('');
  const [specUrl, setSpecUrl] = useState('');
  const [editingSource, setEditingSource] = useState<McpApiSource | null>(null);
  const [editingTool, setEditingTool] = useState<McpToolMapping | null>(null);
  const [toolSearch, setToolSearch] = useState('');
  const [toolPage, setToolPage] = useState(0);
  const [toolRowsPerPage, setToolRowsPerPage] = useState(10);

  useEffect(() => { setToolSearch(''); setToolPage(0); }, [selectedSource?.id]);

  const { data: sources = [], isLoading } = useQuery({ queryKey: ['mcp-sources'], queryFn: mcpGatewayApi.listSources });
  const { data: connectionInfo } = useQuery({
    queryKey: ['mcp-source-connection-info', selectedSource?.id],
    queryFn: () => mcpGatewayApi.getSourceConnectionInfo(selectedSource!.id),
    enabled: !!selectedSource,
  });
  const { data: tools = [], refetch: refetchTools } = useQuery({
    queryKey: ['mcp-tools', selectedSource?.id],
    queryFn: () => mcpGatewayApi.getTools(selectedSource!.id),
    enabled: !!selectedSource,
  });

  const filteredTools = useMemo(() => {
    const sorted = [...tools].sort((a: McpToolMapping, b: McpToolMapping) => b.id - a.id);
    if (!toolSearch.trim()) return sorted;
    const q = toolSearch.toLowerCase();
    return sorted.filter((t: McpToolMapping) =>
      t.toolName.toLowerCase().includes(q) ||
      (t.toolDescription || '').toLowerCase().includes(q) ||
      t.path.toLowerCase().includes(q)
    );
  }, [tools, toolSearch]);

  const paginatedTools = useMemo(() => {
    const start = toolPage * toolRowsPerPage;
    return filteredTools.slice(start, start + toolRowsPerPage);
  }, [filteredTools, toolPage, toolRowsPerPage]);

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: CreateMcpApiSourcePayload) => mcpGatewayApi.createSource(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mcp-sources'] });
      setCreateOpen(false);
      setEditingSource(null);
      enqueueSnackbar('创建成功', { variant: 'success' });
    },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '创建失败', { variant: 'error' }),
  });
  const updateSourceMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateMcpApiSourcePayload }) => mcpGatewayApi.updateSource(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mcp-sources'] });
      setCreateOpen(false);
      setEditingSource(null);
      enqueueSnackbar('更新成功', { variant: 'success' });
    },
    onError: () => enqueueSnackbar('更新失败', { variant: 'error' }),
  });
  const deleteMutation = useMutation({
    mutationFn: mcpGatewayApi.deleteSource,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mcp-sources'] }); setSelectedSource(null); enqueueSnackbar('删除成功', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '删除失败', { variant: 'error' }),
  });
  const parseMutation = useMutation({
    mutationFn: (data: ParseOpenApiPayload) => mcpGatewayApi.parseSpec(selectedSource!.id, data),
    onSuccess: () => { refetchTools(); enqueueSnackbar('解析完成', { variant: 'success' }); },
    onError: () => enqueueSnackbar('解析失败', { variant: 'error' }),
  });
  const toggleToolMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => mcpGatewayApi.updateTool(id, { enabled }),
    onSuccess: () => { refetchTools(); enqueueSnackbar('工具状态已更新', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '操作失败', { variant: 'error' }),
  });
  const updateToolMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Record<string, unknown> }) => mcpGatewayApi.updateTool(id, data),
    onSuccess: () => { refetchTools(); setEditingTool(null); enqueueSnackbar('已更新', { variant: 'success' }); },
    onError: () => enqueueSnackbar('更新失败', { variant: 'error' }),
  });
  const toggleSourceMutation = useMutation({
    mutationFn: mcpGatewayApi.toggleSourceActive,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mcp-sources'] });
      queryClient.invalidateQueries({ queryKey: ['mcp-health'] });
      enqueueSnackbar('源状态已切换', { variant: 'success' });
    },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '操作失败', { variant: 'error' }),
  });
  const healthCheckMutation = useMutation({
    mutationFn: mcpGatewayApi.triggerHealthCheck,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mcp-health'] });
      enqueueSnackbar('健康检查已完成', { variant: 'success' });
    },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '检查失败', { variant: 'error' }),
  });
  const { data: sourceHealth = [] } = useQuery({
    queryKey: ['mcp-health'],
    queryFn: mcpGatewayApi.listSourcesHealth,
    refetchInterval: 30000,
  });
  const healthMap = new Map(sourceHealth.map((h) => [h.id, h]));

  const openCreateDialog = () => {
    setEditingSource(null);
    setCreateOpen(true);
  };
  const openEditSourceDialog = (source: McpApiSource) => {
    setEditingSource(source);
    setCreateOpen(true);
  };
  const handleSourceSubmit = (payload: SourceFormState) => {
    const normalizedPayload = {
      ...payload,
      name: payload.name.trim(),
      description: payload.description.trim(),
      baseUrl: payload.baseUrl.trim(),
    };
    if (editingSource) {
      updateSourceMutation.mutate({ id: editingSource.id, data: normalizedPayload });
      return;
    }
    createMutation.mutate(normalizedPayload);
  };

  const copyText = async (value: string, label: string) => {
    try { await navigator.clipboard.writeText(value); enqueueSnackbar(`${label} 已复制`, { variant: 'success' }); }
    catch { enqueueSnackbar('复制失败', { variant: 'error' }); }
  };

  const CodeEditor = isMobile ? MobileTextarea : MonacoEditor;

  return (
    <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
      {/* Header */}
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Typography variant="h5">API 数据源</Typography>
        <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>添加 API 源</Button>
      </Stack>
      {isLoading && <LinearProgress sx={{ mb: 2 }} />}

      {/* Source grid */}
      {sources.length === 0 && !isLoading ? (
        <Paper variant="outlined" sx={{ p: 6, textAlign: 'center' }}>
          <Api sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
          <Typography variant="h6" color="text.secondary">暂无 API 源</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>添加 API 源来配置 MCP 工具映射</Typography>
          <Button variant="contained" startIcon={<Add />} onClick={openCreateDialog}>添加 API 源</Button>
        </Paper>
      ) : (
        <Stack direction="row" flexWrap="wrap" spacing={2} useFlexGap sx={{ mb: 3 }}>
          {sources.map((s: McpApiSource) => {
            const selected = selectedSource?.id === s.id;
            const health = healthMap.get(s.id);
            const status = health?.healthStatus ?? 'UNKNOWN';
            const healthLabel =
              !s.active ? '停用' :
              status === 'HEALTHY' ? '健康' :
              status === 'DEGRADED' ? '缓慢' :
              status === 'UNREACHABLE' ? '不可达' : '未知';
            const chipColor: 'success' | 'warning' | 'error' | 'default' =
              !s.active ? 'default' :
              status === 'HEALTHY' ? 'success' :
              status === 'DEGRADED' ? 'warning' :
              status === 'UNREACHABLE' ? 'error' : 'default';
            return (
              <Card
                key={s.id}
                variant="outlined"
                onClick={() => setSelectedSource(s)}
                sx={{
                  width: 200, cursor: 'pointer',
                  borderColor: selected ? 'primary.main' : undefined,
                  borderWidth: selected ? 2 : 1,
                }}
              >
                <CardContent sx={{ pb: 1 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Api color={s.active ? 'primary' : 'disabled'} />
                    <Switch size="small" checked={s.active} onClick={(e) => e.stopPropagation()} onChange={() => toggleSourceMutation.mutate(s.id)} />
                  </Stack>
                  <Typography fontWeight={600} sx={{ mt: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {s.name}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {s.description || '—'}
                  </Typography>
                </CardContent>
                <CardActions sx={{ pt: 0 }}>
                  <Chip label={healthLabel} size="small" color={chipColor} variant="outlined" />
                </CardActions>
              </Card>
            );
          })}
        </Stack>
      )}

      {/* Selected source detail */}
      {selectedSource && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          {/* Source header */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
            <Box>
              <Typography variant="h6">{selectedSource.name}</Typography>
              <Typography variant="body2" color="text.secondary">{selectedSource.description}</Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <IconButton size="small" onClick={() => openEditSourceDialog(selectedSource)}><Edit fontSize="small" /></IconButton>
              <IconButton size="small" color="error" onClick={() => deleteMutation.mutate(selectedSource.id)}><Delete fontSize="small" /></IconButton>
            </Stack>
          </Stack>

          {/* Connection info */}
          {connectionInfo && (
            <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
              <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                {connectionInfo.serverName} {connectionInfo.version}
              </Typography>
              {[
                { label: 'HTTP', value: connectionInfo.streamableHttpUrl },
              ].map((item) => (
                <Stack key={item.label} direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                  <Chip label={item.label} size="small" sx={{ width: 50 }} />
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {item.value}
                  </Typography>
                  <Tooltip title="复制">
                    <IconButton size="small" onClick={() => copyText(item.value, item.label)}>
                      <ContentCopy sx={{ fontSize: 14 }} />
                    </IconButton>
                  </Tooltip>
                </Stack>
              ))}
            </Paper>
          )}

          {/* Parse OpenAPI */}
          <Box sx={{ mb: 3 }}>
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1.5 }}>
              <Typography fontWeight={600}>解析 OpenAPI</Typography>
              <ToggleButtonGroup size="small" exclusive value={parseMode} onChange={(_, v) => v && setParseMode(v)}>
                <ToggleButton value="paste">粘贴 Spec</ToggleButton>
                <ToggleButton value="url">URL 导入</ToggleButton>
              </ToggleButtonGroup>
            </Stack>
            {parseMode === 'paste' ? (
              <Box>
                <CodeEditor value={specContent} onChange={setSpecContent} height={180} />
                <Button variant="outlined" startIcon={<Refresh />} onClick={() => parseMutation.mutate({ sourceType: 'SPEC', content: specContent })} disabled={parseMutation.isPending} sx={{ mt: 1 }}>
                  解析
                </Button>
              </Box>
            ) : (
              <Stack direction="row" spacing={1}>
                <TextField size="small" fullWidth value={specUrl} onChange={(e) => setSpecUrl(e.target.value)} placeholder="https://petstore.swagger.io/v2/swagger.json" />
                <Button variant="outlined" onClick={() => parseMutation.mutate({ sourceType: 'URL', content: specUrl })} disabled={parseMutation.isPending}>导入</Button>
              </Stack>
            )}
          </Box>

          {/* Tools list */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography fontWeight={600}>工具 ({filteredTools.length !== tools.length ? `${filteredTools.length} / ` : ''}{tools.length})</Typography>
            {tools.length > 0 && (
              <TextField
                size="small"
                placeholder="搜索工具名、描述、路径…"
                value={toolSearch}
                onChange={(e) => { setToolSearch(e.target.value); setToolPage(0); }}
                sx={{ width: 260 }}
                slotProps={{ input: { startAdornment: <InputAdornment position="start"><Search sx={{ fontSize: 18, color: 'text.disabled' }} /></InputAdornment> } }}
              />
            )}
          </Stack>
          <Paper variant="outlined">
            {filteredTools.length === 0 ? (
              <Box sx={{ py: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  {tools.length === 0 ? '暂无工具，请先解析 OpenAPI 规范' : '没有匹配的工具'}
                </Typography>
              </Box>
            ) : (
              <>
                {paginatedTools.map((t: McpToolMapping, i: number) => (
                  <Stack key={t.id} direction="row" alignItems="center" spacing={1.5} sx={{
                    px: 2, py: 1.5,
                    borderBottom: i < paginatedTools.length - 1 ? '1px solid' : 'none',
                    borderColor: 'divider',
                  }}>
                    <Chip label={t.httpMethod} size="small" sx={{
                      fontFamily: 'monospace', fontWeight: 700, fontSize: 10,
                      bgcolor: `${METHOD_COLORS[t.httpMethod] || '#666'}18`,
                      color: METHOD_COLORS[t.httpMethod] || '#666',
                    }} />
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography sx={{ fontFamily: 'monospace', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {t.toolName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {t.path}
                      </Typography>
                    </Box>
                    <Switch size="small" checked={t.enabled} onChange={(_, v) => toggleToolMutation.mutate({ id: t.id, enabled: v })} />
                    <IconButton size="small" onClick={() => setEditingTool(t)}><Edit sx={{ fontSize: 18 }} /></IconButton>
                    <IconButton size="small" onClick={() => setTestTool(t)}><PlayArrow sx={{ fontSize: 18 }} /></IconButton>
                  </Stack>
                ))}
                <TablePagination
                  component="div"
                  count={filteredTools.length}
                  page={toolPage}
                  onPageChange={(_, p) => setToolPage(p)}
                  rowsPerPage={toolRowsPerPage}
                  onRowsPerPageChange={(e) => { setToolRowsPerPage(parseInt(e.target.value, 10)); setToolPage(0); }}
                  rowsPerPageOptions={[10, 25, 50]}
                  labelRowsPerPage="每页"
                  labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
                  sx={{ borderTop: '1px solid', borderColor: 'divider' }}
                />
              </>
            )}
          </Paper>
        </Paper>
      )}

      <CreateEditSourceDialog
        open={createOpen}
        initialSource={editingSource}
        loading={createMutation.isPending || updateSourceMutation.isPending}
        onClose={() => {
          setCreateOpen(false);
          setEditingSource(null);
        }}
        onSubmit={handleSourceSubmit}
      />

      <EditToolDialog
        open={editingTool !== null}
        tool={editingTool}
        EditorComponent={CodeEditor}
        loading={updateToolMutation.isPending}
        onClose={() => setEditingTool(null)}
        onSubmit={(payload) => editingTool && updateToolMutation.mutate({ id: editingTool.id, data: payload })}
      />

      <TestToolDrawer
        open={testTool !== null}
        toolId={testTool?.id || null}
        EditorComponent={CodeEditor}
        onClose={() => setTestTool(null)}
      />
    </Box>
  );
}
