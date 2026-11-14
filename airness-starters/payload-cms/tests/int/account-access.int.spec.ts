import { Forbidden, type Payload } from 'payload'
import { describe, expect, it } from 'vitest'

import type { User } from '@/payload-types'
import { createAccount, credentials, LOGIN_PHRASE } from '../helpers/accounts'
import {
  BAD_REQUEST,
  CREATED,
  callApi,
  cookieOf,
  FORBIDDEN,
  NOT_FOUND,
  OK,
  PIXEL,
  sessionCookieFor,
  sessionCookieOf,
  storedAccount,
} from '../helpers/api'
import { payloadInstance } from '../helpers/payload'

const PURPOSE: string = 'account-access'

const SCRIPTED_SVG: string = '<svg xmlns="http://www.w3.org/2000/svg"><script>alert(document.cookie)</script></svg>'
const HTML_PAGE: string = '<!doctype html><html><body><h1>Sign in again</h1></body></html>'

interface Upload {
  readonly content: Uint8Array<ArrayBuffer> | string
  readonly type: string
  readonly name: string
}

const uploadForm = (upload: Upload): FormData => {
  const form: FormData = new FormData()
  form.append('file', new Blob([upload.content], { type: upload.type }), upload.name)
  form.append('_payload', JSON.stringify({ alt: 'An uploaded file' }))
  return form
}

const upload = async (role: User['roles'][number], file: Upload): Promise<Response> =>
  await callApi({
    method: 'POST',
    path: '/media',
    cookie: await sessionCookieFor(role, PURPOSE),
    body: uploadForm(file),
  })

const mediaNamed = async (name: string): Promise<number> => {
  const payload: Payload = await payloadInstance
  const counted: { readonly totalDocs: number } = await payload.count({
    collection: 'media',
    where: { filename: { equals: name } },
  })
  return counted.totalDocs
}

describe('what a signed-in user may do to accounts', () => {
  it('cannot grant themselves the admin role', async () => {
    const user: User = await createAccount('user', PURPOSE)
    await callApi({
      method: 'PATCH',
      path: `/users/${String(user.id)}`,
      cookie: await sessionCookieOf(user),
      body: { roles: ['admin'] },
    })
    await expect(storedAccount(user.id)).resolves.toEqual(expect.objectContaining({ roles: ['user'] }))
  })

  it('can change their own name', async () => {
    const user: User = await createAccount('user', PURPOSE)
    const response: Response = await callApi({
      method: 'PATCH',
      path: `/users/${String(user.id)}`,
      cookie: await sessionCookieOf(user),
      body: { name: 'A new name' },
    })
    expect(response.status).toBe(OK)
    await expect(storedAccount(user.id)).resolves.toEqual(expect.objectContaining({ name: 'A new name' }))
  })

  it('can neither read nor change another account', async () => {
    const caller: User = await createAccount('user', PURPOSE)
    const other: User = await createAccount('user', PURPOSE)
    const cookie: string = await sessionCookieOf(caller)
    const read: Response = await callApi({ method: 'GET', path: `/users/${String(other.id)}`, cookie })
    const change: Response = await callApi({
      method: 'PATCH',
      path: `/users/${String(other.id)}`,
      cookie,
      body: { name: 'Taken over' },
    })
    expect([read.status, change.status]).toEqual([NOT_FOUND, FORBIDDEN])
    expect(await read.text()).not.toContain(other.email)
    await expect(storedAccount(other.id)).resolves.toEqual(expect.objectContaining({ name: other.name }))
  })

  it('as an editor, cannot delete an account', async () => {
    const target: User = await createAccount('user', PURPOSE)
    const response: Response = await callApi({
      method: 'DELETE',
      path: `/users/${String(target.id)}`,
      cookie: await sessionCookieFor('editor', PURPOSE),
    })
    expect(response.status).toBe(FORBIDDEN)
    await expect(storedAccount(target.id)).resolves.toEqual(expect.objectContaining({ email: target.email }))
  })

  it('as an anonymous caller, reads no account', async () => {
    await createAccount('user', PURPOSE)
    const payload: Payload = await payloadInstance
    await expect(payload.find({ collection: 'users', depth: 0, overrideAccess: false })).rejects.toThrow(Forbidden)
  })
})

describe('sessions', () => {
  it('end at logout, so the same cookie no longer authenticates', async () => {
    const account: User = await createAccount('user', PURPOSE)
    const login: Response = await callApi({
      method: 'POST',
      path: '/users/login',
      body: credentials(account.email, LOGIN_PHRASE),
    })
    const { token }: { readonly token: string } = (await login.json()) as { readonly token: string }
    const cookie: string = cookieOf(token)
    const logout: Response = await callApi({ method: 'POST', path: '/users/logout', cookie })
    expect(logout.status).toBe(OK)
    const me: Response = await callApi({ method: 'GET', path: '/users/me', cookie })
    expect(await me.json()).toEqual(expect.objectContaining({ user: null }))
  })
})

describe('media uploads', () => {
  it.each([
    { role: 'user' as const, status: FORBIDDEN, stored: 0 },
    { role: 'editor' as const, status: CREATED, stored: 1 },
    { role: 'admin' as const, status: CREATED, stored: 1 },
  ])('from a $role receive $status', async ({ role, status, stored }) => {
    const name: string = `pixel-${role}-${String(Date.now())}.png`
    const response: Response = await upload(role, { content: PIXEL, type: 'image/png', name })
    expect(response.status).toBe(status)
    expect(await mediaNamed(name)).toBe(stored)
  })

  it('refuse an SVG, which can carry script', async () => {
    const name: string = `drawing-${String(Date.now())}.svg`
    const response: Response = await upload('editor', { content: SCRIPTED_SVG, type: 'image/svg+xml', name })
    expect(response.status).toBe(BAD_REQUEST)
    expect(await mediaNamed(name)).toBe(0)
  })

  it('refuse an update that names a type the collection does not accept', async () => {
    const name: string = `pixel-retyped-${String(Date.now())}.png`
    await upload('editor', { content: PIXEL, type: 'image/png', name })
    const payload: Payload = await payloadInstance
    const found: { readonly docs: { readonly id: number }[] } = await payload.find({
      collection: 'media',
      where: { filename: { equals: name } },
      depth: 0,
      limit: 1,
    })
    const id: number | undefined = found.docs[0]?.id
    const response: Response = await callApi({
      method: 'PATCH',
      path: `/media/${String(id)}`,
      cookie: await sessionCookieFor('editor', PURPOSE),
      body: { mimeType: 'text/html' },
    })
    expect(response.status).toBe(BAD_REQUEST)
    await expect(payload.findByID({ collection: 'media', id: id ?? 0, depth: 0 })).resolves.toEqual(
      expect.objectContaining({ mimeType: 'image/png' }),
    )
  })

  it('refuse a page of HTML declared as HTML under an image name', async () => {
    const name: string = `promo-${String(Date.now())}.png`
    const response: Response = await upload('editor', { content: HTML_PAGE, type: 'text/html', name })
    expect(response.status).toBe(BAD_REQUEST)
    expect(await mediaNamed(name)).toBe(0)
  })
})
