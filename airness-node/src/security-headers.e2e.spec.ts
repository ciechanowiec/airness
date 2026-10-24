// The harness owns this specification and runs it from its own dist, so a project never holds a copy of it.
// The baseline it asserts is judged by `securityHeaderProblems`.
import { type APIResponse, expect, test } from '@playwright/test'
import { expectSecurityHeaders } from './security.js'

test.describe('Security headers', () => {
  test('the application answers with a baseline of security headers', async ({ request, baseURL: origin = '' }) => {
    expect(origin, 'the Airness Playwright configuration must declare a server URL').not.toEqual('')
    const response: APIResponse<unknown> = await request.get(origin)
    expect(response.ok(), `${origin} did not answer`).toBe(true)
    expectSecurityHeaders(response)
  })
})
