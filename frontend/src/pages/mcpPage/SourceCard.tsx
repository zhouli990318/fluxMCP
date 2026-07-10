import { memo, useCallback } from 'react';
import { Typography, Switch, Chip, Card, CardContent, CardActions, Stack } from '@mui/material';
import { Api } from '@mui/icons-material';
import type { McpApiSource, SourceHealth } from '../../api/types';

export const SourceCard = memo(function SourceCard({
  source, selected, health, onSelect, onToggle,
}: {
  source: McpApiSource;
  selected: boolean;
  health: SourceHealth | undefined;
  onSelect: (source: McpApiSource) => void;
  onToggle: (id: number) => void;
}) {
  const status = health?.healthStatus ?? 'UNKNOWN';
  const healthLabel =
    !source.active ? '停用' :
    status === 'HEALTHY' ? '健康' :
    status === 'DEGRADED' ? '缓慢' :
    status === 'UNREACHABLE' ? '不可达' : '未知';
  const chipColor: 'success' | 'warning' | 'error' | 'default' =
    !source.active ? 'default' :
    status === 'HEALTHY' ? 'success' :
    status === 'DEGRADED' ? 'warning' :
    status === 'UNREACHABLE' ? 'error' : 'default';

  const handleClick = useCallback(() => onSelect(source), [onSelect, source]);
  const handleToggle = useCallback(() => onToggle(source.id), [onToggle, source.id]);

  return (
    <Card
      variant="outlined"
      onClick={handleClick}
      sx={{
        width: 200, cursor: 'pointer',
        borderColor: selected ? 'primary.main' : undefined,
        borderWidth: selected ? 2 : 1,
      }}
    >
      <CardContent sx={{ pb: 1 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Api color={source.active ? 'primary' : 'disabled'} />
          <Switch size="small" checked={source.active} onClick={(e) => e.stopPropagation()} onChange={handleToggle} />
        </Stack>
        <Typography fontWeight={600} sx={{ mt: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {source.name}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {source.description || '—'}
        </Typography>
      </CardContent>
      <CardActions sx={{ pt: 0 }}>
        <Chip label={healthLabel} size="small" color={chipColor} variant="outlined" />
      </CardActions>
    </Card>
  );
});
