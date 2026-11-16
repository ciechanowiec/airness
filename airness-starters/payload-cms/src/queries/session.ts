import type { Payload, TypedUser } from 'payload'

import { payloadClient } from './payload'

/**
 * The user a request's Payload session cookie names, as Payload itself authenticates it: a token that expired, was
 * revoked or belongs to a deleted account names nobody.
 * @param headers the request's headers, which carry the session cookie.
 * @returns the user, or undefined for an anonymous request.
 */
export const findSignedInUser = async (headers: Headers): Promise<TypedUser | undefined> => {
  const payload: Payload = await payloadClient()
  const { user } = await payload.auth({ headers })
  return user ?? undefined
}
