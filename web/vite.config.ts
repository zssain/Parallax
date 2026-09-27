import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Dev proxy so the browser never sees CORS: assistant → :8083, accounts/collections → :8084,
// everything else under /api → application-service :8080. Targets are overridable by env vars so
// the Docker image (Prompt 23) can point at the compose service names.
const APP = process.env.VITE_APP_URL || 'http://localhost:8080'
const ASSISTANT = process.env.VITE_ASSISTANT_URL || 'http://localhost:8083'
const ACCOUNT = process.env.VITE_ACCOUNT_URL || 'http://localhost:8084'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Order matters: more specific prefixes first (Vite picks the first matching key).
      '/api/v1/assistant': { target: ASSISTANT, changeOrigin: true },
      '/api/v1/accounts': { target: ACCOUNT, changeOrigin: true },
      '/api/v1/collections': { target: ACCOUNT, changeOrigin: true },
      '/api': { target: APP, changeOrigin: true },
    },
  },
})
