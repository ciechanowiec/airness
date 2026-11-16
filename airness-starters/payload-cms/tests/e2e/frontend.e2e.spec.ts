import { expect, type Locator, type Response, test } from '@playwright/test'

// The run's database holds the demo content `scripts/seed-content.ts` seeds, which every page below reads.
const FEATURED_POST: string = '/posts/why-the-cinema-still-feels-like-magic'
const FEATURED_TITLE: string = 'Why the cinema still feels like magic'
const NOT_FOUND: number = 404

test.describe('Frontend', () => {
  test('leads the home page with the tagline and the featured post', async ({ page }) => {
    await page.goto('/')

    await expect(page).toHaveTitle(/Starter/u)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Films worth watching, and the reasons why.')
    await expect(page.getByRole('heading', { level: 2 }).first()).toHaveText(FEATURED_TITLE)
  })

  test('opens a post from its card', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: FEATURED_TITLE }).click()

    await expect(page).toHaveURL(FEATURED_POST)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(FEATURED_TITLE)
    await expect(page.getByRole('heading', { name: 'Watching with strangers' })).toBeVisible()
  })

  test('lists the posts of a category and marks it as the current page', async ({ page }) => {
    await page.goto('/categories/history')

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('History')
    const current: Locator = page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'History' })
    await expect(current).toHaveAttribute('aria-current', 'page')
    await expect(page.getByRole('article')).toHaveCount(2)
  })

  test('pages through every post', async ({ page }) => {
    await page.goto('/posts')
    await page.getByRole('link', { name: 'Next page' }).click()

    await expect(page).toHaveURL('/posts?page=2')
    await expect(page.getByText('Page 2', { exact: true })).toHaveAttribute('aria-current', 'page')
  })

  test('answers an unknown post with the not-found page', async ({ page }) => {
    const response: Response | null = await page.goto('/posts/no-such-post')

    expect(response?.status()).toBe(NOT_FOUND)
    await expect(page.getByRole('link', { name: 'Go to the home page' })).toBeVisible()
  })

  test('answers a page beyond the last with the not-found page', async ({ page }) => {
    const response: Response | null = await page.goto('/posts?page=99')

    expect(response?.status()).toBe(NOT_FOUND)
  })
})
