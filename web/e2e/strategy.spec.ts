import { test } from '@playwright/test'

const DIR = '../docs/screenshots/compare'

test('strategy, governance and lifecycle screens (Aditi)', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Strategist', exact: true }).click()
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.waitForSelector('.bento')

  const shot = async (label: string, wait: string, file: string, full = true) => {
    await page.locator('.dk span', { hasText: label }).click()
    await page.waitForSelector(wait, { timeout: 15000 })
    await page.waitForTimeout(700)
    await page.screenshot({ path: `${DIR}/${file}.png`, fullPage: full })
  }

  await shot('Lab', '.vlog', 'lab-1440')
  await shot('Drift', '.bento', 'drift-1440')
  await shot('Ledger', 'table', 'ledger-1440', false)
  await shot('Assistant', '.chat', 'assistant-1440')
  await shot('System', '.svc', 'system-1440')
  await shot('Accounts', 'table', 'accounts-1440')

  // Account detail via a row click.
  await page.locator('tr.cl').first().click()
  await page.waitForSelector('.payhist')
  await page.waitForTimeout(500)
  await page.screenshot({ path: `${DIR}/account-detail-1440.png`, fullPage: true })

  await shot('Collections', '.bento', 'collections-1440')
})
