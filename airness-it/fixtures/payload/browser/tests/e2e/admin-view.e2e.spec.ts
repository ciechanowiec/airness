import { expect, test, type APIResponse } from '@playwright/test'
import { expectSweptPage } from '@airness/node/a11y'

test('scans a real custom admin view and reports defects only in its own content', async ({ page }) => {
  const response: APIResponse<unknown> = await page.request.post('/api/users/login', {
    data: { email: 'browser@example.invalid', password: 'fixture-browser-password' },
  })
  expect(response.ok()).toBe(true)
  await page.goto('/admin/proof')
  await expect(page.getByRole('heading', { name: 'Owned view' })).toBeVisible()
  await page.addStyleTag({ content: '.app-header { font-size: 5px !important; }' })
  await expectSweptPage(page)
  await page.addStyleTag({ content: '#owned-copy { font-size: 5px !important; }' })
  await expect(expectSweptPage(page)).rejects.toThrow('small-text')
})
