import { memo, lazy, Suspense } from 'react';
import { Box, TextField, CircularProgress } from '@mui/material';

const LazyEditor = lazy(() => import('@monaco-editor/react'));

export const MonacoEditor = memo(function MonacoEditor({ value, onChange, height = 180 }: { value: string; onChange: (v: string) => void; height?: number }) {
  return (
    <Box sx={{ height, borderRadius: 1, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
      <Suspense fallback={<Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}><CircularProgress size={24} /></Box>}>
        <LazyEditor height="100%" defaultLanguage="json" value={value} onChange={(v: string | undefined) => onChange(v || '')} options={{ minimap: { enabled: false }, fontSize: 13, scrollBeyondLastLine: false }} />
      </Suspense>
    </Box>
  );
});

export const MobileTextarea = memo(function MobileTextarea({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return <TextField multiline fullWidth minRows={4} maxRows={10} value={value} onChange={(e) => onChange(e.target.value)} sx={{ fontFamily: 'monospace' }} />;
});
