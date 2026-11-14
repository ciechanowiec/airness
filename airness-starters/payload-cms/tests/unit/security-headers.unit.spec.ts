import { describe, expect, it } from 'vitest'
import { contentSecurityPolicy, staticSecurityHeaders } from '@/lib/security-headers'
import { directiveOf } from '../helpers/csp'

const NONCE: string = 'test-nonce'

const scriptSourcesFor = (isDevelopment: boolean): string =>
  directiveOf(contentSecurityPolicy(NONCE, isDevelopment), 'script-src')

// Both modes are asserted because only one of them can ever run the suite. The production policy is
// the one that ships, so leaving it to whichever mode the tests happen to run in would mean the policy
// users actually receive is the policy nothing here has ever looked at.
describe('what a production build permits', () => {
  it('admits script by nonce rather than by permitting inline script', () => {
    const script: string = scriptSourcesFor(false)
    expect(script).toContain(`'nonce-${NONCE}'`)
    expect(script).not.toContain("'unsafe-inline'")
  })

  it('does not permit eval, which only the development server needs', () => {
    expect(scriptSourcesFor(false)).not.toContain("'unsafe-eval'")
  })

  it('confines connections to this origin, with no websocket', () => {
    expect(directiveOf(contentSecurityPolicy(NONCE, false), 'connect-src')).toBe("connect-src 'self'")
  })
})

describe('what the development server additionally needs', () => {
  it('permits eval, because hot reloading evaluates modules with it', () => {
    expect(scriptSourcesFor(true)).toContain("'unsafe-eval'")
  })

  it('permits the websocket it reloads over', () => {
    expect(directiveOf(contentSecurityPolicy(NONCE, true), 'connect-src')).toContain('ws:')
  })

  it('still refuses inline script, which is what the nonce exists to avoid needing', () => {
    expect(scriptSourcesFor(true)).not.toContain("'unsafe-inline'")
  })
})

describe('what no mode relaxes', () => {
  it('refuses framing outright', () => {
    expect(directiveOf(contentSecurityPolicy(NONCE, true), 'frame-ancestors')).toBe("frame-ancestors 'none'")
    expect(directiveOf(contentSecurityPolicy(NONCE, false), 'frame-ancestors')).toBe("frame-ancestors 'none'")
  })

  it('refuses plugin content in either mode', () => {
    expect(directiveOf(contentSecurityPolicy(NONCE, true), 'object-src')).toBe("object-src 'none'")
    expect(directiveOf(contentSecurityPolicy(NONCE, false), 'object-src')).toBe("object-src 'none'")
  })

  it.each([true, false])('states the headers that carry no per-request value (development: %s)', (isDevelopment) => {
    const headers: Readonly<Record<string, string>> = staticSecurityHeaders(isDevelopment)
    expect(headers['x-content-type-options']).toBe('nosniff')
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin')
    expect(headers['x-frame-options']).toBe('DENY')
  })
})

describe('transport security', () => {
  it('holds browsers to HTTPS for two years, subdomains included, from a production build', () => {
    expect(staticSecurityHeaders(false)['strict-transport-security']).toBe('max-age=63072000; includeSubDomains')
  })

  it('leaves the development server, which serves plain HTTP on localhost, without it', () => {
    expect(staticSecurityHeaders(true)).not.toHaveProperty('strict-transport-security')
  })
})
