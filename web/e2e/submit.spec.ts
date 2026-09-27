import { test, expect } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

// Submits the default Meera Joshi (near-prime) application and captures the pipeline modal driven by
// the server's real pipeline[]. Near-prime scores into REFER (SPEC §4 worked example).
test('submit drives the pipeline modal', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Underwriter', exact: true }).click()
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.locator('.dk span', { hasText: 'Apply' }).click()
  await page.waitForSelector('.form')

  await page.getByRole('button', { name: /Submit for decision/ }).click()
  await page.waitForSelector('.modal .pipe')
  // Wait for the result row (the pipeline reveal finishes and "Open decision →" appears).
  await page.getByRole('button', { name: /Open decision/ }).waitFor({ timeout: 15000 })
  await page.screenshot({ path: `${DIR}/apply-pipeline-1440.png`, fullPage: false })
  await expect(page.locator('.modal .pstep.ok').first()).toBeVisible()
})
