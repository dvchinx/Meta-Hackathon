import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // The repo-level .env sets BACKEND_PORT for local dev.
  const env = loadEnv(mode, '..', '')
  const backend = `http://localhost:${env.BACKEND_PORT || '8080'}`
  return {
    plugins: [react()],
    worker: { format: 'es' },
    // Same paths as production, where Nginx routes them to the backend.
    server: { proxy: { '/api': backend, '/media': backend } },
    preview: { proxy: { '/api': backend, '/media': backend } },
    test: { environment: 'jsdom' },
  }
})
