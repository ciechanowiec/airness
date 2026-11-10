// The route inventory a Java web application's build writes, read in the spelling the route map matches, so
// the one coverage rule judges a Spring application's pages as it judges an app directory's.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { findUnsweptRoutes, type UnsweptRoute } from '@/route-coverage.js'
import { inventoryRoutes } from '@/route-inventory.js'
import { type DeclaredRoute, resolvedRouteOf } from '@/route-map.js'
import { PROCESS_TIME } from './helpers/process-time.js'

// The variable naming the inventory file, which the Maven node gate sets for a detected web interface.
const ROUTE_INVENTORY_VARIABLE: string = 'AIRNESS_ROUTE_INVENTORY'

// The routes an inventory with this text declares, read as a run reads the file the environment names.
const routesOfInventory = (text: string): readonly DeclaredRoute[] | undefined => {
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-inventory-'))
  try {
    const file: string = path.join(directory, 'web-routes.tsv')
    writeFileSync(file, text)
    return inventoryRoutes({ [ROUTE_INVENTORY_VARIABLE]: file })
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

describe('the route of a path pattern', PROCESS_TIME, () => {
  it.each([
    ['/', '/'],
    ['//', '/'],
    ['/contacts', '/contacts'],
    ['/contacts/{id}', '/contacts/[id]'],
    [String.raw`/contacts/{id:\d+}/edit`, '/contacts/[id]/edit'],
    ['/files/{*path}', '/files/[[...path]]'],
    ['/docs/**', '/docs/[[...rest]]'],
    ['/reports/*', '/reports/[segment1]'],
    ['/reports/*.pdf', '/reports/[segment1]'],
  ])('reads %s as %s', (pattern: string, route: string) => {
    expect(routesOfInventory(`${pattern}\tPages#page\n`)?.[0]?.route).toBe(route)
  })

  it('lets a crawled address resolve to the variable route it was rendered by', () => {
    const routes: readonly DeclaredRoute[] =
      routesOfInventory('/contacts\tContacts#list\n/contacts/{id}\tContacts#show\n') ?? []
    expect(resolvedRouteOf(routes, '/contacts/42')?.file).toBe('Contacts#show')
    expect(resolvedRouteOf(routes, '/contacts')?.file).toBe('Contacts#list')
  })
})

describe('the routes of an inventory', PROCESS_TIME, () => {
  it('declares each route once, by the first source that serves it, and skips blank lines', () => {
    expect(routesOfInventory('/\tHome#index\n\n/about\tPages#about\n/\tHome#welcome\n')).toEqual([
      { file: 'Home#index', route: '/', isDynamic: false },
      { file: 'Pages#about', route: '/about', isDynamic: false },
    ])
  })

  it('refuses a malformed line rather than dropping its route from the accounting', () => {
    expect(() => routesOfInventory('/about\n')).toThrow(/line 1 is not/u)
    expect(() => routesOfInventory('about\tPages#about\n')).toThrow(/line 1 names no absolute path/u)
    expect(() => routesOfInventory('/a\tA#a\textra\n')).toThrow(/line 1 is not/u)
  })

  it('reports an inventory route no crawl reached as unswept, naming what serves it', () => {
    const unswept: readonly UnsweptRoute[] = findUnsweptRoutes({
      declaredRoutes: routesOfInventory('/\tHome#index\n/hidden\tPages#hidden\n') ?? [],
      visitedRoutes: ['/'],
      answeredRoutes: ['/'],
      skippedPrefixes: [],
      customSkippedPrefixes: [],
      specs: [],
      everyFile: [],
    })
    expect(unswept).toMatchObject([{ file: 'Pages#hidden', route: '/hidden', rule: 'route-unswept' }])
  })
})

describe('inventoryRoutes', PROCESS_TIME, () => {
  it('reads no inventory when the environment names none', () => {
    expect(inventoryRoutes({})).toBeUndefined()
    expect(inventoryRoutes({ [ROUTE_INVENTORY_VARIABLE]: '' })).toBeUndefined()
  })

  it('reads the named inventory, and fails on a missing one rather than accounting for nothing', () => {
    const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-inventory-'))
    try {
      const file: string = path.join(directory, 'web-routes.tsv')
      writeFileSync(file, '/\tHome#index\n')
      expect(inventoryRoutes({ [ROUTE_INVENTORY_VARIABLE]: file })).toEqual([
        { file: 'Home#index', route: '/', isDynamic: false },
      ])
      expect(() => inventoryRoutes({ [ROUTE_INVENTORY_VARIABLE]: path.join(directory, 'missing.tsv') })).toThrow(
        /does not exist; the routes are unassessed/u,
      )
    } finally {
      rmSync(directory, { recursive: true, force: true })
    }
  })
})
