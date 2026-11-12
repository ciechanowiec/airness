import { expect, type Page } from '@playwright/test'

/** The administrator the test wrapper writes on the run's own database. */
export const ADMINISTRATOR: string = process.env['APP_ADMINISTRATOR_EMAIL'] ?? ''

/** The administrator's password, which the test wrapper chooses for the run. */
export const CREDENTIAL: string = process.env['APP_ADMINISTRATOR_PASSWORD'] ?? ''

/**
 * Sign in through the sign-in form, and wait until the page offers to sign out.
 * @param page the page to sign in on.
 * @param email the account's address.
 * @param secret the account's password.
 */
export const signIn = async (page: Page, email: string, secret: string): Promise<void> => {
  await page.goto('/login')
  await page.locator('input[name="username"]').fill(email)
  await page.locator('input[name="password"]').fill(secret)
  await page.locator('#sign-in').click()
  await expect(page.locator('form[action="/logout"]')).toBeVisible()
}
