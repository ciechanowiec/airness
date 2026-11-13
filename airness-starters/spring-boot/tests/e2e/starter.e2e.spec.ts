import { randomUUID } from 'node:crypto'
import { expectSweptPage } from '@airness/node/a11y'
import { type APIResponse, expect, type JSHandle, type Locator, type Page, test } from '@playwright/test'
import { ADMINISTRATOR, CREDENTIAL, signIn } from './helpers/sign-in'

// The sweep visits every page the administrator reaches, signed in through `sign-in.setup.ts`. What it cannot
// reach is scanned here, with the same accessibility and layout checks: the pages as a user sees them, a task
// that only exists once this run writes it, the delete dialog in its open state, and the sign-in page in
// Polish. The user and the task are admitted through the pages themselves, on a database of the run's own.

const open = async (page: Page, address: string): Promise<Locator> => {
  await page.goto(address)
  return page.locator('main')
}

// A user of this run's own, admitted by the administrator and signed in with the password chosen here.
const admitUser = async (page: Page): Promise<{ readonly email: string; readonly secret: string }> => {
  const email: string = `reader-${randomUUID()}@starter.test`
  const secret: string = `Reader-${randomUUID()}`
  await signIn(page, ADMINISTRATOR, CREDENTIAL)
  await page.goto('/users/new')
  await page.locator('input[name="profile.fullName"]').fill('Sweep reader')
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
  for (const address of ['/', '/tasks', '/tasks/board', '/account']) {
    await expect(await open(page, address)).toBeVisible()
    await expectSweptPage(page)
  }
  await page.goto('/tasks')
  const edit: string | null = await page.locator('a[href^="/tasks/"][href$="/edit"]').first().getAttribute('href')
  await expect(await open(page, edit ?? '')).toBeVisible()
  await expectSweptPage(page)
  await page.locator('button[data-opens="delete-task"]').click()
  await expect(page.locator('#delete-task')).toBeVisible()
  await expectSweptPage(page)
})

// The language switch loads the whole page, so the page declares the language it is now written in and a
// screen reader pronounces it in that language.
test('switching the language makes the page declare it', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: 'Polski', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'pl')
  await page.getByRole('button', { name: 'English', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
})

test.describe('a browser asking for Polish', () => {
  test.use({ locale: 'pl-PL' })

  test('the sign-in page in Polish passes the scans', async ({ page }) => {
    await expect(await open(page, '/login')).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('lang', 'pl')
    await expectSweptPage(page)
  })
})

// A browser draws what the pointer carries from the panel a card sits in, and the preview's panel stays put beside
// the page, which some browsers draw whole. The preview hands the browser a copy of the card alone instead, gone
// once drawn, and the card itself still lands in the column it is dropped on.
test('the preview carries a card alone and lands it where it is dropped', async ({ page }) => {
  await page.goto('/login')
  const card: Locator = page.locator('.preview [data-preview-column] .preview-card').first()
  const title: string = (await card.locator('span').first().textContent()) ?? ''
  // What the pointer carries exists only while the drag starts, so the page answers with it then, through a
  // listener in place before the drag begins.
  const carried: JSHandle<{ readonly text: Promise<string> }> = await page
    .locator('body')
    .evaluateHandle((body: HTMLElement): { readonly text: Promise<string> } => ({
      text: new Promise<string>((resolve: (text: string) => void): void => {
        body.ownerDocument.addEventListener(
          'dragstart',
          (): void => {
            resolve(body.querySelector(':scope > .preview-carried')?.textContent ?? '')
          },
          { once: true },
        )
      }),
    }))
  await card.dispatchEvent('dragstart', { dataTransfer: await page.evaluateHandle(() => new DataTransfer()) })
  expect(
    await carried.evaluate(async (record: { readonly text: Promise<string> }): Promise<string> => record.text),
  ).toContain(title)
  await expect(page.locator('.preview-carried')).toHaveCount(0)
  await card.dispatchEvent('dragend')
  const done: Locator = page.locator('.preview [data-preview-column]').nth(2)
  await card.dragTo(done)
  await expect(done).toContainText(title)
  await expect(page.locator('[data-preview-toast]')).toHaveText(
    `Moved to ${(await done.getAttribute('data-name')) ?? ''}`,
  )
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
