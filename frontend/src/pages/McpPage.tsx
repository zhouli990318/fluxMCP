import { useEffect, useMemo, useState, useCallback, useRef, useDeferredValue, memo } from 'react';
import {
  Box, Typography, Button, TextField, IconButton,
  InputAdornment, LinearProgress, useMediaQuery,
  Tooltip, Chip, ToggleButton, ToggleButtonGroup,
  Stack, Paper, TablePagination,
} from '@mui/material';
import { Add, Delete, Edit, ContentCopy, Refresh, Api, Search } from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fluxMcpApi } from '../api/mcpApi';
import type { CreateMcpApiSourcePayload, McpApiSource, McpToolMapping, ParseOpenApiPayload, UpdateMcpApiSourcePayload } from '../api/types';
import { useSnackbar } from 'notistack';

import type { SourceFormState, CodeEditorComponent } from './mcpPage/types';
import { buildAuthConfig } from './mcpPage/utils';
import { MonacoEditor, MobileTextarea } from './mcpPage/CodeEditor';
import { CreateEditSourceDialog } from './mcpPage/CreateEditSourceDialog';
import { EditToolDialog } from './mcpPage/EditToolDialog';
import { TestToolDrawer } from './mcpPage/TestToolDrawer';
import { SourceCard } from './mcpPage/SourceCard';
import { ToolListItem } from './mcpPage/ToolListItem';
import type { ToolUpdatePayload } from './mcpPage/types';

const OpenApiParseSection = function OpenApiParseSection({
  sourceId,
  EditorComponent,
  loading,
  onParse,
}: {
  sourceId: number;
  EditorComponent: CodeEditorComponent;
  loading: boolean;
  onParse: (payload: ParseOpenApiPayload) => void;
}) {
  const [parseMode, setParseMode] = useState<'paste' | 'url'>('paste');
  const [specContent, setSpecContent] = useState('');
  const [specUrl, setSpecUrl] = useState('');

  useEffect(() => {
    setParseMode('paste');
    setSpecContent('');
    setSpecUrl('');
  }, [sourceId]);

  return (
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
          <EditorComponent value={specContent} onChange={setSpecContent} height={180} />
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => onParse({ sourceType: 'SPEC', content: specContent })}
            disabled={loading}
            sx={{ mt: 1 }}
          >
            解析
          </Button>
        </Box>
      ) : (
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            fullWidth
            value={specUrl}
            onChange={(e) => setSpecUrl(e.target.value)}
            placeholder="https://petstore.swagger.io/v2/swagger.json"
          />
          <Button variant="outlined" onClick={() => onParse({ sourceType: 'URL', content: specUrl })} disabled={loading}>
            导入
          </Button>
        </Stack>
      )}
    </Box>
  );
};

const MemoOpenApiParseSection = memo(OpenApiParseSection);

