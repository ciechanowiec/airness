import { randomUUID } from 'node:crypto'
import type { Payload } from 'payload'
import type { User } from '@/payload-types'
import { payloadInstance } from './payload'

/** The password of every account {@link createAccount} makes. */
export const LOGIN_PHRASE: string = 'integration-test-phrase'

/** The failed attempts that lock an account, as the Users collection's `maxLoginAttempts` sets them. */
export const MAX_LOGIN_ATTEMPTS: number = 5
/** How long a locked account stays locked, as the Users collection's `lockTime` sets it. */
export const LOCK_DURATION_MS: number = 600_000

/** The sign-in body naming an email and the secret that goes with it. */
export interface Credentials {
  readonly email: string
  readonly password: string
}

/**
 * The body a sign-in or registration sends.
 * @param email - the account's email.
 * @param secret - the secret submitted for it.
 * @returns the credentials.
 */
export const credentials = (email: string, secret: string): Credentials => ({ email, password: secret })

/**
 * Create a fresh account with one role in the run's disposable database.
 * @param role - the role the account holds.
 * @param purpose - the spec's subject, which names the account so a failure shows where it came from.
 * @returns the stored account.
 */
export const createAccount = async (role: User['roles'][number], purpose: string): Promise<User> => {
  const payload: Payload = await payloadInstance
  return await payload.create({
    collection: 'users',
    data: {
      email: `${purpose}-${randomUUID()}@example.test`,
      name: `${purpose} test account`,
      password: LOGIN_PHRASE,
      roles: [role],
    },
  })
}

/**
 * Create an account that is locked for the next ten minutes, as five failed sign-ins leave one.
 * @param purpose - the spec's subject, which names the account.
 * @returns the stored account.
 */
export const lockedAccount = async (purpose: string): Promise<User> => {
  const account: User = await createAccount('user', purpose)
  const payload: Payload = await payloadInstance
  return await payload.update({
    collection: 'users',
    id: account.id,
    data: { loginAttempts: MAX_LOGIN_ATTEMPTS, lockUntil: new Date(Date.now() + LOCK_DURATION_MS).toISOString() },
  })
}

/**
 * Sign an account in through Payload.
 * @param account - an account {@link createAccount} made.
 * @returns the session token Payload issued.
 */
export const sessionTokenOf = async (account: User): Promise<string> => {
  const payload: Payload = await payloadInstance
  const session: { readonly token?: string | undefined } = await payload.login({
    collection: 'users',
    data: credentials(account.email, LOGIN_PHRASE),
  })
  if (session.token === undefined) {
    throw new Error('Payload did not return a session token for the test account.')
  }
  return session.token
}

/**
 * Create a fresh account with one role and sign it in through Payload.
 * @param role - the role the account holds.
 * @param purpose - the spec's subject, which names the account.
 * @returns the session token Payload issued.
 */
export const sessionTokenFor = async (role: User['roles'][number], purpose: string): Promise<string> =>
  await sessionTokenOf(await createAccount(role, purpose))
