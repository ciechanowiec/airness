// Which routes the accessibility sweep leaves alone. The harness itself skips nothing: a stack layer whose
// framework serves pages outside the public site, such as an admin panel, contributes their prefixes, and a
// project declares its own with a reason. Every prefix names a whole subtree and matches at a segment
// boundary, so `/p` never takes `/products` and `/pricing` with it, and no prefix may cover the whole site.
import type { DeclaredRoute } from './route-map.js'

const SEGMENT_SEPARATOR: string = '/'

/** One declared exclusion, as far as the route rules read it. */
interface DeclaredEntry {
  readonly setting: string
  readonly pattern: string
}

const withoutTrailingSeparators = (prefix: string): string =>
  prefix.length > 1 && prefix.endsWith(SEGMENT_SEPARATOR) ? withoutTrailingSeparators(prefix.slice(0, -1)) : prefix

const isAtSegmentBoundary = (route: string, prefix: string): boolean => {
  const subtree: string = withoutTrailingSeparators(prefix)
  return route === subtree || route.startsWith(`${subtree}${SEGMENT_SEPARATOR}`)
}

/**
 * Whether a declared prefix names a subtree rather than the whole site: it starts at the root and names at
 * least one segment. `/`, an empty text, or a relative text would switch the sweep off rather than narrow it.
 * @param prefix the declared prefix.
 * @returns true for a prefix the sweep can honour.
 */
export const isSubtreePrefix = (prefix: string): boolean =>
  prefix.startsWith(SEGMENT_SEPARATOR) && withoutTrailingSeparators(prefix).length > 1

/**
 * Whether a route lies outside the accessibility sweep: some prefix, a layer's or the project's, names the
 * subtree it is in.
 * @param route the URL path or declared route pattern.
 * @param prefixes every effective prefix, the layers' and the project's.
 * @param customPrefixes the prefixes the project itself declared.
 * @returns whether the route is outside the accessibility sweep.
 */
export const isSkippedRoute = (
  route: string,
  prefixes: readonly string[],
  customPrefixes: readonly string[] = [],
): boolean =>
  [...prefixes, ...customPrefixes]
    .filter((prefix: string): boolean => isSubtreePrefix(prefix))
    .some((prefix: string): boolean => isAtSegmentBoundary(route, prefix))

const isCovered = (prefix: string, route: DeclaredRoute): boolean => isAtSegmentBoundary(route.route, prefix)

// Against the declared pages, where the framework declares any: a prefix must skip some and not all of them.
const reachFindings = (named: string, prefix: string, declared: readonly DeclaredRoute[]): readonly string[] => {
  const skipped: number = declared.filter((route: DeclaredRoute): boolean => isCovered(prefix, route)).length
  if (declared.length === 0 || (skipped > 0 && skipped < declared.length)) {
    return []
  }
  const effect: string =
    skipped === 0
      ? 'skips no page the member declares, so it records a decision with no effect'
      : 'skips every page the member declares, which switches the accessibility sweep off'
  return [`${named} ${effect}`]
}

/**
 * Every declared route prefix the sweep cannot honour as a narrowing: one that names the whole site, and,
 * where the member's pages are known, one that skips none of them or every one of them.
 * @param entries the project's `accessibilitySkipRoutes` entries.
 * @param declared the pages the member's file tree declares, or undefined when the framework declares none.
 * @returns one finding per refused entry.
 */
export const skipRouteFindings = (
  entries: readonly DeclaredEntry[],
  declared: readonly DeclaredRoute[] | undefined,
): readonly string[] =>
  entries
    .filter((entry: DeclaredEntry): boolean => entry.setting === 'accessibilitySkipRoutes')
    .flatMap((entry: DeclaredEntry): readonly string[] => {
      const named: string = `accessibilitySkipRoutes entry "${entry.pattern}"`
      if (!isSubtreePrefix(entry.pattern)) {
        return [`${named} names no subtree; write a prefix such as /drafts, never one that covers the whole site`]
      }
      return reachFindings(named, entry.pattern, declared ?? [])
    })
