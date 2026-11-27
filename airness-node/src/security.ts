// The security-header assertion the shipped specs and a project's own specs share, judged by the pure
// baseline beside it.
import { expect } from '@playwright/test'
import { servedByOf } from './browser-server-policy.js'
import { securityHeaderProblems } from './security-headers.js'

/** The request behind a navigation response, which a request response (`APIResponse`) does not expose. */
export interface SecurityRequest {
  readonly method: () => string
  readonly headers: () => Record<string, string>
}

/** Both Playwright navigation responses and request responses expose this surface. */
export interface SecurityResponse {
  readonly headers: () => Record<string, string>
  readonly url: () => string
  readonly status: () => number
  readonly request?: () => SecurityRequest
}

/**
 * Assert the HTML response baseline using the response that the test actually received. In a verification run,
 * which serves the production server, the response must also declare Strict-Transport-Security and mark every
 * cookie it sets Secure. A request response (`APIResponse`) carries its Set-Cookie lines in `headers()`; a
 * navigation response withholds them there, so its cookies are judged only through the sweep, which reads
 * `allHeaders()`.
 *
 * The response's caching is judged against the request it answered. A navigation response names that request's
 * method and headers itself; a request response names neither, so it is judged as the answer to a GET, and the
 * headers the test sent are passed as `sentHeaders` when they carried `Authorization`.
 * @param response the response under judgement.
 * @param sentHeaders the headers the test sent with a request response, when it sent any.
 */
export const expectSecurityHeaders = (
  response: SecurityResponse,
  sentHeaders: Readonly<Record<string, string>> = {},
): void => {
  const request: SecurityRequest | undefined = response.request?.()
  expect(
    securityHeaderProblems(response.headers(), servedByOf(process.env), {
      method: request?.method() ?? 'GET',
      status: response.status(),
      requestHeaders: { ...request?.headers(), ...sentHeaders },
    }),
    `security headers on ${response.url()}`,
  ).toEqual([])
}
