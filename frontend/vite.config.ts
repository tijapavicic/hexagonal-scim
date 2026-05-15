import { defineConfig } from 'vite';

// https://vitejs.dev/config/
export default defineConfig({
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

