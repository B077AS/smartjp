import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// In development the page and the API share one address (localhost:5173): Vite forwards /api to the application running
// in website mode (port 8081, `java -jar smartjp.jar`), which owns the database. That is the same situation as production
// (the application serves the built files and /api from one address), so no CORS. strictPort: never silently pick another port.
// SMARTJP_PORT and SMARTJP_BACKEND exist only to run a second copy next to the first (tests); normal use needs neither.
// "npm run build" writes the app straight into the application's resources (git-ignored): Maven copies
// that folder into target/classes on every build, so the application always serves the last build and there is no second copy to go stale.
export default defineConfig({
  plugins: [vue()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: {
    port: Number(process.env.SMARTJP_PORT ?? 5173),
    strictPort: true,
    proxy: {
      '/api': process.env.SMARTJP_BACKEND ?? 'http://localhost:8081',
    },
  },
})
