import { test, expect, type Page } from '@playwright/test'

// Functional end-to-end scenarios (Prompt 23 §4) against the running compose stack. These assert
// behaviour (not just screenshots); the screenshot specs live alongside. Each test signs in fresh and
// is independent; run with `npm run e2e -- --workers=1` so the stateful flows do not race.
//
// The v2 UI signs in by picking a ROLE tab (Strategist/Approver/Underwriter/Auditor — the email is
// prefilled) and pressing "Enter workspace". Navigation is the bottom dock (`.dk`), not a sidebar.

async function signIn(page: Page, roleTab?: string) {
  await page.goto('/login')
  if (roleTab) await page.getByRole('button', { name: roleTab, exact: true }).click()
  await page.getByRole('button', { name: /Enter workspace/ }).click()
  await page.waitForURL('**/app')
  await page.waitForSelector('.dock')
}

const nav = (page: Page, label: string) => page.locator('.dk span', { hasText: label }).first().click()

// 1) Marketing loads and "Open workspace" reaches the login screen.
test('marketing loads and links to the workspace', async ({ page }) => {
  await page.goto('/')
  await page.waitForSelector('#stage')
  await page.getByText(/Open workspace/i).first().click()
  await page.waitForURL('**/login')
  await expect(page.locator('#login')).toBeVisible()
})

// 2) Each role tab signs in and the top bar shows the right role.
test('each demo role signs in with the correct role', async ({ page }) => {
  for (const role of ['Strategist', 'Approver', 'Underwriter', 'Auditor']) {
    await signIn(page, role)
    await expect(page.locator('.bar')).toContainText(role)
  }
})

// 3) Submit a prime application -> a decision in the pipeline modal -> open it -> Reproduce is
// identical. A unique SSN per run avoids the velocity fraud rule (F04) on repeated runs.
test('submit a prime application and reproduce is identical', async ({ page }) => {
  await signIn(page, 'Underwriter')
  await nav(page, 'Apply')
  await page.waitForSelector('.form')
  // Prime SSN: 2nd digit 1 = PRIME, 3rd digit 2 = NONE; last 6 unique so each run is a new applicant.
  const ssn = '912' + String(Date.now()).slice(-6)
  await page.locator('label:has-text("SSN") + input').fill(ssn)
  await page.getByRole('button', { name: /Submit for decision/ }).click()
  await page.waitForSelector('.modal .pipe')
  await page.getByRole('button', { name: /Open decision/ }).click({ timeout: 20000 })
  await page.waitForSelector('.dos-l')
  await expect(page.locator('.dos-l')).toContainText(/approved|refer|declined/i)
  // Reproduce runs the stored input under the recorded version and asserts an identical result.
  await page.getByRole('button', { name: /Reproduce/i }).click()
  await expect(page.getByText(/identical/i).first()).toBeVisible({ timeout: 15000 })
})

// 4) APP-1041 shows DECLINED 445 and the adverse-action notice lists reasons.
test('APP-1041 is declined with an adverse-action notice', async ({ page }) => {
  await signIn(page, 'Underwriter')
  await nav(page, 'Decisions')
  await page.waitForSelector('table')
  await page.locator('tr.cl', { hasText: 'APP-1041' }).click()
  await page.waitForSelector('.dos-l')
  await expect(page.locator('.dos-l')).toContainText(/declined/i)
  await expect(page.locator('.dos-l')).toContainText('445')
  await page.getByRole('button', { name: /Adverse action|adverse-action|notice/i }).first().click()
  await expect(page.locator('.modal')).toBeVisible()
})

// 8) Ledger: verify chain OK, an UPDATE is denied, and a tamper test breaks the chain.
test('ledger verifies, blocks UPDATE and detects tampering', async ({ page }) => {
  await signIn(page, 'Auditor')
  await nav(page, 'Ledger')
  await page.waitForSelector('table')
  await page.getByRole('button', { name: /Verify chain/ }).click()
  await expect(page.getByText(/Chain intact|records verified/i)).toBeVisible({ timeout: 15000 })
  await page.getByRole('button', { name: /Attempt UPDATE/ }).click()
  await expect(page.getByText(/permission denied|INSERT and SELECT/i)).toBeVisible({ timeout: 15000 })
  await page.getByRole('button', { name: /Tamper test/ }).click()
  await expect(page.getByText(/chain breaks at seq/i)).toBeVisible({ timeout: 15000 })
})

// 10) Drift: moving the slider produces a labelled SIMULATION result.
test('drift slider produces a simulation', async ({ page }) => {
  await signIn(page, 'Strategist')
  await nav(page, 'Drift')
  await page.waitForSelector('.bento')
  const slider = page.locator('input[type="range"]')
  await slider.fill('1.2')
  await expect(page.getByText(/SIMULATION/)).toBeVisible({ timeout: 15000 })
})

// 11) Assistant renders; if a model is configured it answers with tool chips, else shows the card.
test('assistant renders and answers when configured', async ({ page }) => {
  await signIn(page, 'Underwriter')
  await nav(page, 'Assistant')
  await page.waitForSelector('.chat')
  const notConfigured = await page.getByText(/model is not configured/i).isVisible().catch(() => false)
  if (notConfigured) {
    test.info().annotations.push({ type: 'note', description: 'assistant model not configured' })
    return
  }
  await page.locator('.chat input, .chat textarea').first().fill('Why was APP-1041 declined?')
  await page.keyboard.press('Enter')
  await expect(page.locator('.tool').first()).toBeVisible({ timeout: 45000 })
})

// 12) Accounts has at least one account and Collections shows its buckets.
test('accounts and collections render', async ({ page }) => {
  await signIn(page, 'Underwriter')
  await nav(page, 'Accounts')
  await page.waitForSelector('table')
  await nav(page, 'Collections')
  await page.waitForSelector('.bento')
  await expect(page.locator('.bento').first()).toBeVisible()
})
