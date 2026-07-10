import { useEffect, useState, useCallback, memo } from 'react';
import {
  Box, Typography, Button, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, Select, MenuItem, IconButton, Switch,
  ToggleButton, ToggleButtonGroup, Stack, Paper,
} from '@mui/material';
import { Add, Delete } from '@mui/icons-material';
import type { McpToolMapping } from '../../api/types';
import type { ParameterRow, ExampleItem, ToolUpdatePayload, CodeEditorComponent } from './types';
import { METHOD_COLORS } from './types';
import {
  DEFAULT_PARAMETER_SCHEMA, parseParameterRows, buildParameterSchema,
  buildResponseSchema, parseExamplePayload, buildExamplePayload,
} from './utils';

const createEmptyTool = (): McpToolMapping => ({
  id: 0,
  operationId: '',
  toolName: '',
  toolDescription: '',
  httpMethod: 'GET',
  path: '/',
  parameterSchema: DEFAULT_PARAMETER_SCHEMA,
  responseSchema: '{}',
  examplePayload: '{}',
  enabled: true,
});

type ToolEditableField =
  | 'toolName'
  | 'toolDescription'
  | 'httpMethod'
  | 'path'
  | 'parameterSchema'
  | 'responseSchema'
  | 'examplePayload';

const SchemaTableRow = memo(function SchemaTableRow({
  row, namePlaceholder, showRequired, onUpdate, onDelete,
}: {
  row: ParameterRow;
  namePlaceholder: string;
  showRequired?: boolean;
  onUpdate: (key: string, field: string, value: string | boolean) => void;
  onDelete: (key: string) => void;
}) {
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1.25, borderBottom: '1px solid', borderColor: 'divider' }}>
      <TextField size="small" placeholder={namePlaceholder} value={row.name} onChange={(e) => onUpdate(row.key, 'name', e.target.value)} sx={{ flex: 1 }} />
      <Select size="small" value={row.type} onChange={(e) => onUpdate(row.key, 'type', String(e.target.value))} sx={{ width: 100 }}>
        {['string', 'number', 'integer', 'boolean', 'array', 'object'].map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
      </Select>
      <TextField size="small" placeholder="描述" value={row.description} onChange={(e) => onUpdate(row.key, 'description', e.target.value)} sx={{ flex: 2 }} />
      {showRequired && <Switch size="small" checked={row.required} onChange={(_, v) => onUpdate(row.key, 'required', v)} />}
      <IconButton size="small" color="error" onClick={() => onDelete(row.key)}><Delete sx={{ fontSize: 16 }} /></IconButton>
    </Box>
  );
});

