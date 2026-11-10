// The routes of an application whose pages no file tree of this member declares: a Java web application served
// for the sweep from a tooling project. The build that knows its framework writes the inventory, and the member
// reads it here in the same shape a Next.js app directory yields, so the one coverage rule judges both.
import { existsSync, readFileSync } from 'node:fs'
import type { DeclaredRoute } from './route-map.js'

/** The variable naming the inventory file, which `airness:node-gate` sets for a detected web interface. */
const ROUTE_INVENTORY_VARIABLE: string = 'AIRNESS_ROUTE_INVENTORY'

const SEPARATOR: string = '\t'

const SEGMENT_SEPARATOR: string = '/'

// A path variable, `{id}` or `{id:\d+}`; the regular expression is not the route's to resolve here.
const PATH_VARIABLE: RegExp = /^\{([A-Za-z_$][\w$]*)(?::.*)?\}$/u

// A capture of the rest of the path, `{*path}`.
const REST_VARIABLE: RegExp = /^\{\*([A-Za-z_$][\w$]*)\}$/u

/**
 * One segment of a Spring MVC path pattern in the spelling the route map matches: a variable is a dynamic
 * segment, `**` and `{*rest}` an optional catch-all, and any other wildcard a dynamic segment, because the crawl
 * can only ever have reached a concrete value of it.
 * @param segment the pattern segment.
 * @param index its position, which names an anonymous wildcard.
 * @returns the segment as the route map spells it.
 */
const routeSegment = (segment: string, index: number): string => {
  if (segment === '**') {
    return '[[...rest]]'
  }
  const rest: RegExpExecArray | null = REST_VARIABLE.exec(segment)
  if (rest !== null) {
    return `[[...${rest[1] ?? 'rest'}]]`
  }
  const variable: RegExpExecArray | null = PATH_VARIABLE.exec(segment)
  if (variable !== null) {
    return `[${variable[1] ?? String(index)}]`
  }
  return segment.includes('*') || segment.includes('{') ? `[segment${String(index)}]` : segment
}

/**
 * A Spring MVC path pattern as the route map spells an address.
 * @param pattern the pattern, starting with a slash.
 * @returns the address with each variable or wildcard as a dynamic segment.
 */
const routeOfPattern = (pattern: string): string => {
  const segments: readonly string[] = pattern
    .split(SEGMENT_SEPARATOR)
    .filter((segment: string): boolean => segment !== '')
  return segments.length === 0
    ? SEGMENT_SEPARATOR
    : `${SEGMENT_SEPARATOR}${segments.map((segment: string, index: number): string => routeSegment(segment, index)).join(SEGMENT_SEPARATOR)}`
}

const isDynamic = (route: string): boolean => route.includes('[')

const lineProblem = (line: string, number: number): string | undefined => {
  const [pattern, source, ...extra] = line.split(SEPARATOR)
  if (pattern === undefined || source === undefined || extra.length > 0 || source.trim() === '') {
    return String.raw`line ${String(number)} is not "<pattern>\t<source>": ${line}`
  }
  return pattern.startsWith(SEGMENT_SEPARATOR) ? undefined : `line ${String(number)} names no absolute path: ${line}`
}

/**
 * Read an inventory: one `<pattern>\t<source>` line per route, where the source names what serves it. A route
 * served by several sources is declared once, by the first. A malformed line fails the read rather than
 * dropping a route from the accounting.
 * @param text the inventory.
 * @returns the declared routes, in the inventory's order.
 * @throws {Error} when a line is malformed.
 */
const routesOfInventory = (text: string): readonly DeclaredRoute[] => {
  const lines: readonly string[] = text.split('\n').filter((line: string): boolean => line.trim() !== '')
  const problems: readonly string[] = lines.flatMap((line: string, index: number): readonly string[] => {
    const problem: string | undefined = lineProblem(line, index + 1)
    return problem === undefined ? [] : [problem]
  })
  if (problems.length > 0) {
    throw new Error(`The route inventory is malformed:\n${problems.join('\n')}`)
  }
  const routes: readonly DeclaredRoute[] = lines.map((line: string): DeclaredRoute => {
    const [pattern = '', source = ''] = line.split(SEPARATOR)
    const route: string = routeOfPattern(pattern)
    return { file: source, route, isDynamic: isDynamic(route) }
  })
  return routes.filter(
    (declared: DeclaredRoute, index: number): boolean =>
      routes.findIndex((other: DeclaredRoute): boolean => other.route === declared.route) === index,
  )
}

/**
 * The routes the inventory the environment names declares.
 * @param environment the process environment.
 * @returns the routes, or undefined when no inventory is named.
 * @throws {Error} when the named inventory cannot be read or is malformed, because a route accounting read from
 * nothing would report every page covered.
 */
export const inventoryRoutes = (
  environment: Readonly<Record<string, string | undefined>>,
): readonly DeclaredRoute[] | undefined => {
  const file: string | undefined = environment[ROUTE_INVENTORY_VARIABLE]
  if (file === undefined || file === '') {
    return undefined
  }
  if (!existsSync(file)) {
    throw new Error(`${ROUTE_INVENTORY_VARIABLE} names ${file}, which does not exist; the routes are unassessed`)
  }
  return routesOfInventory(readFileSync(file, 'utf8'))
}
