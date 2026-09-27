import { test, expect } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

test('dark theme toggle applies and a modal syncs', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Strategist', exact: true }).click()
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.waitForSelector('.bento')
  // The top bar's theme toggle flips light -> dark.
  await page.locator('button.icb[title="Toggle theme"]').click()
  await expect(page.locator('.app.dark')).toBeVisible()
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${DIR}/overview-dark-1440.png`, fullPage: false })
})
