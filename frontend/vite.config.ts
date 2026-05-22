import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
// @ts-ignore
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// @ts-ignore
const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const gatewayProxyTarget = env.VITE_MCP_GATEWAY_PROXY_TARGET || 'http://localhost:8092';
  const apiProxy = {
    '/api': {
      target: gatewayProxyTarget,
      changeOrigin: true,
    },
  };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: 5174,
      proxy: apiProxy,
    },
    preview: {
      proxy: apiProxy,
    },
  };
});
