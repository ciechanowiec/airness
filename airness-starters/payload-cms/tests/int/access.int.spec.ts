import fc from 'fast-check'

import type { AccessResult, FieldAccess } from 'payload'
import { describe, expect, it } from 'vitest'
import { admins, adminsFieldLevel, adminsOrEditors, adminsOrSelf, anyone } from '@/access'
import type { User } from '@/payload-types'
import { accessArgument, buildUser } from '../helpers/access'

// The field-level predicates, like the collection ones, only read `req.user`.
type FieldAccessArgument = Parameters<FieldAccess>[0]

const fieldArgument = (user: User | null): FieldAccessArgument => ({ req: { user } }) as unknown as FieldAccessArgument

describe('access helpers', () => {
  it('anyone grants regardless of authentication', () => {
    expect(anyone(accessArgument(null))).toBe(true)
    expect(anyone(accessArgument(buildUser(['user'])))).toBe(true)
  })

  it('admins grants only to the admin role', () => {
    expect(admins(accessArgument(null))).toBe(false)
    expect(admins(accessArgument(buildUser(['user'])))).toBe(false)
    expect(admins(accessArgument(buildUser(['admin'])))).toBe(true)
  })

  it('adminsOrEditors grants to admins and editors', () => {
    expect(adminsOrEditors(accessArgument(null))).toBe(false)
    expect(adminsOrEditors(accessArgument(buildUser(['user'])))).toBe(false)
    expect(adminsOrEditors(accessArgument(buildUser(['editor'])))).toBe(true)
    expect(adminsOrEditors(accessArgument(buildUser(['admin'])))).toBe(true)
    // A user with mixed roles is granted when ANY role qualifies. This pins `.some` (grant) against
    // `.every` (would deny, since `user` does not qualify) - a single-role array cannot tell them apart.
    expect(adminsOrEditors(accessArgument(buildUser(['user', 'editor'])))).toBe(true)
  })

  it('adminsOrSelf denies anonymous, grants admins, scopes everyone else to their own record', () => {
    expect(adminsOrSelf(accessArgument(null))).toBe(false)
    expect(adminsOrSelf(accessArgument(buildUser(['admin'])))).toBe(true)
    expect(adminsOrSelf(accessArgument(buildUser(['user'], 42)))).toEqual({ id: { equals: 42 } })
  })

  it('adminsFieldLevel grants only to admins', () => {
    expect(adminsFieldLevel(fieldArgument(null))).toBe(false)
    expect(adminsFieldLevel(fieldArgument(buildUser(['user'])))).toBe(false)
    expect(adminsFieldLevel(fieldArgument(buildUser(['admin'])))).toBe(true)
  })
})

// Property-based tests assert invariants over EVERY generated user (any role combination, including
// duplicates and the empty set, and any id) - inputs the example tests above never enumerate. The
// seed and run count are pinned globally in vitest.setup.ts, so these are deterministic.
// Derived from the builder these arbitraries feed, so the generated roles stay the roles a User has.
interface TestUserShape {
  readonly roles: NonNullable<User['roles']>
  readonly id: number
}

describe('access helper properties (fast-check)', () => {
  const anyUser: fc.Arbitrary<TestUserShape> = fc.record({
    roles: fc.array(fc.constantFrom('admin', 'editor', 'user')),
    id: fc.integer(),
  })
  // A user who is never an admin - used for the data-isolation property.
  const nonAdminUser: fc.Arbitrary<TestUserShape> = fc.record({
    roles: fc.array(fc.constantFrom('editor', 'user')),
    id: fc.integer(),
  })

  it('admins are always a subset of admins-or-editors', () => {
    fc.assert(
      fc.property(anyUser, ({ roles, id }) => {
        const argument: ReturnType<typeof accessArgument> = accessArgument(buildUser(roles, id))
        return admins(argument) !== true || adminsOrEditors(argument) === true
      }),
    )
  })

  it('never grants a privileged role to an anonymous caller', () => {
    expect(adminsOrEditors(accessArgument(null))).toBe(false)
    expect(admins(accessArgument(null))).toBe(false)
  })

  it('adminsOrSelf scopes every non-admin to their own record id and no other', () => {
    fc.assert(
      fc.property(nonAdminUser, ({ roles, id }) => {
        const result: AccessResult | Promise<AccessResult> = adminsOrSelf(accessArgument(buildUser(roles, id)))
        return JSON.stringify(result) === JSON.stringify({ id: { equals: id } })
      }),
    )
  })
})
