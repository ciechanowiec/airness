import { describe, expect, it } from 'vitest'
import { Posts } from '@/collections/Posts'
import { Users } from '@/collections/Users'

describe('the collections', () => {
  it('lock an account after repeated failed sign-ins', () => {
    expect(Users.auth).toMatchObject({ maxLoginAttempts: 5 })
  })

  it('keep the versions of a post', () => {
    expect(Posts.versions).toBe(true)
  })
})
