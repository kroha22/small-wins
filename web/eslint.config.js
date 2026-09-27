import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import hooks from 'eslint-plugin-react-hooks';
import refresh from 'eslint-plugin-react-refresh';
export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'storybook-static/**',
      'playwright-report/**',
      'test-results/**',
      'node_modules/**',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { 'react-hooks': hooks, 'react-refresh': refresh },
    rules: {
      ...hooks.configs['recommended-latest'].rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    files: ['src/games/**/model.ts', 'src/shared/lib/*.ts'],
    rules: {
      'no-restricted-globals': ['error', 'window', 'document', 'localStorage', 'Worker', 'Date'],
      'no-restricted-imports': [
        'error',
        { patterns: ['react', 'react-dom', 'zustand', '*/Game', '*/store'] },
      ],
    },
  },
);
