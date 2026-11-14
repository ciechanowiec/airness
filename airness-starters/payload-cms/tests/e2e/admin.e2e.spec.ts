import { type BrowserContext, expect, type Locator, type Page, test } from '@playwright/test'
import { login } from '../helpers/login'
import { seedTestAdmin, testAdmin } from '../helpers/test-admin'

test.describe('Admin Panel', () => {
  // Playwright creates the logged-in page in beforeAll and every test in the block shares it. There is
  // no per-test fixture that can carry a session established once for the whole block.
  // eslint-disable-next-line functional/no-let -- one session shared by the block, set in beforeAll
  let page: Page

  test.beforeAll(async ({ browser }, testInfo) => {
    await seedTestAdmin()

    // A context opened by hand does not inherit the config's `baseURL`, so it is threaded through
    // explicitly: every navigation below is relative to the origin the harness declares.
    const baseURL: string | undefined = testInfo.project.use.baseURL
    if (baseURL === undefined) {
      throw new Error('the harness Playwright config must declare a server URL')
    }
    const context: BrowserContext = await browser.newContext({ baseURL })
    page = await context.newPage()

    await login({ page, user: testAdmin })
  })

  test('can navigate to dashboard', async () => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/admin$/)
    const dashboardArtifact: Locator = page.locator('span[title="Dashboard"]').first()
    await expect(dashboardArtifact).toBeVisible({ timeout: 30_000 })
  })

  test('can navigate to list view', async () => {
    await page.goto('/admin/collections/users')
    // Payload appends list preferences (?depth=1&limit=10); match the path, not the query string.
    await expect(page).toHaveURL(/\/admin\/collections\/users(\?.*)?$/)
    const listViewArtifact: Locator = page.locator('h1', { hasText: 'Users' }).first()
    await expect(listViewArtifact).toBeVisible({ timeout: 30_000 })
  })

  test('can navigate to edit view', async () => {
    await page.goto('/admin/collections/users/create')
    await expect(page).toHaveURL(/\/admin\/collections\/users\/[\w-]+/)
    const editViewArtifact: Locator = page.locator('input[name="email"]')
    await expect(editViewArtifact).toBeVisible({ timeout: 30_000 })
  })
})
