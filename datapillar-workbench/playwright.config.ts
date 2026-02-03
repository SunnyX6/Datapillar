import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL

if (!baseURL) {
  throw new Error('请设置 PLAYWRIGHT_BASE_URL，例如 http://localhost:5173')
}

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry'
  },
  projects: [
    {
      name: 'e2e-chromium',
      testDir: './tests/e2e',
      testMatch: '**/*.e2e.ts',
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'perf-chromium',
      testDir: './tests/perf',
      testMatch: '**/*.perf.ts',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
