import { type APIResponse, expect, test } from '@playwright/test'
import { seedTestAdmin, testAdmin } from '../helpers/test-admin'

test.describe('Custom route access', () => {
  test.beforeAll(async () => {
    await seedTestAdmin()
  })

  test('does not disclose user counts to an anonymous caller', async ({ request }) => {
    const response: APIResponse = await request.get('/my-route')
    expect(response.status()).toBe(401)
    expect(await response.json()).toEqual({ message: 'Authentication required' })
  })

  test('lets an authenticated administrator count the users they may read', async ({ request }) => {
    const login: APIResponse = await request.post('/api/users/login', {
      data: { email: testAdmin.email, password: testAdmin.password },
    })
    expect(login.status()).toBe(200)
    const session: unknown = await login.json()
    const token: string = (session as { readonly token: string }).token
    expect(token).toEqual(expect.any(String))
    const response: APIResponse = await request.get('/my-route', {
      headers: { Authorization: `JWT ${token}` },
    })
    expect(response.status()).toBe(200)
    const result: unknown = await response.json()
    expect(result).toMatchObject({
      message: 'This is an example of a custom route.',
      users: expect.any(Number),
    })
    expect((result as { readonly users: number }).users).toBeGreaterThan(0)
  })
})
