import { randomUUID } from 'node:crypto'
import { expectSweptPage } from '@airness/node/a11y'
import { type APIResponse, expect, type Locator, type Page, test } from '@playwright/test'
import { ADMINISTRATOR, CREDENTIAL, signIn } from './helpers/sign-in'

// The sweep visits every page the administrator reaches, signed in through `sign-in.setup.ts`. What it cannot
// reach is scanned here, with the same accessibility and layout checks: the pages as a user sees them, a task
// that only exists once this run writes it, the delete dialog in its open state, and the sign-in page in
// Polish. The user and the task are admitted through the pages themselves, on a database of the run's own.

const open = async (page: Page, address: string): Promise<Locator> => {
  await page.goto(address)
  return page.locator('main')
}

// A user of this run's own, admitted by the administrator and signed in with the password chosen here. The
// name is the run's own too, because the list of users links each name to its account, and two links of one
// name that lead to different accounts fail the accessibility scan.
const admitUser = async (page: Page): Promise<{ readonly email: string; readonly secret: string }> => {
  const reference: string = randomUUID()
  const email: string = `reader-${reference}@starter.test`
  const secret: string = `Reader-${randomUUID()}`
  await signIn(page, ADMINISTRATOR, CREDENTIAL)
  await page.goto('/users/new')
  await page.locator('input[name="profile.fullName"]').fill(`Sweep reader ${reference}`)
  await page.locator('input[name="profile.email"]').fill(email)
  await page.locator('select[name="role"]').selectOption('USER')
  await page.locator('input[name="password"]').fill(secret)
  await page.locator('main form button[type="submit"]').click()
  await expect(page).toHaveURL(/\/users\?notice=CREATED$/u)
  await page.context().clearCookies()
  return { email, secret }
}

const writeTask = async (page: Page, title: string): Promise<void> => {
  await page.goto('/tasks/new')
  await page.locator('input[name="title"]').fill(title)
  await page.locator('main form button[type="submit"]').click()
  await expect(page).toHaveURL(/\/tasks\?notice=CREATED$/u)
}

test('the pages a user works in pass the scans', async ({ page }) => {
  const { email, secret } = await admitUser(page)
  await signIn(page, email, secret)
  await writeTask(page, `Sweep task ${randomUUID()}`)
  for (const address of ['/', '/tasks', '/account']) {
    await expect(await open(page, address)).toBeVisible()
    await expectSweptPage(page)
  }
  await page.goto('/tasks')
  const edit: string | null = await page.locator('a[href^="/tasks/"][href$="/edit"]').first().getAttribute('href')
  await expect(await open(page, edit ?? '')).toBeVisible()
  await expectSweptPage(page)
  // The sweep opens no dialog that needs a task of the run's own, so the keyboard pattern of the dialog is held
  // here: the keyboard opens it with focus inside, and Escape closes it and hands focus back to its button.
  const opener: Locator = page.locator('button[data-opens="delete-task"]')
  const dialog: Locator = page.locator('#delete-task')
  await opener.focus()
  await page.keyboard.press('Enter')
  await expect(dialog).toBeVisible()
  await expect(dialog.locator(':focus')).toHaveCount(1)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(opener).toBeFocused()
  await opener.click()
  await expect(dialog).toBeVisible()
  await expectSweptPage(page)
})

test.describe('a browser asking for Polish', () => {
  test.use({ locale: 'pl-PL' })

  test('the sign-in page in Polish passes the scans', async ({ page }) => {
    await expect(await open(page, '/login')).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', 'pl')
    await expectSweptPage(page)
  })
})

// The build writes the stylesheet with the Tailwind command line its script runs, and the page links it under
// the versioned address Spring hands out, so this reads the stylesheet the reader's browser receives.
test('a page links the stylesheet Tailwind and daisyUI built', async ({ page }) => {
  await page.goto('/login')
  const address: string | null = await page.locator('link[rel="stylesheet"]').getAttribute('href')
  const response: APIResponse = await page.request.get(address ?? '')
  const stylesheet: string = await response.text()
  expect(stylesheet).toMatch(/tailwindcss v4/u)
  expect(stylesheet).toMatch(/\.btn\b/u)
})
