/// <reference types="vitest" />
import { defineConfig } from 'vite';

export default defineConfig({
  optimizeDeps: {
    exclude: [
      'ngx-markdown',
      'marked',
      '@angular/material/button',
      '@angular/material/card',
      '@angular/material/form-field',
      '@angular/material/input',
      '@angular/material/icon',
      '@angular/material/tooltip',
      '@angular/cdk'
    ]
  },
  server: {
    fs: {
      strict: false
    }
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['node_modules/', 'dist/', '**/*.spec.ts']
    }
  }
});