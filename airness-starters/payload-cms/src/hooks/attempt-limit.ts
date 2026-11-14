// The limit on the authentication attempts one client may make, whether or not they name an account.
//
// It covers the operations a caller reaches without being signed in: signing in and both halves of password
// recovery. It runs before the other `beforeOperation` hooks, so a refused attempt costs neither the password
// derivation that keeps a refusal's timing even nor an audit record. A call through the Local API is the server's own
// code rather than a client's, so it is not counted.
import type { CollectionBeforeOperationHook } from 'payload'
import { APIError } from 'payload'
import { type AttemptLedger, attemptLedger } from '@/lib/attempt-ledger'
import { forwardedClientKey } from '@/lib/client-address'
import { loadServerEnvironment } from '@/lib/environment'
import { operationOf } from '@/lib/hook-argument'

const LIMITED_OPERATIONS: ReadonlySet<string> = new Set<string>(['login', 'forgotPassword', 'resetPassword'])

/** Twenty attempts a minute: far above a person mistyping, far below what keeps a thread pool busy. */
const MAX_ATTEMPTS: number = 20
const WINDOW_MS: number = 60_000
const MAX_TRACKED_CLIENTS: number = 10_000

const ledger: AttemptLedger = attemptLedger({
  maxAttempts: MAX_ATTEMPTS,
  windowMs: WINDOW_MS,
  maxTrackedClients: MAX_TRACKED_CLIENTS,
})

const TRUSTED_PROXY_COUNT: number = loadServerEnvironment().trustedProxyCount

const BAD_REQUEST: number = 400
const TOO_MANY_REQUESTS: number = 429

export const limitAuthenticationAttempts: CollectionBeforeOperationHook = (hookArgument) => {
  const { args, req } = hookArgument
  if (req.payloadAPI === 'local' || !LIMITED_OPERATIONS.has(operationOf(hookArgument))) {
    return args
  }
  const client: string | undefined = forwardedClientKey(req.headers.get('x-forwarded-for'), TRUSTED_PROXY_COUNT)
  if (client === undefined) {
    req.payload.logger.error(
      `An authentication attempt carried no client address ${String(TRUSTED_PROXY_COUNT)} trusted proxies from the end ` +
        'of X-Forwarded-For. Check TRUSTED_PROXY_COUNT against the proxies in front of the server.',
    )
    throw new APIError('The request could not be attributed to a client.', BAD_REQUEST, undefined, true)
  }
  if (ledger.recordAttempt(client, Date.now()) === 'refused') {
    throw new APIError('Too many attempts. Try again in a minute.', TOO_MANY_REQUESTS, undefined, true)
  }
  return args
}
