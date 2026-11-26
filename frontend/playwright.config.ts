import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 35_000,
  workers: 1,
  use: { ...devices['Desktop Chrome'], baseURL: process.env.RELEASE_TEST_URL || 'http://127.0.0.1:3000',
    channel: process.env.RELEASE_BROWSER_CHANNEL || undefined },
  webServer: process.env.RELEASE_TEST_URL ? undefined : {
    command: 'npm run dev',
    url: 'http://127.0.0.1:3000',
    reuseExistingServer: !process.env.CI,
  },
})
