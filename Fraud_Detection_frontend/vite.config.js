import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      },
      '/transactions': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Bypass proxy for browser navigation (HTML requests)
        // Only proxy actual API calls (JSON requests)
        bypass: (req, res, options) => {
          // If request accepts HTML (browser navigation), serve index.html instead
          if (req.headers.accept?.includes('text/html')) {
            return '/index.html';
          }
          // Otherwise (API POST request), proxy to backend
        }
      }
    }
  }
});

