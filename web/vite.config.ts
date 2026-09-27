import { defineConfig } from 'vitest/config';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: process.env.GITHUB_ACTIONS ? '/small-wins/' : '/',
  plugins: [react(), babel({ presets: [reactCompilerPreset()] }), tailwindcss()],
  server: {
    watch: { ignored: ['**/storybook-static/**', '**/playwright-report/**', '**/test-results/**'] },
  },
  test: { environment: 'node', include: ['src/**/*.test.{ts,tsx}'], restoreMocks: true },
});
