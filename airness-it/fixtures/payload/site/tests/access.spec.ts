import type { PayloadRequest } from 'payload'
import { describe, expect, it } from 'vitest'
import { anyone, nobody } from '@/access'

const anonymous: { readonly req: PayloadRequest } = { req: { user: null } as unknown as PayloadRequest }

describe('the access rules', () => {
  it('grants an operation to anyone', () => {
    expect(anyone(anonymous)).toBe(true)
  })

  it('refuses an operation to everyone', () => {
    expect(nobody(anonymous)).toBe(false)
  })
})
