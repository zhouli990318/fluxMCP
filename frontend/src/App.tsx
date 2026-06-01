import { Box, Typography } from '@mui/material';
import McpPage from './pages/McpPage';

export default function App() {
  return (
    <Box sx={{ minHeight: '100vh' }}>
      <Box sx={{ px: { xs: 2, md: 3 }, pt: { xs: 2, md: 3 }, pb: 0.5 }}>
        <Typography variant="h4">Flux MCP 控制台</Typography>
      </Box>
      <McpPage />
    </Box>
  );
}
