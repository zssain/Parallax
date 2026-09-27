import { defineConfig, devices } from '@playwright/test'

// Playwright config. The e2e/screenshot specs assume the seeded stack and `npm run dev` are running
// (Prompt 23 wires CI). Default viewport 1440px for the prototype side-by-side comparison.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    viewport: { width: 1440, height: 900 },
    ...devices['Desktop Chrome'],
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
