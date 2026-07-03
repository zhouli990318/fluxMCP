import { useEffect, useState, memo } from 'react';
import { Typography, Button, Drawer, Paper } from '@mui/material';
import { PlayArrow } from '@mui/icons-material';
import { useMutation } from '@tanstack/react-query';
import { fluxMcpApi } from '../../api/mcpApi';
import type { CodeEditorComponent } from './types';

export const TestToolDrawer = memo(function TestToolDrawer({
  open, toolId, EditorComponent, onClose,
}: {
  open: boolean;
  toolId: number | null;
  EditorComponent: CodeEditorComponent;
  onClose: () => void;
}) {
  const [args, setArgs] = useState('{}');
  const [result, setResult] = useState('');

  useEffect(() => {
    if (!open) return;
    setArgs('{}');
    setResult('');
  }, [open, toolId]);

  const testToolMutation = useMutation({
    mutationFn: (payload: { id: number; args: string }) => fluxMcpApi.testTool(payload.id, payload.args),
    onSuccess: (data) => setResult(typeof data === 'string' ? data : JSON.stringify(data, null, 2)),
    onError: (error: any) => setResult('Error: ' + (error.message || 'Unknown')),
  });

  return (
    <Drawer anchor="right" open={open} onClose={onClose} PaperProps={{ sx: { width: { xs: '100%', md: 420 }, p: 3 } }}>
      <Typography variant="h6" sx={{ mb: 2 }}>测试工具调用</Typography>
      <EditorComponent value={args} onChange={setArgs} height={160} />
      <Button variant="contained" startIcon={<PlayArrow />} fullWidth
        onClick={() => toolId && testToolMutation.mutate({ id: toolId, args })}
        sx={{ mt: 2 }} disabled={testToolMutation.isPending || !toolId}>
        执行
      </Button>
      {result && (
        <Paper variant="outlined" sx={{ mt: 2, p: 2, maxHeight: 300, overflow: 'auto' }}>
          <Typography sx={{ fontSize: 13, fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>{result}</Typography>
        </Paper>
      )}
    </Drawer>
  );
});
