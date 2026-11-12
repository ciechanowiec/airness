import { describe, expect, it } from 'vitest'
import { Posts } from '@/collections/Posts'
import { Users } from '@/collections/Users'

describe('the collections', () => {
  it('lock an account after repeated failed sign-ins', () => {
    expect(Users.auth).toMatchObject({ maxLoginAttempts: 5 })
  })

  it('mark the session cookie Secure in production alone, which the test run is not', () => {
    expect(Users.auth).toMatchObject({ cookies: { secure: false, sameSite: 'Lax' } })
  })

  it('keep the versions of a post', () => {
    expect(Posts.versions).toBe(true)
  })
})
