import { fileURLToPath, URL } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Shared code lives in ../../packages and is imported by path, so this app builds on
// its own (e.g. on Vercel with this folder as the root directory).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@booking/runtime-config": fileURLToPath(new URL("../../packages/runtime-config/src/index.ts", import.meta.url)),
      "@booking/ui": fileURLToPath(new URL("../../packages/ui", import.meta.url)),
    },
    dedupe: ["react", "react-dom"],
  },
  server: { port: 5173, fs: { allow: ["../.."] } },
});
