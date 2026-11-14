import type { Access } from 'payload'
import { describe, expect, it } from 'vitest'
import { Media } from '@/collections/Media'
import { Users } from '@/collections/Users'
import { accessArgument, buildUser } from '../helpers/access'
import { LOCK_DURATION_MS, MAX_LOGIN_ATTEMPTS } from '../helpers/accounts'

describe('Users collection', () => {
  it('is keyed on the users slug and uses email as the title', () => {
    expect(Users.slug).toBe('users')
    expect(Users.admin?.useAsTitle).toBe('email')
  })

  it('enforces auth hardening (login attempt cap and lock time)', () => {
    expect(Users.auth).toEqual(
      expect.objectContaining({ maxLoginAttempts: MAX_LOGIN_ATTEMPTS, lockTime: LOCK_DURATION_MS }),
    )
  })

  it('restricts admin-panel access to admins and editors', () => {
    const adminPanelAccess: Access = Users.access?.admin as Access
    expect(adminPanelAccess).toBeTypeOf('function')
    expect(adminPanelAccess(accessArgument(buildUser(['editor'])))).toBe(true)
    expect(adminPanelAccess(accessArgument(buildUser(['user'])))).toBe(false)
    expect(adminPanelAccess(accessArgument(null))).toBe(false)
  })
})

describe('Media collection', () => {
  it('is an upload collection that is publicly readable', () => {
    expect(Media.slug).toBe('media')
    expect(Media.upload).toBeTruthy()
    expect(Media.access?.read?.(accessArgument(null))).toBe(true)
  })

  // The collection is readable by anyone, so what it will ACCEPT is the whole of its exposure. An
  // `image/*` wildcard would satisfy the harness rule and still admit `image/svg+xml`, which is a
  // document that can carry script and would be served from this origin - so the list is checked for
  // what it leaves out rather than only for being present.
  it('accepts no file type that a browser would execute', () => {
    const accepted: readonly string[] = typeof Media.upload === 'object' ? (Media.upload.mimeTypes ?? []) : []
    expect(accepted.length).toBeGreaterThan(0)
    expect(accepted).not.toContain('image/svg+xml')
    expect(accepted.some((type: string): boolean => type.includes('*'))).toBe(false)
  })
})
