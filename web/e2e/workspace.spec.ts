import { test } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

// Auth is in-memory (a full reload returns to /login), so navigate via in-app clicks only.
test('workspace screens (signed in as Priya)', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Underwriter', exact: true }).click()
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.waitForSelector('.bento')
  await page.waitForTimeout(700)
  await page.screenshot({ path: `${DIR}/overview-1440.png`, fullPage: true })

  await page.locator('.dk span', { hasText: 'Decisions' }).click()
  await page.waitForSelector('table')
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${DIR}/decisions-1440.png`, fullPage: true })

  await page.locator('tr.cl', { hasText: 'APP-1041' }).click()
  await page.waitForSelector('.dos-l')
  await page.waitForTimeout(600)
  await page.screenshot({ path: `${DIR}/decision-detail-1440.png`, fullPage: true })

  await page.locator('.dk span', { hasText: 'Queue' }).click()
  await page.waitForSelector('.qsel, .empty')
  await page.waitForTimeout(500)
  // Queue can hold ~1900 SEED refers, so capture the viewport (not the full page).
  await page.screenshot({ path: `${DIR}/queue-1440.png`, fullPage: false })

  await page.locator('.dk span', { hasText: 'Apply' }).click()
  await page.waitForSelector('.form')
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${DIR}/apply-1440.png`, fullPage: true })
})
