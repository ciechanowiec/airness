// The Payload layer owns this specification and ships it compiled, so a project never holds a copy of it. The
// one thing a project declares about it, the anonymous permissions it grants on purpose, goes under the
// `ploaness` key of package.json, as `publicAccess`.
//
// The static rules read the access block a project wrote. They cannot see what `read: anyone` decides, because
// that is a name, and they cannot see what Payload made of the whole configuration after sanitisation. Payload
// answers the question itself: `/api/access` reports, for the caller making the request, what each collection
// and global permits. Called with no credentials it is exactly the anonymous view.
import { type APIRequestContext, type APIResponse, expect, test } from '@playwright/test'
import { datalessFields, declaredPublicAccess } from './access.js'
import {
  type AccessReport,
  accessReportOf,
  type DatalessFields,
  staleDeclarations,
  undeclaredGrants,
} from './access-boundary.js'
import type { PublicAccess } from './payload-settings.js'

// Payload's own endpoint for what the caller making this request may do; it exists in every Payload application.
const ACCESS_ENDPOINT: string = '/api/access'

test.describe('Access boundary', () => {
  test('an unauthenticated caller receives only the access the project declared', async ({
    request,
    baseURL: origin = '',
  }: {
    readonly request: APIRequestContext
    readonly baseURL: string | undefined
  }): Promise<void> => {
    expect(origin, 'the harness Playwright configuration must declare a server URL').not.toBe('')
    // Anonymous by construction: the request fixture carries no credential, and the harness's configuration
    // sets no storage state a project could extend.
    const anonymous: APIResponse<unknown> = await request.get(`${origin}${ACCESS_ENDPOINT}`)
    expect(anonymous.ok(), `${ACCESS_ENDPOINT} did not answer; every Payload application serves it`).toBe(true)
    const report: AccessReport = accessReportOf(await anonymous.json())
    const dataless: DatalessFields = await datalessFields()
    const declared: readonly PublicAccess[] = declaredPublicAccess()

    expect(report.canAccessAdmin ?? false, 'the admin panel must not admit an unauthenticated caller').toBe(false)
    expect(
      undeclaredGrants(report, declared, dataless),
      'the running application grants these to anyone, and the project has not said it meant to. Close the ' +
        'access rule, or record the grant under ploaness.publicAccess with the reason it is intended',
    ).toEqual([])
    // An entry the application does not bear out records a decision with no effect, and covers the field the
    // day it is exposed again.
    expect(
      staleDeclarations(report, declared, dataless),
      'the project declares these under ploaness.publicAccess, and the running application grants none of ' +
        'them to anyone. Correct the path, or remove the entry',
    ).toEqual([])
  })
})