const ToolsSection = function ToolsSection({
  sourceId,
  tools,
  onCreate,
  onToggle,
  onEdit,
  onTest,
}: {
  sourceId: number;
  tools: McpToolMapping[];
  onCreate: () => void;
  onToggle: (id: number, enabled: boolean) => void;
  onEdit: (tool: McpToolMapping) => void;
  onTest: (tool: McpToolMapping) => void;
}) {
  const [toolSearch, setToolSearch] = useState('');
  const deferredToolSearch = useDeferredValue(toolSearch);
  const [toolPage, setToolPage] = useState(0);
  const [toolRowsPerPage, setToolRowsPerPage] = useState(10);

  useEffect(() => {
    setToolSearch('');
    setToolPage(0);
    setToolRowsPerPage(10);
  }, [sourceId]);

  const filteredTools = useMemo(() => {
    const sorted = [...tools].sort((a, b) => b.id - a.id);
    if (!deferredToolSearch.trim()) return sorted;
    const query = deferredToolSearch.toLowerCase();
    return sorted.filter((tool) =>
      tool.toolName.toLowerCase().includes(query) ||
      (tool.toolDescription || '').toLowerCase().includes(query) ||
      tool.path.toLowerCase().includes(query)
    );
  }, [tools, deferredToolSearch]);

  const paginatedTools = useMemo(() => {
    const start = toolPage * toolRowsPerPage;
    return filteredTools.slice(start, start + toolRowsPerPage);
  }, [filteredTools, toolPage, toolRowsPerPage]);

  return (
    <>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography fontWeight={600}>工具 ({filteredTools.length !== tools.length ? `${filteredTools.length} / ` : ''}{tools.length})</Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          <Button size="small" variant="outlined" startIcon={<Add />} onClick={onCreate}>添加工具</Button>
          {tools.length > 0 && (
            <TextField
              size="small"
              placeholder="搜索工具名、描述、路径…"
              value={toolSearch}
              onChange={(e) => {
                setToolSearch(e.target.value);
                setToolPage(0);
              }}
              sx={{ width: 260 }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search sx={{ fontSize: 18, color: 'text.disabled' }} />
                    </InputAdornment>
                  ),
                },
              }}
            />
          )}
        </Stack>
      </Stack>
      <Paper variant="outlined">
        {filteredTools.length === 0 ? (
          <Box sx={{ py: 4, textAlign: 'center' }}>
            <Typography color="text.secondary">{tools.length === 0 ? '暂无工具，请先解析 OpenAPI 规范' : '没有匹配的工具'}</Typography>
          </Box>
        ) : (
          <>
            {paginatedTools.map((tool, index) => (
              <ToolListItem
                key={tool.id}
                tool={tool}
                isLast={index === paginatedTools.length - 1}
                onToggle={onToggle}
                onEdit={onEdit}
                onTest={onTest}
              />
            ))}
            <TablePagination
              component="div"
              count={filteredTools.length}
              page={toolPage}
              onPageChange={(_, page) => setToolPage(page)}
              rowsPerPage={toolRowsPerPage}
              onRowsPerPageChange={(e) => {
                setToolRowsPerPage(parseInt(e.target.value, 10));
                setToolPage(0);
              }}
              rowsPerPageOptions={[10, 25, 50]}
              labelRowsPerPage="每页"
              labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
              sx={{ borderTop: '1px solid', borderColor: 'divider' }}
            />
          </>
        )}
      </Paper>
    </>
  );
};

const MemoToolsSection = memo(ToolsSection);

