import { svelte } from '@sveltejs/vite-plugin-svelte'
import { defineConfig } from 'vitest/config'

// Tests mount real components (tests/ecrans.test.ts), so Svelte has to resolve
// to its client build rather than the server one Vitest picks by default.
export default defineConfig({
  plugins: [svelte()],
  resolve: {
    conditions: ['browser'],
  },
  test: {
    environment: 'node',
  },
})
