import type { Payload } from 'payload'
import { describe, expect, it } from 'vitest'

import { GET } from '@/app/my-route/route'
import type { User } from '@/payload-types'
import { sessionTokenFor } from '../helpers/accounts'
import { OK, ORIGIN, UNAUTHENTICATED } from '../helpers/api'
import { payloadInstance } from '../helpers/payload'

const ROUTE_URL: string = new URL('/my-route', ORIGIN).href

// The route answers what it decides from a real Payload session against the disposable database, so
// each case signs in a fresh account rather than stubbing authentication.
const loggedInHeaders = async (role: User['roles'][number]): Promise<Headers> =>
  new Headers({ Authorization: `JWT ${await sessionTokenFor(role, 'custom-route')}` })

describe('custom route', () => {
  it('refuses an anonymous caller without disclosing a count', async () => {
    const response: Response = await GET(new Request(ROUTE_URL))
    expect(response.status).toBe(UNAUTHENTICATED)
    expect(await response.json()).toEqual({ message: 'Authentication required' })
  })

  it('counts the users an administrator may read', async () => {
    const response: Response = await GET(new Request(ROUTE_URL, { headers: await loggedInHeaders('admin') }))
    const payload: Payload = await payloadInstance
    const { totalDocs } = await payload.count({ collection: 'users' })
    expect(response.status).toBe(OK)
    expect(await response.json()).toEqual({ message: 'This is an example of a custom route.', users: totalDocs })
  })

  it('counts only themselves for a user who may read no other account', async () => {
    const response: Response = await GET(new Request(ROUTE_URL, { headers: await loggedInHeaders('user') }))
    expect(response.status).toBe(OK)
    expect(await response.json()).toEqual({ message: 'This is an example of a custom route.', users: 1 })
  })
})
