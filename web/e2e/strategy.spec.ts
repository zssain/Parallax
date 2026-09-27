import { test } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

test('strategy, governance and lifecycle screens (Aditi)', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /Aditi Rao/ }).click()
  await page.click('button.btn-teal')
  await page.waitForURL('**/app')
  await page.waitForSelector('.kpis')

  const shot = async (nav: string, wait: string, file: string, full = true) => {
    await page.locator('.navi span', { hasText: nav }).click()
    await page.waitForSelector(wait, { timeout: 15000 })
    await page.waitForTimeout(700)
    await page.screenshot({ path: `${DIR}/${file}.png`, fullPage: full })
  }

  await shot('Strategy Lab', '.vcards', 'lab-1440')
  await shot('Drift monitor', '.kpis', 'drift-1440')
  await shot('Decision ledger', 'table', 'ledger-1440', false)
  await shot('Assistant', '.chat', 'assistant-1440')
  await shot('System', '.svc', 'system-1440')
  await shot('Accounts', '.kpis', 'accounts-1440')

  // Account detail via a row click.
  await page.locator('tr.cl').first().click()
  await page.waitForSelector('.payhist')
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${DIR}/account-detail-1440.png`, fullPage: true })

  await shot('Collections', '.kpis', 'collections-1440')
})