const ExampleItemRow = memo(function ExampleItemRow({
  ex, idx, total, isBodyMethod, httpMethod, path, EditorComponent, onUpdate, onDelete,
}: {
  ex: ExampleItem;
  idx: number;
  total: number;
  isBodyMethod: boolean;
  httpMethod: string;
  path: string;
  EditorComponent: CodeEditorComponent;
  onUpdate: (key: string, field: string, value: string) => void;
  onDelete: (key: string) => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1.5}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography sx={{ fontSize: 13, fontWeight: 600, color: 'text.secondary' }}>示例 {idx + 1}</Typography>
          {total > 1 && (
            <IconButton size="small" color="error" onClick={() => onDelete(ex.key)}><Delete sx={{ fontSize: 16 }} /></IconButton>
          )}
        </Stack>
        <TextField size="small" label="场景描述" placeholder="描述这个调用的典型使用场景" value={ex.description}
          onChange={(e) => onUpdate(ex.key, 'description', e.target.value)} fullWidth />
        {isBodyMethod ? (
          <>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>请求 Body</Typography>
            <EditorComponent value={ex.body} onChange={(v: string) => onUpdate(ex.key, 'body', v)} height={100} />
            {path && (
              <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                <Typography sx={{ fontSize: 12, fontFamily: 'monospace', color: 'text.secondary', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                  {`curl -X ${httpMethod} '${path}' \
  -H 'Content-Type: application/json' \
  -d '${ex.body}'`}
                </Typography>
              </Paper>
            )}
          </>
        ) : (
          <>
            <TextField size="small" label="请求 URL 示例" placeholder="/pets?status=available&limit=10" value={ex.url}
              onChange={(e) => onUpdate(ex.key, 'url', e.target.value)} fullWidth slotProps={{ input: { sx: { fontFamily: 'monospace' } } }} />
            {(ex.url || path) && (
              <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                <Typography sx={{ fontSize: 13, fontFamily: 'monospace', color: 'text.secondary' }}>
                  <Box component="span" sx={{ fontWeight: 700, color: METHOD_COLORS[httpMethod] || '#666' }}>
                    {httpMethod}
                  </Box>{' '}{ex.url || path}
                </Typography>
              </Paper>
            )}
          </>
        )}
      </Stack>
    </Paper>
  );
});

const ParameterSchemaSection = memo(function ParameterSchemaSection({
  editMode,
  rows,
  jsonValue,
  EditorComponent,
  onEditModeChange,
  onRowUpdate,
  onRowDelete,
  onAddRow,
  onJsonChange,
}: {
  editMode: string;
  rows: ParameterRow[];
  jsonValue: string;
  EditorComponent: CodeEditorComponent;
  onEditModeChange: (_: unknown, nextMode: string | null) => void;
  onRowUpdate: (key: string, field: string, value: string | boolean) => void;
  onRowDelete: (key: string) => void;
  onAddRow: () => void;
  onJsonChange: (value: string) => void;
}) {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 600 }}>参数定义</Typography>
        <ToggleButtonGroup size="small" exclusive value={editMode} onChange={onEditModeChange}>
          <ToggleButton value="table">表格</ToggleButton>
          <ToggleButton value="json">JSON</ToggleButton>
        </ToggleButtonGroup>
      </Box>
      {editMode === 'table' ? (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography sx={{ flex: 1, fontSize: 13, fontWeight: 600 }}>参数名</Typography>
            <Typography sx={{ width: 100, fontSize: 13, fontWeight: 600 }}>类型</Typography>
            <Typography sx={{ flex: 2, fontSize: 13, fontWeight: 600 }}>描述</Typography>
            <Typography sx={{ width: 38, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>必填</Typography>
            <Typography sx={{ width: 28, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>操作</Typography>
          </Box>
          {rows.map((row) => (
            <SchemaTableRow key={row.key} row={row} namePlaceholder="参数名" showRequired onUpdate={onRowUpdate} onDelete={onRowDelete} />
          ))}
          <Box sx={{ p: 1.5 }}>
            <Button size="small" startIcon={<Add />} onClick={onAddRow}>添加参数</Button>
          </Box>
        </Paper>
      ) : (
        <EditorComponent value={jsonValue} onChange={onJsonChange} height={200} />
      )}
    </Box>
  );
});

const ResponseSchemaSection = memo(function ResponseSchemaSection({
  editMode,
  rows,
  jsonValue,
  EditorComponent,
  onEditModeChange,
  onRowUpdate,
  onRowDelete,
  onAddRow,
  onJsonChange,
}: {
  editMode: string;
  rows: ParameterRow[];
  jsonValue: string;
  EditorComponent: CodeEditorComponent;
  onEditModeChange: (_: unknown, nextMode: string | null) => void;
  onRowUpdate: (key: string, field: string, value: string | boolean) => void;
  onRowDelete: (key: string) => void;
  onAddRow: () => void;
  onJsonChange: (value: string) => void;
}) {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 600 }}>响应 Schema</Typography>
        <ToggleButtonGroup size="small" exclusive value={editMode} onChange={onEditModeChange}>
          <ToggleButton value="table">表格</ToggleButton>
          <ToggleButton value="json">JSON</ToggleButton>
        </ToggleButtonGroup>
      </Box>
      {editMode === 'table' ? (
        <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', px: 2, py: 1, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'action.hover' }}>
            <Typography sx={{ flex: 1, fontSize: 13, fontWeight: 600 }}>字段名</Typography>
            <Typography sx={{ width: 100, fontSize: 13, fontWeight: 600 }}>类型</Typography>
            <Typography sx={{ flex: 2, fontSize: 13, fontWeight: 600 }}>描述</Typography>
            <Typography sx={{ width: 28, fontSize: 13, fontWeight: 600, textAlign: 'center' }}>操作</Typography>
          </Box>
          {rows.map((row) => (
            <SchemaTableRow key={row.key} row={row} namePlaceholder="字段名" onUpdate={onRowUpdate} onDelete={onRowDelete} />
          ))}
          <Box sx={{ p: 1.5 }}>
            <Button size="small" startIcon={<Add />} onClick={onAddRow}>添加字段</Button>
          </Box>
        </Paper>
      ) : (
        <EditorComponent value={jsonValue} onChange={onJsonChange} height={160} />
      )}
    </Box>
  );
});

