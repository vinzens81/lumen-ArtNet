import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { execSync } from 'node:child_process';

/** Same as the Mac app (package-mac.sh): 1.0.<number of commits>. */
function lumenVersion(): string {
  try {
    return `1.0.${execSync('git rev-list --count HEAD', { encoding: 'utf8' }).trim()}`;
  } catch {
    return 'dev';
  }
}

export default defineConfig({
  plugins: [svelte()],
  define: {
    __LUMEN_VERSION__: JSON.stringify(lumenVersion()),
  },
  build: {
    // served by Spring Boot from classpath:/static
    outDir: 'target/classes/static',
    emptyOutDir: true,
  },
  server: {
    // dev mode: `npm run dev` on :5173, API calls go to the running backend
    // CHANGELOG.md lies in the project root, one level up
    fs: { allow: ['..'] },
    proxy: {
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
});
