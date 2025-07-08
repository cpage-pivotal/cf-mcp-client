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
  }
});