// Requests for the integration specs that exercise the REST and GraphQL APIs through Payload's real request pipeline
// and the run's own database.
import { randomBytes } from 'node:crypto'
import { GRAPHQL_POST } from '@payloadcms/next/routes'
import { handleEndpoints, type Payload } from 'payload'

import { loadServerEnvironment } from '@/lib/environment'
import config from '@/payload.config'
import type { User } from '@/payload-types'
import { type Credentials, sessionTokenFor, sessionTokenOf } from './accounts'
import { payloadInstance } from './payload'

/** The origin requests come from; Payload authenticates a session cookie only from an allowed origin. */
export const ORIGIN: string = loadServerEnvironment().serverUrl

/** The response statuses the specs expect. */
export const OK: number = 200
export const CREATED: number = 201
export const BAD_REQUEST: number = 400
export const UNAUTHENTICATED: number = 401
export const FORBIDDEN: number = 403
export const TOO_MANY_REQUESTS: number = 429
export const NOT_FOUND: number = 404

/** A complete one-pixel PNG, a real image the media collection accepts. */
export const PIXEL: Uint8Array<ArrayBuffer> = Uint8Array.from(
  Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAwS2OUAAAAABJRU5ErkJggg==',
    'base64',
  ),
)

/**
 * The cookie that carries a session.
 * @param token - the session token Payload issued.
 * @returns the `Cookie` header value.
 */
export const cookieOf = (token: string): string => `payload-token=${token}`

/**
 * The cookie that carries an account's session.
 * @param account - an account the accounts helper made.
 * @returns the `Cookie` header value.
 */
export const sessionCookieOf = async (account: User): Promise<string> => cookieOf(await sessionTokenOf(account))

/**
 * The session cookie of a new account with the given role, or no cookie for an anonymous caller.
 * @param role - the caller.
 * @param purpose - the spec's subject, which names the account.
 * @returns the `Cookie` header value, empty for an anonymous caller.
 */
export const sessionCookieFor = async (role: User['roles'][number] | 'anonymous', purpose: string): Promise<string> =>
  role === 'anonymous' ? '' : cookieOf(await sessionTokenFor(role, purpose))

const CLIENT_GROUP_BYTES: number = 2

/**
 * A client address of its own, in the IPv6 documentation range and in a /64 network no other client shares, so the
 * limit on authentication attempts counts the requests of one call or one spec and no others.
 * @returns the address, as a reverse proxy would write it into `X-Forwarded-For`.
 */
export const newClientAddress = (): string =>
  `2001:db8:${randomBytes(CLIENT_GROUP_BYTES).toString('hex')}:${randomBytes(CLIENT_GROUP_BYTES).toString('hex')}::1`

/** One request to the REST API. */
export interface ApiCall {
  readonly method: 'DELETE' | 'GET' | 'PATCH' | 'POST'
  /** The path below `/api`, such as `/users/me`. */
  readonly path: string
  readonly cookie?: string
  /** A JSON body, or form data for an upload. */
  readonly body?: FormData | object
  /** The client the request comes from, as the trusted proxy names it; a new client unless the call names one. */
  readonly client?: string
}

const bodyOf = (body: ApiCall['body']): BodyInit | null => {
  if (body === undefined) {
    return null
  }
  return body instanceof FormData ? body : JSON.stringify(body)
}

// Form data sets its own content type, with the boundary its parts are separated by.
const contentTypeOf = (body: ApiCall['body']): Record<string, string> =>
  body instanceof FormData ? {} : { 'Content-Type': 'application/json' }

/**
 * Send one request through Payload's REST pipeline, from the allowed origin.
 * @param call - the request.
 * @returns Payload's response.
 */
export const callApi = async (call: ApiCall): Promise<Response> =>
  await handleEndpoints({
    config,
    request: new Request(`${ORIGIN}/api${call.path}`, {
      method: call.method,
      headers: {
        ...contentTypeOf(call.body),
        Origin: ORIGIN,
        cookie: call.cookie ?? '',
        'X-Forwarded-For': call.client ?? newClientAddress(),
      },
      body: bodyOf(call.body),
    }),
  })

const LOGIN_MUTATION: string =
  'mutation ($email: String!, $password: String!) { loginUser(email: $email, password: $password) { exp } }'

/**
 * Attempt a login through Payload's GraphQL route, from the allowed origin and a new client.
 * @param variables - the email and secret the login submits.
 * @returns Payload's response.
 */
export const callGraphqlLogin = async (variables: Credentials): Promise<Response> =>
  await GRAPHQL_POST(config)(
    new Request(`${ORIGIN}/api/graphql`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: ORIGIN, 'X-Forwarded-For': newClientAddress() },
      body: JSON.stringify({ query: LOGIN_MUTATION, variables }),
    }),
  )

/**
 * Read an account as stored, hidden fields included, whatever any caller may see of it.
 * @param id - the account's id.
 * @returns the account.
 */
export const storedAccount = async (id: number): Promise<User> => {
  const payload: Payload = await payloadInstance
  return await payload.findByID({ collection: 'users', id, depth: 0, showHiddenFields: true })
}
