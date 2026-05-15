import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    globals: true,
    clearMocks: true,
    restoreMocks: true,
  },
  server: {
    port: 3000,
    proxy: {
      // Proxy /api calls to the Spring Boot backend (avoids CORS in dev)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});

