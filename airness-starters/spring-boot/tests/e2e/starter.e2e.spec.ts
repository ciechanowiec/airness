import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
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

// The due day is typed or picked from a calendar the browser's own date control would not let the stylesheet
// reach. The page is scanned with the calendar open, so its days and month buttons meet the same checks as
// every other control, and a picked day is the one the task is saved with.
test('a day picked from the calendar is the day the task keeps', async ({ page }) => {
  await signIn(page, ADMINISTRATOR, CREDENTIAL)
  const title: string = `Calendar task ${randomUUID()}`
  await writeTask(page, title)
  await page.goto(`/tasks?search=${encodeURIComponent(title)}`)
  const edit: string | null = await page.getByRole('link', { name: title }).getAttribute('href')
  await page.goto(edit ?? '')
  await page.locator('[data-date-field] summary').click()
  const calendar: Locator = page.locator('calendar-date')
  await expect(calendar).toBeVisible()
  await expectSweptPage(page)
  await calendar.locator('button[part~="day"]').filter({ hasText: /^15$/u }).click()
  await expect(calendar).toBeHidden()
  const day: string = await page.locator('input[name="dueOn"]').inputValue()
  expect(day).toMatch(/^\d{4}-\d{2}-15$/u)
  await page.locator('main form').first().locator('button[type="submit"]').click()
  await expect(page).toHaveURL(/notice=SAVED$/u)
  await page.goto(edit ?? '')
  await expect(page.locator('input[name="dueOn"]')).toHaveValue(day)
})

// A one-page PDF, written out whole so the test carries no binary fixture of its own.
const PDF: string = [
  '%PDF-1.4',
  '1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj',
  '2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj',
  '3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >> endobj',
  'trailer << /Root 1 0 R >>',
  '%%EOF',
].join('\n')

// Files attached to a task are drawn from the renditions of their pictures on the task's page, its list and its
// board, so each is scanned with them, and the task's page again with a picture opened large in its dialog.
test('a task with attached files passes the scans', async ({ page }) => {
  const { email, secret } = await admitUser(page)
  await signIn(page, email, secret)
  const title: string = `Attached task ${randomUUID()}`
  await writeTask(page, title)
  await page.goto(`/tasks?search=${encodeURIComponent(title)}`)
  const edit: string | null = await page.getByRole('link', { name: title }).getAttribute('href')
  await page.goto(edit ?? '')
  await page.locator('#attachment-files').setInputFiles([
    {
      name: 'empty.webp',
      mimeType: 'image/webp',
      buffer: await readFile('src/main/resources/static/images/empty.webp'),
    },
    { name: 'brief.pdf', mimeType: 'application/pdf', buffer: Buffer.from(PDF, 'latin1') },
  ])
  await page.locator('form[enctype="multipart/form-data"] button[type="submit"]').click()
  await expect(page).toHaveURL(/notice=ATTACHED$/u)
  await expectSweptPage(page)
  await page.locator('button[aria-haspopup="dialog"]').first().click()
  await expect(page.locator('dialog[open]')).toBeVisible()
  await expectSweptPage(page)
  for (const address of [`/tasks?search=${encodeURIComponent(title)}`, '/tasks/board']) {
    await expect(await open(page, address)).toBeVisible()
    await expectSweptPage(page)
  }
})

// A portrait is drawn beside the reader's name in the navigation and large on their account page, each from
// the rendition that fills it.
test('a reader with a portrait passes the scans', async ({ page }) => {
  const { email, secret } = await admitUser(page)
  await signIn(page, email, secret)
  await page.goto('/account')
  await page.locator('#portrait-file').setInputFiles({
    name: 'me.webp',
    mimeType: 'image/webp',
    buffer: await readFile('src/main/resources/static/images/empty.webp'),
  })
  await page.locator('form[action="/account/portrait"] button[type="submit"]').click()
  await expect(page).toHaveURL(/notice=PORTRAIT_CHANGED$/u)
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
