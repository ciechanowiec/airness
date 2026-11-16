import { expectSweptPage } from '@airness/node/a11y'
import { type BrowserContext, expect, type Page, type Response, test } from '@playwright/test'

import type { Payload } from 'payload'

import type { Category, Media, Post } from '@/payload-types'
import { createCategory, createPhotoCover, createPost, uniqueSlug } from '../helpers/content'
import { login } from '../helpers/login'
import { payloadInstance } from '../helpers/payload'
import { seedTestAdmin, testAdmin } from '../helpers/test-admin'

const NOT_FOUND: number = 404

const DRAFT_TITLE: string = 'An unpublished draft'

const draftPost = async (): Promise<Post> => {
  const [category, cover]: [Category, Media] = await Promise.all([createCategory('preview'), createPhotoCover()])
  return await createPost({ category, cover, status: 'draft', title: DRAFT_TITLE, slug: uniqueSlug('draft') })
}

// A context opened by hand does not inherit the config's `baseURL`, so it is threaded through explicitly.
const signedInPage = async (context: BrowserContext): Promise<Page> => {
  const page: Page = await context.newPage()
  await seedTestAdmin()
  await login({ page, user: testAdmin })
  return page
}

test.describe('Draft preview', () => {
  test('shows a signed-in editor the newest draft, marked as a preview', async ({ browser }, testInfo) => {
    const draft: Post = await draftPost()
    const context: BrowserContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL ?? '' })
    const page: Page = await signedInPage(context)

    await page.goto(`/preview/posts/${draft.slug}`)

    await expect(page.getByRole('heading', { level: 1 })).toHaveText(DRAFT_TITLE)
    await expect(page.getByRole('status')).toContainText('preview of the newest draft')
    await expectSweptPage(page)
  })

  // The admin panel's Live Preview saves the draft and posts a document event to the frame; the frame then renders
  // the page again with the saved draft, without navigating.
  test('shows the newly saved draft when the admin panel reports a save', async ({ browser }, testInfo) => {
    const draft: Post = await draftPost()
    const context: BrowserContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL ?? '' })
    const page: Page = await signedInPage(context)
    await page.goto(`/preview/posts/${draft.slug}`)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(DRAFT_TITLE)

    const payload: Payload = await payloadInstance
    await payload.update({ collection: 'posts', id: draft.id, draft: true, data: { title: 'A retitled draft' } })
    await page.evaluate("window.postMessage({ type: 'payload-document-event' }, window.location.origin)")

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('A retitled draft')
  })

  test('answers an anonymous visitor as if the draft did not exist', async ({ page }) => {
    const draft: Post = await draftPost()

    const response: Response | null = await page.goto(`/preview/posts/${draft.slug}`)

    expect(response?.status()).toBe(NOT_FOUND)
    await expect(page.getByText(DRAFT_TITLE)).toHaveCount(0)
  })

  test('keeps the draft off its public address', async ({ page }) => {
    const draft: Post = await draftPost()

    const response: Response | null = await page.goto(`/posts/${draft.slug}`)

    expect(response?.status()).toBe(NOT_FOUND)
  })
})
