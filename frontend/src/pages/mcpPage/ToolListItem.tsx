import { memo, useCallback } from 'react';
import { Box, Typography, IconButton, Switch, Chip, Stack } from '@mui/material';
import { Edit, PlayArrow } from '@mui/icons-material';
import type { McpToolMapping } from '../../api/types';
import { METHOD_COLORS } from './types';

export const ToolListItem = memo(function ToolListItem({
  tool, isLast, onToggle, onEdit, onTest,
}: {
  tool: McpToolMapping;
  isLast: boolean;
  onToggle: (id: number, enabled: boolean) => void;
  onEdit: (tool: McpToolMapping) => void;
  onTest: (tool: McpToolMapping) => void;
}) {
  const handleToggle = useCallback((_: unknown, v: boolean) => onToggle(tool.id, v), [onToggle, tool.id]);
  const handleEdit = useCallback(() => onEdit(tool), [onEdit, tool]);
  const handleTest = useCallback(() => onTest(tool), [onTest, tool]);

  return (
    <Stack direction="row" alignItems="center" spacing={1.5} sx={{
      px: 2, py: 1.5,
      borderBottom: isLast ? 'none' : '1px solid',
      borderColor: 'divider',
    }}>
      <Chip label={tool.httpMethod} size="small" sx={{
        fontFamily: 'monospace', fontWeight: 700, fontSize: 10,
        bgcolor: `${METHOD_COLORS[tool.httpMethod] || '#666'}18`,
        color: METHOD_COLORS[tool.httpMethod] || '#666',
      }} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontFamily: 'monospace', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {tool.toolName}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {tool.path}
        </Typography>
      </Box>
      <Switch size="small" checked={tool.enabled} onChange={handleToggle} />
      <IconButton size="small" onClick={handleEdit}><Edit sx={{ fontSize: 18 }} /></IconButton>
      <IconButton size="small" onClick={handleTest}><PlayArrow sx={{ fontSize: 18 }} /></IconButton>
    </Stack>
  );
});