export default function McpPage() {
  const isMobile = useMediaQuery('(max-width:899px)');
  const { enqueueSnackbar } = useSnackbar();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [selectedSource, setSelectedSource] = useState<McpApiSource | null>(null);
  const [testTool, setTestTool] = useState<McpToolMapping | null>(null);
  const [editingSource, setEditingSource] = useState<McpApiSource | null>(null);
  const [editingTool, setEditingTool] = useState<McpToolMapping | null>(null);
  const [creatingTool, setCreatingTool] = useState(false);

  // ── Queries ──
  const { data: sources = [], isLoading } = useQuery({ queryKey: ['mcp-sources'], queryFn: fluxMcpApi.listSources });
  const { data: connectionInfo } = useQuery({
    queryKey: ['mcp-source-connection-info', selectedSource?.id],
    queryFn: () => fluxMcpApi.getSourceConnectionInfo(selectedSource!.id),
    enabled: !!selectedSource,
  });
  const { data: tools = [], refetch: refetchTools } = useQuery({
    queryKey: ['mcp-tools', selectedSource?.id],
    queryFn: () => fluxMcpApi.getTools(selectedSource!.id),
    enabled: !!selectedSource,
  });
  const { data: sourceHealth = [] } = useQuery({
    queryKey: ['mcp-health'],
    queryFn: fluxMcpApi.listSourcesHealth,
    refetchInterval: 30000,
  });

  const healthMap = useMemo(() => new Map(sourceHealth.map((h) => [h.id, h])), [sourceHealth]);

  // ── Mutations ──
  const createMutation = useMutation({
    mutationFn: (data: CreateMcpApiSourcePayload) => fluxMcpApi.createSource(data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mcp-sources'] }); setCreateOpen(false); setEditingSource(null); enqueueSnackbar('创建成功', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '创建失败', { variant: 'error' }),
  });
  const updateSourceMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateMcpApiSourcePayload }) => fluxMcpApi.updateSource(id, data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mcp-sources'] }); setCreateOpen(false); setEditingSource(null); enqueueSnackbar('更新成功', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '更新失败', { variant: 'error' }),
  });
  const deleteMutation = useMutation({
    mutationFn: fluxMcpApi.deleteSource,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mcp-sources'] }); setSelectedSource(null); enqueueSnackbar('删除成功', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '删除失败', { variant: 'error' }),
  });
  const parseMutation = useMutation({
    mutationFn: (data: ParseOpenApiPayload) => fluxMcpApi.parseSpec(selectedSource!.id, data),
    onSuccess: () => { refetchTools(); enqueueSnackbar('解析完成', { variant: 'success' }); },
    onError: () => enqueueSnackbar('解析失败', { variant: 'error' }),
  });
  const toggleToolMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) => fluxMcpApi.updateTool(id, { enabled }),
    onSuccess: () => { refetchTools(); enqueueSnackbar('工具状态已更新', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '操作失败', { variant: 'error' }),
  });
  const updateToolMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Record<string, unknown> }) => fluxMcpApi.updateTool(id, data),
    onSuccess: () => { refetchTools(); setEditingTool(null); enqueueSnackbar('已更新', { variant: 'success' }); },
    onError: () => enqueueSnackbar('更新失败', { variant: 'error' }),
  });
  const createToolMutation = useMutation({
    mutationFn: ({ sourceId, data }: { sourceId: number; data: Record<string, unknown> }) => fluxMcpApi.createTool(sourceId, data),
    onSuccess: () => { refetchTools(); setCreatingTool(false); enqueueSnackbar('工具已创建', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '创建工具失败', { variant: 'error' }),
  });
  const toggleSourceMutation = useMutation({
    mutationFn: fluxMcpApi.toggleSourceActive,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mcp-sources'] }); queryClient.invalidateQueries({ queryKey: ['mcp-health'] }); enqueueSnackbar('源状态已切换', { variant: 'success' }); },
    onError: (e: any) => enqueueSnackbar(e?.response?.data?.message || '操作失败', { variant: 'error' }),
  });

  // ── Stable callback refs ──
  const createMutateRef = useRef(createMutation.mutate);
  createMutateRef.current = createMutation.mutate;
  const updateSourceMutateRef = useRef(updateSourceMutation.mutate);
  updateSourceMutateRef.current = updateSourceMutation.mutate;
  const updateToolMutateRef = useRef(updateToolMutation.mutate);
  updateToolMutateRef.current = updateToolMutation.mutate;
  const createToolMutateRef = useRef(createToolMutation.mutate);
  createToolMutateRef.current = createToolMutation.mutate;
  const parseMutateRef = useRef(parseMutation.mutate);
  parseMutateRef.current = parseMutation.mutate;

  // ── Handlers ──
  const openCreateDialog = useCallback(() => { setEditingSource(null); setCreateOpen(true); }, []);
  const openEditSourceDialog = useCallback((source: McpApiSource) => { setEditingSource(source); setCreateOpen(true); }, []);

  const handleCloseCreateDialog = useCallback(() => { setCreateOpen(false); setEditingSource(null); }, []);
  const handleSourceSubmit = useCallback((payload: SourceFormState) => {
    const shouldPreserveExistingAuthConfig = Boolean(
      editingSource && payload.authType !== 'NONE' && payload.authType === editingSource.authType && !payload.updateAuthConfig,
    );
    const normalizedPayload = {
      name: payload.name.trim(), description: payload.description.trim(),
      baseUrl: payload.baseUrl.trim(), authType: payload.authType,
      authConfig: shouldPreserveExistingAuthConfig || payload.authType === 'NONE' ? '' : buildAuthConfig(payload.authType, payload.authFields),
    };
    if (editingSource) { updateSourceMutateRef.current({ id: editingSource.id, data: normalizedPayload }); return; }
    createMutateRef.current(normalizedPayload);
  }, [editingSource]);

  const handleOpenCreateTool = useCallback(() => { setEditingTool(null); setCreatingTool(true); }, []);
  const handleCloseEditTool = useCallback(() => { setEditingTool(null); setCreatingTool(false); }, []);
  const handleToolSubmit = useCallback((payload: ToolUpdatePayload) => {
    if (editingTool) {
      updateToolMutateRef.current({ id: editingTool.id, data: payload });
      return;
    }
    if (selectedSource) {
      createToolMutateRef.current({ sourceId: selectedSource.id, data: payload });
    }
  }, [editingTool, selectedSource]);

  const handleCloseTestTool = useCallback(() => { setTestTool(null); }, []);

  const handleSelectSource = useCallback((source: McpApiSource) => setSelectedSource(source), []);
  const handleToggleSource = useCallback((id: number) => toggleSourceMutation.mutate(id), [toggleSourceMutation]);
  const handleToggleTool = useCallback((id: number, enabled: boolean) => toggleToolMutation.mutate({ id, enabled }), [toggleToolMutation]);
  const handleEditTool = useCallback((tool: McpToolMapping) => setEditingTool(tool), []);
  const handleTestTool = useCallback((tool: McpToolMapping) => setTestTool(tool), []);
  const handleParseSpec = useCallback((payload: ParseOpenApiPayload) => parseMutateRef.current(payload), []);

  const copyText = useCallback(async (value: string, label: string) => {
    try { await navigator.clipboard.writeText(value); enqueueSnackbar(`${label} 已复制`, { variant: 'success' }); }
    catch { enqueueSnackbar('复制失败', { variant: 'error' }); }
  }, [enqueueSnackbar]);

  const CodeEditor = useMemo<CodeEditorComponent>(() => isMobile ? MobileTextarea : MonacoEditor, [isMobile]);

  // ── Render ──
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
          {sources.map((s: McpApiSource) => (
            <SourceCard key={s.id} source={s} selected={selectedSource?.id === s.id}
              health={healthMap.get(s.id)} onSelect={handleSelectSource} onToggle={handleToggleSource} />
          ))}
        </Stack>
      )}

      {/* Selected source detail */}
      {selectedSource && (
        <Paper variant="outlined" sx={{ p: 3 }}>
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
              {[{ label: 'HTTP', value: connectionInfo.streamableHttpUrl }].map((item) => (
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
          <MemoOpenApiParseSection
            sourceId={selectedSource.id}
            EditorComponent={CodeEditor}
            loading={parseMutation.isPending}
            onParse={handleParseSpec}
          />

          {/* Tools list */}
          <MemoToolsSection
            sourceId={selectedSource.id}
            tools={tools}
            onCreate={handleOpenCreateTool}
            onToggle={handleToggleTool}
            onEdit={handleEditTool}
            onTest={handleTestTool}
          />
        </Paper>
      )}

      <CreateEditSourceDialog open={createOpen} initialSource={editingSource}
        loading={createMutation.isPending || updateSourceMutation.isPending}
        onClose={handleCloseCreateDialog} onSubmit={handleSourceSubmit} />

      <EditToolDialog open={editingTool !== null || creatingTool} tool={editingTool} EditorComponent={CodeEditor}
        loading={updateToolMutation.isPending || createToolMutation.isPending} onClose={handleCloseEditTool} onSubmit={handleToolSubmit} />

      <TestToolDrawer open={testTool !== null} toolId={testTool?.id || null}
        EditorComponent={CodeEditor} onClose={handleCloseTestTool} />
    </Box>
  );
}
