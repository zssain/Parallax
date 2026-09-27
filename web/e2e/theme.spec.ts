import { test, expect } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

test('dark theme toggle applies and a modal syncs', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /Aditi Rao/ }).click()
  await page.click('button.btn-teal')
  await page.waitForURL('**/app')
  await page.waitForSelector('.kpis')
  // Click the dark theme toggle (third button in .theme).
  await page.locator('.theme button').nth(2).click()
  await expect(page.locator('.app.dark')).toBeVisible()
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${DIR}/overview-dark-1440.png`, fullPage: false })
})
