import type { Access } from 'payload'
import type { User } from '@/payload-types'

// The access predicates only read `req.user`, so a minimal cast is enough to exercise every branch
// without booting Payload or touching the database.
type AccessArgument = Parameters<Access>[0]

/**
 * A user carrying only what the access predicates read.
 * @param roles - the roles the user holds.
 * @param id - the user's record id.
 * @returns the user.
 */
export const buildUser = (roles: User['roles'], id = 1): User => ({ id, roles }) as unknown as User

/**
 * The argument a collection access predicate receives for a request made by the given user.
 * @param user - the requesting user, or null for an anonymous caller.
 * @returns the access argument.
 */
export const accessArgument = (user: User | null): AccessArgument => ({ req: { user } }) as unknown as AccessArgument
