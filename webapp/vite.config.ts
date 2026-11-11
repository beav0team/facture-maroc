import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// The app is served under /app/ on factureapp.ma, next to the static
// marketing site that stays at the root of docs/. The build writes straight
// into docs/app/ so Cloudflare Pages serves all of docs/ with no CI step.
export default defineConfig({
  base: '/app/',
  plugins: [svelte()],
  build: {
    outDir: '../docs/app',
    emptyOutDir: true,
  },
  server: {
    port: 5174,
  },
})
