// The wiring half of the endpoint access contract: what an `endpoints:` entry serves lives where the access rule reads.
//
// Every accepted spelling is asserted beside every reported one, because a rule that reported a handler correctly
// imported from `src/endpoints` would teach a project to move the handler anywhere that silences it.

import { describe, expect, it } from 'vitest'
import { findMisplacedEndpoints } from '@/endpoint-placement.js'

const COLLECTION: string = 'src/collections/Orders.ts'

const rulesOf = (filePath: string, source: string): readonly string[] =>
  findMisplacedEndpoints(filePath, source).map((violation) => violation.rule)

const orders = (imports: string, endpoints: string): string =>
  `${imports}
export const Orders: CollectionConfig = {
  slug: 'orders',
  endpoints: ${endpoints},
  fields: [],
}`

describe('require-endpoint-module', () => {
  it('reports a handler imported from src/lib, whose Local API calls nothing judges', () => {
    const source: string = orders(
      "import { listOrders } from '../lib/orders-endpoint'",
      "[{ path: '/recent', method: 'get', handler: listOrders }]",
    )
    expect(rulesOf(COLLECTION, source)).toEqual(['require-endpoint-module'])
  })

  it('reports a handler imported from src/hooks through the source alias', () => {
    const source: string = orders(
      "import { listOrders } from '@/hooks/orders'",
      "[{ path: '/recent', method: 'get', handler: listOrders }]",
    )
    expect(rulesOf(COLLECTION, source)).toEqual(['require-endpoint-module'])
  })

  it('reports a handler written in place', () => {
    const source: string = orders('', "[{ path: '/recent', method: 'get', handler: async (req) => Response.json({}) }]")
    expect(rulesOf(COLLECTION, source)).toEqual(['require-endpoint-module'])
  })

  it('reports a handler the file declares itself', () => {
    const source: string = orders(
      'const listOrders = async () => Response.json({})',
      "[{ path: '/recent', handler: listOrders }]",
    )
    expect(rulesOf(COLLECTION, source)).toEqual(['require-endpoint-module'])
  })

  it('reports a handler bound only by a type-only import, which binds nothing at runtime', () => {
    const source: string = orders(
      "import type { listOrders } from '../endpoints/orders'",
      "[{ path: '/recent', handler: listOrders }]",
    )
    expect(rulesOf(COLLECTION, source)).toEqual(['require-endpoint-module'])
  })

  it('reports an endpoint array the rule cannot follow', () => {
    expect(rulesOf(COLLECTION, orders('', 'makeEndpoints()'))).toEqual(['require-endpoint-module'])
  })

  it('names the directory and the rule that judges it, at the line of the entry', () => {
    const source: string = orders("import { listOrders } from '../lib/orders'", "[{ path: '/r', handler: listOrders }]")
    const [violation] = findMisplacedEndpoints(COLLECTION, source)
    expect(violation?.reason).toContain('src/endpoints/')
    expect(violation?.reason).toContain('require-endpoint-access')
    expect(violation?.line).toBe(4)
  })

  it('reports each misplaced entry of the root configuration', () => {
    const source: string = `import { health } from './endpoints/health'
import { stats } from './lib/stats'
export default buildConfig({
  endpoints: [
    { path: '/health', method: 'get', handler: health },
    { path: '/stats', method: 'get', handler: stats },
  ],
})`
    expect(findMisplacedEndpoints('src/payload.config.ts', source).map((violation) => violation.line)).toEqual([6])
  })
})

describe('require-endpoint-module accepts what src/endpoints serves', () => {
  it.each([
    { spelling: 'a relative import', imports: "import { listOrders } from '../endpoints/orders.js'" },
    { spelling: 'the source alias', imports: "import { listOrders } from '@/endpoints/orders'" },
    { spelling: 'the directory index', imports: "import { listOrders } from '../endpoints'" },
    { spelling: 'a renamed binding', imports: "import { recent as listOrders } from '../endpoints/orders'" },
    { spelling: 'a default import', imports: "import listOrders from '../endpoints/orders'" },
  ])('accepts a handler imported through $spelling', ({ imports }: { readonly imports: string }) => {
    expect(rulesOf(COLLECTION, orders(imports, "[{ path: '/recent', handler: listOrders }]"))).toEqual([])
  })

  it('accepts a whole endpoint, a spread and a namespace member imported from src/endpoints', () => {
    const source: string = orders(
      "import 'server-only'\nimport * as endpoints from '../endpoints'\n" +
        "import { recent, shared } from '../endpoints/orders'",
      '[recent, ...shared, endpoints.archive]',
    )
    expect(rulesOf(COLLECTION, source)).toEqual([])
  })

  it('accepts the shorthand handler key when the name is imported from src/endpoints', () => {
    const source: string = orders("import { handler } from '../endpoints/orders'", "[{ path: '/recent', handler }]")
    expect(rulesOf(COLLECTION, source)).toEqual([])
  })

  it('accepts a bare array reference imported from src/endpoints, and endpoints turned off', () => {
    expect(
      rulesOf(COLLECTION, orders("import { orderEndpoints } from '../endpoints/orders'", 'orderEndpoints')),
    ).toEqual([])
    expect(rulesOf(COLLECTION, orders('', 'false'))).toEqual([])
  })

  it('leaves a file under src/endpoints to require-endpoint-access', () => {
    const source: string = orders('', "[{ path: '/recent', handler: async () => Response.json({}) }]")
    expect(rulesOf('src/endpoints/orders.ts', source)).toEqual([])
  })

  it('reads the code, not a comment naming a handler, and ignores a configuration without endpoints', () => {
    const commented: string = orders(
      "import { listOrders } from '../endpoints/orders'",
      "[/* handler: fromLib */ { path: '/r', handler: listOrders }]",
    )
    expect(rulesOf(COLLECTION, commented)).toEqual([])
    expect(rulesOf(COLLECTION, "export const Tags: CollectionConfig = { slug: 'tags', fields: [] }")).toEqual([])
    expect(rulesOf('src/payload.config.ts', 'export default buildConfig(config)')).toEqual([])
  })
})
