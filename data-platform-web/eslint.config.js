import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import vue from 'eslint-plugin-vue'

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'coverage/**',
      'src/auto-imports.d.ts',
      'src/components.d.ts'
    ]
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.{ts,tsx,vue}'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        ecmaVersion: 'latest',
        sourceType: 'module'
      }
    },
    rules: {
      'no-console': 'off',
      'no-debugger': 'error',
      'no-empty': ['error', { allowEmptyCatch: true }],
      'no-var': 'error',
      'prefer-const': 'error',
      'eqeqeq': ['error', 'always', { null: 'ignore' }],
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          caughtErrors: 'none',
          varsIgnorePattern: '^_'
        }
      ],
      'vue/multi-word-component-names': 'off',
      'vue/require-default-prop': 'off'
    }
  },
  {
    files: ['src/**/*.{js,jsx,ts,tsx,vue}'],
    languageOptions: {
      globals: globals.browser
    }
  },
  {
    files: ['*.config.{js,mjs,cjs,ts}', 'vite.config.ts'],
    languageOptions: {
      globals: globals.node
    }
  }
)
