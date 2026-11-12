// The security-header assertion the shipped specs and a project's own specs share, judged by the pure
// baseline beside it.
import { expect } from '@playwright/test'
import { servedByOf } from './browser-server-policy.js'
import { securityHeaderProblems } from './security-headers.js'

/** Both Playwright navigation responses and request responses expose this surface. */
export interface SecurityResponse {
  readonly headers: () => Record<string, string>
  readonly url: () => string
}

/**
 * Assert the HTML response baseline using the response that the test actually received. In a verification run,
 * which serves the production server, the response must also declare Strict-Transport-Security and mark every
 * cookie it sets Secure. A request response (`APIResponse`) carries its Set-Cookie lines in `headers()`; a
 * navigation response withholds them there, so its cookies are judged only through the sweep, which reads
 * `allHeaders()`.
 * @param response the response under judgement.
 */
export const expectSecurityHeaders = (response: SecurityResponse): void => {
  expect(
    securityHeaderProblems(response.headers(), servedByOf(process.env)),
    `security headers on ${response.url()}`,
  ).toEqual([])
}