const ExamplesSection = memo(function ExamplesSection({
  exampleIsLegacy,
  legacyExampleValue,
  examples,
  isBodyMethod,
  httpMethod,
  path,
  EditorComponent,
  onAddExample,
  onLegacyJsonChange,
  onSwitchToStructuredEdit,
  onExampleUpdate,
  onExampleDelete,
}: {
  exampleIsLegacy: boolean;
  legacyExampleValue: string;
  examples: ExampleItem[];
  isBodyMethod: boolean;
  httpMethod: string;
  path: string;
  EditorComponent: CodeEditorComponent;
  onAddExample: () => void;
  onLegacyJsonChange: (value: string) => void;
  onSwitchToStructuredEdit: () => void;
  onExampleUpdate: (key: string, field: string, value: string) => void;
  onExampleDelete: (key: string) => void;
}) {
  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Typography sx={{ fontSize: 15, fontWeight: 600 }}>调用示例</Typography>
        {!exampleIsLegacy && (
          <Button size="small" startIcon={<Add />} onClick={onAddExample}>添加示例</Button>
        )}
      </Box>
      {exampleIsLegacy ? (
        <Box>
          <EditorComponent value={legacyExampleValue} onChange={onLegacyJsonChange} height={140} />
          <Button size="small" sx={{ mt: 1 }} onClick={onSwitchToStructuredEdit}>切换到结构化编辑</Button>
        </Box>
      ) : (
        <Stack spacing={2}>
          {examples.map((ex, idx) => (
            <ExampleItemRow
              key={ex.key}
              ex={ex}
              idx={idx}
              total={examples.length}
              isBodyMethod={isBodyMethod}
              httpMethod={httpMethod}
              path={path}
              EditorComponent={EditorComponent}
              onUpdate={onExampleUpdate}
              onDelete={onExampleDelete}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
});

export const EditToolDialog = memo(function EditToolDialog({
  open, tool, EditorComponent, loading, onClose, onSubmit,
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
  const isCreateMode = tool === null;

  useEffect(() => {
    if (!open) return;
    const nextTool = tool ?? createEmptyTool();
    setLocalTool(nextTool);
    setParameterEditMode('table');
    setResponseEditMode('table');
    setParamRows(parseParameterRows(nextTool.parameterSchema));
    setRespRows(parseParameterRows(nextTool.responseSchema));
    const parsedExamples = parseExamplePayload(nextTool.examplePayload);
    if (parsedExamples) {
      setExamples(parsedExamples.map((item, index) => ({
        key: `ex-${index}-${Date.now()}`,
        description: item.description || '',
        url: item.request?.url || '',
        body: item.request?.body ? JSON.stringify(item.request.body, null, 2) : '{}',
      })));
      setExampleIsLegacy(false);
      return;
    }

    setExamples([{ key: `ex-0-${Date.now()}`, description: '', url: '', body: nextTool.examplePayload || '{}' }]);
    setExampleIsLegacy(true);
  }, [open, tool]);

  const handleUpdateParam = useCallback((key: string, field: string, value: string | boolean) => {
    setParamRows((rows) => rows.map((item) => item.key === key ? { ...item, [field]: value } : item));
  }, []);

  const handleDeleteParam = useCallback((key: string) => {
    setParamRows((rows) => rows.filter((item) => item.key !== key));
  }, []);

  const handleUpdateResp = useCallback((key: string, field: string, value: string | boolean) => {
    setRespRows((rows) => rows.map((item) => item.key === key ? { ...item, [field]: value } : item));
  }, []);

  const handleDeleteResp = useCallback((key: string) => {
    setRespRows((rows) => rows.filter((item) => item.key !== key));
  }, []);

  const handleUpdateExample = useCallback((key: string, field: string, value: string) => {
    setExamples((items) => items.map((item) => item.key === key ? { ...item, [field]: value } : item));
  }, []);

  const handleDeleteExample = useCallback((key: string) => {
    setExamples((items) => items.filter((item) => item.key !== key));
  }, []);

  const updateToolField = useCallback((field: ToolEditableField, value: string) => {
    setLocalTool((current) => current ? ({ ...current, [field]: value } as McpToolMapping) : current);
  }, []);

  const handleParameterEditModeChange = useCallback((_: unknown, nextMode: string | null) => {
    if (!nextMode) return;
    if (nextMode === 'json') updateToolField('parameterSchema', buildParameterSchema(paramRows));
    else setParamRows(parseParameterRows(localTool?.parameterSchema));
    setParameterEditMode(nextMode);
  }, [localTool?.parameterSchema, paramRows, updateToolField]);

  const handleResponseEditModeChange = useCallback((_: unknown, nextMode: string | null) => {
    if (!nextMode) return;
    if (nextMode === 'json') updateToolField('responseSchema', buildResponseSchema(respRows));
    else setRespRows(parseParameterRows(localTool?.responseSchema));
    setResponseEditMode(nextMode);
  }, [localTool?.responseSchema, respRows, updateToolField]);

  const handleAddParam = useCallback(() => {
    setParamRows((rows) => [...rows, { key: `new-${Date.now()}`, name: '', type: 'string', description: '', required: false }]);
  }, []);

  const handleAddResp = useCallback(() => {
    setRespRows((rows) => [...rows, { key: `new-${Date.now()}`, name: '', type: 'string', description: '', required: false }]);
  }, []);

  const handleAddExample = useCallback(() => {
    setExamples((items) => [...items, { key: `ex-${Date.now()}`, description: '', url: '', body: '{}' }]);
  }, []);

  const handleSwitchToStructuredEdit = useCallback(() => {
    setExampleIsLegacy(false);
    setExamples([{ key: `ex-${Date.now()}`, description: '', url: '', body: '{}' }]);
  }, []);

  const handleParameterSchemaChange = useCallback((value: string) => updateToolField('parameterSchema', value), [updateToolField]);
  const handleResponseSchemaChange = useCallback((value: string) => updateToolField('responseSchema', value), [updateToolField]);
  const handleLegacyExampleChange = useCallback((value: string) => updateToolField('examplePayload', value), [updateToolField]);

  const toolMethod = localTool?.httpMethod || '';
  const toolPath = localTool?.path || '';
  const parameterSchemaValue = localTool?.parameterSchema || DEFAULT_PARAMETER_SCHEMA;
  const responseSchemaValue = localTool?.responseSchema || '{}';
  const legacyExampleValue = localTool?.examplePayload || '{}';
  const isBodyMethod = ['POST', 'PUT', 'PATCH'].includes(toolMethod);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{isCreateMode ? '添加工具' : '编辑工具'}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="工具名" value={localTool?.toolName || ''} onChange={(e) => updateToolField('toolName', e.target.value)} />
        <TextField label="描述" value={localTool?.toolDescription || ''} onChange={(e) => updateToolField('toolDescription', e.target.value)} />
        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField label="HTTP 方法" value={toolMethod} onChange={(e) => updateToolField('httpMethod', e.target.value)} />
          <TextField label="路径" fullWidth value={toolPath} onChange={(e) => updateToolField('path', e.target.value)} />
        </Box>

        <ParameterSchemaSection
          editMode={parameterEditMode}
          rows={paramRows}
          jsonValue={parameterSchemaValue}
          EditorComponent={EditorComponent}
          onEditModeChange={handleParameterEditModeChange}
          onRowUpdate={handleUpdateParam}
          onRowDelete={handleDeleteParam}
          onAddRow={handleAddParam}
          onJsonChange={handleParameterSchemaChange}
        />

        <ResponseSchemaSection
          editMode={responseEditMode}
          rows={respRows}
          jsonValue={responseSchemaValue}
          EditorComponent={EditorComponent}
          onEditModeChange={handleResponseEditModeChange}
          onRowUpdate={handleUpdateResp}
          onRowDelete={handleDeleteResp}
          onAddRow={handleAddResp}
          onJsonChange={handleResponseSchemaChange}
        />

        <ExamplesSection
          exampleIsLegacy={exampleIsLegacy}
          legacyExampleValue={legacyExampleValue}
          examples={examples}
          isBodyMethod={isBodyMethod}
          httpMethod={toolMethod}
          path={toolPath}
          EditorComponent={EditorComponent}
          onAddExample={handleAddExample}
          onLegacyJsonChange={handleLegacyExampleChange}
          onSwitchToStructuredEdit={handleSwitchToStructuredEdit}
          onExampleUpdate={handleUpdateExample}
          onExampleDelete={handleDeleteExample}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={() => {
          if (!localTool) return;
          let finalExample = localTool.examplePayload || '{}';
          if (!exampleIsLegacy) {
            finalExample = buildExamplePayload(examples.map((item) => {
              let bodyParsed: unknown = null;
              try { bodyParsed = JSON.parse(item.body); } catch { bodyParsed = item.body; }
              return {
                description: item.description,
                request: { ...(isBodyMethod ? { body: bodyParsed } : { url: item.url || undefined }) },
              };
            }));
          }
          onSubmit({
            toolName: localTool.toolName,
            toolDescription: localTool.toolDescription,
            httpMethod: localTool.httpMethod,
            path: localTool.path,
            parameterSchema: parameterEditMode === 'table' ? buildParameterSchema(paramRows) : parameterSchemaValue,
            responseSchema: responseEditMode === 'table' ? buildResponseSchema(respRows) : responseSchemaValue,
            examplePayload: finalExample,
            enabled: localTool.enabled,
          });
        }} disabled={loading || !localTool}>{isCreateMode ? '创建' : '保存'}</Button>
      </DialogActions>
    </Dialog>
  );
});
