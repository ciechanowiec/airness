import { describe, expect, it } from 'vitest'
import type { DeclaredRoute } from '@/route-map.js'
import {
  answeredSeed,
  INVENTORY_SOURCE,
  inventorySeeds,
  type QueuedCrawl,
  queuedRoute,
  seedAnswer,
  seedAnswersOf,
  seededCoverageLines,
  seededRoutesOf,
  withSeeds,
} from '@/sweep-seeds.js'

// Discovery requests every inventory route that names one concrete address and no link reached, so a page only a
// redirect or an email reaches is swept like a linked one. A pattern with a variable stays with links and specs.

const SPRING: readonly (readonly [string, boolean])[] = [
  ['/account/signed-out', false],
  ['/orders/[id]', true],
  ['/files/[[...rest]]', true],
  ['/media/[segment1]', true],
]

const declaredOf = (routes: readonly (readonly [string, boolean])[]): readonly DeclaredRoute[] =>
  routes.map(
    ([route, isDynamic]: readonly [string, boolean]): DeclaredRoute => ({ file: 'Pages#any', route, isDynamic }),
  )

const isNeverSkipped = (): boolean => false

const isAdmin = (route: string): boolean => route.startsWith('/admin')

const EMPTY: QueuedCrawl = {
  queue: [],
  seen: new Set<string>(['/']),
  dropped: new Set<string>(),
  earlier: [],
  seeded: new Map<string, string | undefined>(),
}

describe('which inventory routes discovery requests', () => {
  it('requests a route with one concrete address and passes over every pattern with a variable', () => {
    // The route map spells `{id}` and `{id:\d+}` as `[id]`, `**` and `{*rest}` as `[[...rest]]`, and `*.pdf` as a
    // numbered segment; Next.js writes `[slug]` and `[[...parts]]` itself. None names an address to request.
    expect(
      inventorySeeds(
        declaredOf([...SPRING, ['/posts/[slug]', true], ['/docs/[[...parts]]', true], ['/about', false]]),
        isNeverSkipped,
      ),
    ).toEqual(['/account/signed-out', '/about'])
  })

  it('leaves out a route under a skipped prefix', () => {
    expect(
      inventorySeeds(
        declaredOf([
          ['/admin/users', false],
          ['/help', false],
        ]),
        isAdmin,
      ),
    ).toEqual(['/help'])
  })

  it('requests each address once, whichever way its trailing slash was written', () => {
    expect(
      inventorySeeds(
        declaredOf([
          ['/help/', false],
          ['/help', false],
          ['/', false],
        ]),
        isNeverSkipped,
      ),
    ).toEqual(['/help', '/'])
  })

  it('keeps a static page whose address carries a file extension, which the inventory declares a page', () => {
    expect(inventorySeeds(declaredOf([['/help/faq.html', false]]), isNeverSkipped)).toEqual(['/help/faq.html'])
  })
})

describe('how seeds join the crawl', () => {
  it('queues only the seeds no link reached, after the routes already queued, and remembers which they were', () => {
    const linked: QueuedCrawl = queuedRoute(EMPTY, '/about', '/', 10)
    const seeded: QueuedCrawl = withSeeds(linked, ['/', '/about', '/account/signed-out'], 10)
    expect(seeded.queue).toEqual([
      { route: '/about', from: '/' },
      { route: '/account/signed-out', from: INVENTORY_SOURCE },
    ])
    expect([...seeded.seeded.keys()]).toEqual(['/account/signed-out'])
  })

  it('counts seeds against the route ceiling, together with the routes an earlier visit reached', () => {
    const atCeiling: QueuedCrawl = withSeeds({ ...EMPTY, earlier: ['/', '/about'] }, ['/help', '/terms'], 3)
    expect(atCeiling.queue).toEqual([{ route: '/help', from: INVENTORY_SOURCE }])
    expect([...atCeiling.dropped]).toEqual(['/terms'])
    expect([...atCeiling.seeded.keys()]).toEqual(['/help'])
  })

  it('records what a seed came to, and nothing for a route a link queued', () => {
    const seeded: QueuedCrawl = withSeeds(queuedRoute(EMPTY, '/about', '/', 10), ['/account'], 10)
    const answered: QueuedCrawl = answeredSeed(
      answeredSeed(seeded, '/account', 'was forwarded to /login'),
      '/about',
      'x',
    )
    expect(seededRoutesOf(answered.seeded)).toEqual([{ route: '/account', answer: 'was forwarded to /login' }])
    expect(seededRoutesOf(answeredSeed(answered, '/account', undefined).seeded)).toEqual([{ route: '/account' }])
  })
})

describe('what requesting a seed came to', () => {
  it('covers a seed that answered its own page', () => {
    expect(seedAnswer(200, true, '/help')).toBeUndefined()
  })

  it('names a temporary forward, which covers only where it landed', () => {
    expect(seedAnswer(200, false, '/login')).toBe('was forwarded to /login')
  })

  it('names a status that is not a page, and a request nothing answered', () => {
    expect(seedAnswer(404, false, '')).toBe('answered 404')
    expect(seedAnswer(undefined, false, '')).toBe('did not answer')
  })
})

describe('how seeds are reported', () => {
  it('lists every seed in the coverage summary, with what it answered when it was not swept', () => {
    expect(
      seededCoverageLines([{ route: '/account/signed-out' }, { route: '/account', answer: 'answered 403' }]),
    ).toEqual(['  seeded: /account/signed-out', '  seeded: /account (answered 403)'])
  })

  it("gathers each visitor's answer for a seed neither covered, and names the signed-in one", () => {
    const answers: ReadonlyMap<string, readonly string[]> = seedAnswersOf([
      {
        visitor: 'anonymous',
        seeded: [{ route: '/account', answer: 'was forwarded to /login' }, { route: '/help' }],
      },
      { visitor: 'signed-in', seeded: [{ route: '/account', answer: 'answered 403' }] },
    ])
    expect([...answers]).toEqual([['/account', ['was forwarded to /login', 'answered 403 signed in']]])
  })
})
