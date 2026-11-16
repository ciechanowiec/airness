import { type NextRequest, NextResponse } from 'next/server'
import { isDevelopment } from '@/lib/environment'
import { contentSecurityPolicy, type Framing, framingOf, staticSecurityHeaders } from '@/lib/security-headers'

// The only place a per-request security value can be produced: a nonce has to differ per response, so
// it cannot come from `next.config.ts`, which is read once.
//
// Named `proxy` rather than `middleware`: Next.js 16 deprecates the middleware file convention, and
// the governing standard bans calling an API its own supplier marks deprecated. Nothing reported it -
// `next build` says so in a warning, which is not a finding, and the lint rule that bans a deprecated
// API reads typed symbols rather than a framework's file naming.
//
// The nonce is what lets the script policy refuse `'unsafe-inline'` at all. Next.js reads it back out
// of the request's own Content-Security-Policy header and stamps it onto the scripts it injects, so
// those run while anything an editor managed to store does not.
//
// The policy itself is built in `src/lib/security-headers.ts`, which decides everything and reads
// nothing; what is left here is the interception, which is the one part that cannot be pure.

// Base64 of a v4 UUID: unpredictable per request, which is the whole requirement, and available on the
// Edge runtime without importing anything.
const newNonce = (): string => btoa(crypto.randomUUID())

/**
 * Answer a request with the security headers the application has decided on.
 * @param request - the incoming request, whose headers carry the nonce through to the renderer.
 * @returns the response, carrying the policy on both the request and the response.
 */
export const proxy = (request: NextRequest): NextResponse => {
  const nonce: string = newNonce()
  const isDevelopmentServer: boolean = isDevelopment()
  const framing: Framing = framingOf(request.nextUrl.pathname)
  const policy: string = contentSecurityPolicy(nonce, isDevelopmentServer, framing)
  const headers: Readonly<Record<string, string>> = staticSecurityHeaders(isDevelopmentServer, framing)

  // Set on the REQUEST as well as the response: this is where Next.js looks for the nonce when it
  // renders, and a policy present only on the response would leave its own scripts unnonced and blocked.
  const forwarded: Headers = new Headers(request.headers)
  forwarded.set('x-nonce', nonce)
  forwarded.set('content-security-policy', policy)

  const response: NextResponse = NextResponse.next({ request: { headers: forwarded } })
  response.headers.set('content-security-policy', policy)
  for (const [name, value] of Object.entries(headers)) {
    response.headers.set(name, value)
  }
  return response
}

/**
 * Where this runs: everything but the static assets, which carry no markup for a policy to govern and
 * would only pay the cost of running it on each one.
 */
export const config: { readonly matcher: readonly string[] } = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico).*)'],
}
