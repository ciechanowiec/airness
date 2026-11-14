import type { Page } from '@playwright/test'
import { expect, type Locator } from '@playwright/test'
import type { Credentials } from './accounts'

export interface LoginOptions {
  page: Page
  user: Credentials
}

/**
 * Logs the user into the admin panel via the login page.
 *
 * Navigation is relative, so the page's browser context must carry the `baseURL` the Playwright
 * config declares: the origin is the harness's to decide (`airness.serverUrl`), not this helper's.
 */
export async function login({ page, user }: LoginOptions): Promise<void> {
  await page.goto('/admin/login')

  await page.fill('#field-email', user.email)
  await page.fill('#field-password', user.password)
  await page.click('button[type="submit"]')

  await page.waitForURL((url: URL): boolean => url.pathname === '/admin')

  const dashboardArtifact: Locator = page.locator('span[title="Dashboard"]')
  await expect(dashboardArtifact).toBeVisible()
}
