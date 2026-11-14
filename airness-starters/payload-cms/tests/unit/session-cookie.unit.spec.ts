import { describe, expect, it } from 'vitest'
import { sessionCookie } from '@/lib/session-cookie'

describe('the session cookie', () => {
  it('travels over HTTPS only from a production build', () => {
    expect(sessionCookie(false)).toEqual({ sameSite: 'Lax', secure: true })
  })

  it('also travels over the plain HTTP the development server serves', () => {
    expect(sessionCookie(true)).toEqual({ sameSite: 'Lax', secure: false })
  })
})
