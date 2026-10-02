import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';

export default defineConfig({
  plugins: [svelte()],
  build: {
    // served by Spring Boot from classpath:/static
    outDir: 'target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // dev mode: `npm run dev` on :5173, API calls go to the running backend
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
});
