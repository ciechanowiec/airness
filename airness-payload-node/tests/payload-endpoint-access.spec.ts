// The access decision of a route handler or server action, where the handler's own file does not show it: an
// action written inline in a Server Component, and a helper the handler calls in another of the member's files.
import type { SpecSource } from '@airness/node/axe-coverage'
import { describe, expect, it } from 'vitest'
import { findEndpointViolations, findReachedEndpointViolations } from '@/payload-policy.js'
import type { LocatedViolation } from '@/relationship-cleanup.js'

const endpointRulesOf = (path: string, source: string): readonly string[] =>
  findEndpointViolations(path, source).map((violation) => violation.rule)

const SERVER_ACTION_CALL: string = "await payload.find({ collection: 'media', depth: 0, limit: 10 })"

describe('require-endpoint-access, in a server action', () => {
  it.each(["'use server'", '"use server";', "'use strict'\n'use server'"])(
    'judges every call of a module that opens with %s',
    (directive: string) => {
      expect(endpointRulesOf('src/actions/media.ts', `${directive}\n${SERVER_ACTION_CALL}`)).toEqual([
        'require-endpoint-access',
      ])
    },
  )

  it.each([
    "export async function save() { 'use server'\n  SERVER_CALL }",
    'export const save = async () => {\n  "use server";\n  SERVER_CALL\n}',
    "<form action={async () => { 'use server'; SERVER_CALL }} />",
  ])('judges a function whose body opens with the directive: %s', (shape: string) => {
    const source: string = shape.replace('SERVER_CALL', SERVER_ACTION_CALL)
    expect(endpointRulesOf('src/app/(frontend)/page.tsx', source)).toEqual(['require-endpoint-access'])
  })

  it('judges only the server action of a Server Component, not the render beside it', () => {
    const source: string = [
      'export default async function Page() {',
      `  ${SERVER_ACTION_CALL}`,
      "  async function save() { 'use server'",
      `    ${SERVER_ACTION_CALL}`,
      '  }',
      '}',
    ].join('\n')
    expect(findEndpointViolations('src/app/(frontend)/page.tsx', source).map((found) => found.line)).toEqual([4])
  })

  it('accepts a server action that states the decision', () => {
    const source: string = "'use server'\nawait payload.find({ collection: 'm', depth: 0, overrideAccess: false })"
    expect(endpointRulesOf('src/actions/media.ts', source)).toEqual([])
  })

  it('ignores a directive that is not the first statement of the module', () => {
    expect(endpointRulesOf('src/lib/media.ts', `const mode = 'x'\n'use server'\n${SERVER_ACTION_CALL}`)).toEqual([])
  })

  // Payload generates its own admin and API glue, whose server functions hand the request to Payload.
  it('leaves the generated Payload application glue alone', () => {
    const source: string = `'use server'\n${SERVER_ACTION_CALL}`
    expect(endpointRulesOf('src/app/(payload)/layout.tsx', source)).toEqual([])
    expect(endpointRulesOf('src/app/(payload)/api/[...slug]/route.ts', SERVER_ACTION_CALL)).toEqual([])
  })

  it('judges a call through a name the action binds to getPayload', () => {
    const source: string = [
      "'use server'",
      'const cms = await getPayload({ config })',
      'await cms.count({})',
    ].join('\n')
    expect(endpointRulesOf('src/actions/media.ts', source)).toEqual(['require-endpoint-access'])
  })
})

const HELPER: string = 'src/lib/posts.ts'
const reachedOf = (files: readonly SpecSource[]): readonly LocatedViolation[] => findReachedEndpointViolations(files)
const handlerCalling = (call: string): string =>
  ["import { listPosts } from '@/lib/posts'", 'export const handler = async (req) => {', `  ${call}`, '}'].join('\n')

// A handler that hands its read to a helper runs that read for the same stranger.
describe('require-endpoint-access, one call into the member source', () => {
  it.each([
    ['a block that receives payload', 'export const listPosts = async (payload) => { await payload.count({}) }'],
    ['an arrow that returns the call', 'export const listPosts = (payload) => payload.find({ depth: 0 })'],
    ['a declaration', 'export async function listPosts(req) { return req.payload.find({ req, depth: 0 }) }'],
    [
      'an instance the helper opens',
      'export async function listPosts() { const cms = await getPayload({ config }); return cms.find({ depth: 0 }) }',
    ],
  ])('reports a helper that leaves the decision to the default, written as %s', (_shape: string, helper: string) => {
    const found: readonly LocatedViolation[] = reachedOf([
      { path: 'src/endpoints/posts.ts', source: handlerCalling('return listPosts(req.payload)') },
      { path: HELPER, source: helper },
    ])
    expect(found.map((located) => [located.path, located.violation.line, located.violation.rule])).toEqual([
      ['src/endpoints/posts.ts', 3, 'require-endpoint-access'],
    ])
  })

  it('names the helper, its module and the operation', () => {
    const helper: string = "export const listPosts = (payload) => payload.find({ collection: 'posts', depth: 0 })"
    const reason: string =
      reachedOf([
        { path: 'src/app/api/posts/route.ts', source: handlerCalling('return listPosts(payload)') },
        { path: HELPER, source: helper },
      ])[0]?.violation.reason ?? ''
    expect([reason.includes('listPosts()'), reason.includes('@/lib/posts'), reason.includes('find()')]).toEqual([
      true,
      true,
      true,
    ])
  })

  it('follows a helper a server action calls', () => {
    const action: string = ["'use server'", "import { listPosts } from '../lib/posts'", 'listPosts(payload)'].join('\n')
    const helper: string = 'export const listPosts = (payload) => payload.count({})'
    const files: readonly SpecSource[] = [
      { path: 'src/actions/posts.ts', source: action },
      { path: HELPER, source: helper },
    ]
    expect(reachedOf(files)).toHaveLength(1)
  })

  it('accepts a helper that states the decision', () => {
    const helper: string = 'export const listPosts = (payload) => payload.find({ depth: 0, overrideAccess: false })'
    const files: readonly SpecSource[] = [
      { path: 'src/endpoints/posts.ts', source: handlerCalling('return listPosts(req.payload)') },
      { path: HELPER, source: helper },
    ]
    expect(reachedOf(files)).toEqual([])
  })

  it('does not follow a call from a file that is no endpoint', () => {
    const helper: string = 'export const listPosts = (payload) => payload.find({ depth: 0 })'
    const files: readonly SpecSource[] = [
      { path: 'src/hooks/posts.ts', source: handlerCalling('return listPosts(req.payload)') },
      { path: HELPER, source: helper },
    ]
    expect(reachedOf(files)).toEqual([])
  })

  // A helper in another endpoint module is judged there, so following it would report one call twice.
  it('leaves a helper in a module the rule judges whole to that module', () => {
    const files: readonly SpecSource[] = [
      { path: 'src/endpoints/posts.ts', source: "import { listPosts } from './shared'\nlistPosts(payload)" },
      { path: 'src/endpoints/shared.ts', source: 'export const listPosts = (payload) => payload.count({})' },
    ]
    expect(reachedOf(files)).toEqual([])
  })
})
