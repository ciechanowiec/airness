import { NextRequest, type NextResponse } from 'next/server'
import { describe, expect, it } from 'vitest'
import { config, proxy } from '@/proxy'
import { directiveOf } from '../helpers/csp'

// A real request against the real proxy. Nothing here is substituted: NextRequest is the type the
// framework hands the function in production, and the response carries the headers a browser would get.
const respondToHome = (): NextResponse => proxy(new NextRequest(new Request('http://localhost:3000/')))

const policyOf = (response: NextResponse): string => response.headers.get('content-security-policy') ?? ''

describe('the headers every response carries', () => {
  it('tells the browser not to re-interpret a response type', () => {
    expect(respondToHome().headers.get('x-content-type-options')).toBe('nosniff')
  })

  it('states a referrer policy rather than leaving the default', () => {
    expect(respondToHome().headers.get('referrer-policy')).toBe('strict-origin-when-cross-origin')
  })

  it('refuses framing in both the header and the policy', () => {
    const response: NextResponse = respondToHome()
    expect(response.headers.get('x-frame-options')).toBe('DENY')
    expect(directiveOf(policyOf(response), 'frame-ancestors')).toBe("frame-ancestors 'none'")
  })
})

// The nonce is the whole reason the script policy can refuse unsafe-inline, so what it is worth
// asserting is that it exists, that it is not reused, and that the renderer can find it.
describe('the script policy and its nonce', () => {
  it('admits script by nonce rather than by permitting inline script', () => {
    const script: string = directiveOf(policyOf(respondToHome()), 'script-src')
    expect(script).toContain("'nonce-")
    expect(script).not.toContain("'unsafe-inline'")
  })

  it('issues a different nonce to each response, so one cannot be replayed', () => {
    expect(policyOf(respondToHome())).not.toBe(policyOf(respondToHome()))
  })

  it('constrains what a document may load even before script is considered', () => {
    expect(directiveOf(policyOf(respondToHome()), 'default-src')).toBe("default-src 'self'")
    expect(directiveOf(policyOf(respondToHome()), 'object-src')).toBe("object-src 'none'")
  })
})

describe('where the proxy runs', () => {
  it('skips the static assets, which carry no markup for a policy to govern', () => {
    const [matcher] = config.matcher
    expect(matcher).toContain('_next/static')
  })
})
