import { saveSweepSession } from '@airness/node/a11y'
import { expect, test } from '@playwright/test'
import { ADMINISTRATOR, CREDENTIAL, signIn } from './helpers/sign-in'

// The browser sweep visits the application a second time as the administrator, who reaches every page an
// account can reach, so each page behind the sign-in form is judged by every pass the public pages are.
test('signs in as the administrator the sweep visits the application as', async ({ page }) => {
  await signIn(page, ADMINISTRATOR, CREDENTIAL)
  await expect(page.locator('form[action="/logout"]')).toBeVisible()
  await saveSweepSession(page)
})
