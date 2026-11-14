import { expect, type Locator, test } from '@playwright/test'

test.describe('Frontend', () => {
  test('can go on homepage', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveTitle(/Starter/)

    const heading: Locator = page.locator('h1').first()

    await expect(heading).toHaveText('Welcome to your new project.')
  })
})
