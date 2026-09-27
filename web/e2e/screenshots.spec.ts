import { test } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

// Captures the public pages and the empty shell for the prototype side-by-side comparison. Assumes
// the seeded stack + `npm run dev` are up.
test('marketing, login and shell screenshots', async ({ page }) => {
  await page.goto('/')
  await page.waitForSelector('#stage')
  await page.waitForTimeout(400)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.screenshot({ path: `${DIR}/marketing-1440.png`, fullPage: true })
  await page.setViewportSize({ width: 1100, height: 900 })
  await page.screenshot({ path: `${DIR}/marketing-1100.png`, fullPage: true })

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.waitForSelector('#login')
  await page.screenshot({ path: `${DIR}/login-1440.png`, fullPage: true })
  await page.setViewportSize({ width: 1100, height: 900 })
  await page.screenshot({ path: `${DIR}/login-1100.png`, fullPage: true })

  // Sign in as Aditi (email prefilled by the default role) and capture the empty shell.
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/login')
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.waitForSelector('.dock')
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${DIR}/shell-overview-1440.png`, fullPage: true })
})
