// Who the browser sweep visits the application as. The crawl always visits as an anonymous visitor. A member
// whose end-to-end specifications hold `tests/e2e/sign-in.setup.ts` is visited a second time, signed in as
// whoever that setup signs in, so the pages behind a sign-in are judged by every pass in every engine rather
// than by the one resting scan a project specification runs. The setup signs in by the project's own means,
// because only the project knows how it signs in, and leaves the session where the signed-in tests open it.
//
// The second visit costs what is new to it. A page both visitors reach is judged again only when signing in
// changed its structure, and the passes that sample one page per template sample the signed-in pages on
// their own, so a public site whose pages look the same signed in adds little more than its crawl. The
// route ceiling counts the routes of both visitors together, so it bounds the whole run as before.
//
// Everything here is pure, so the signed-in visit's decisions are unit-tested; the sweep modules do the I/O.
import { createHash } from 'node:crypto'
import path from 'node:path'

/** Whom the sweep visits as. */
export type Visitor = 'anonymous' | 'signed-in'

/** The tag a test of the signed-in visit carries, which every file of the visit is kept apart by. */
export const SIGNED_IN_TAG: string = '@airness-signed-in'

/** The setup a member writes to be visited signed in, relative to the member. */
export const SIGN_IN_SETUP: string = 'tests/e2e/sign-in.setup.ts'

/** The directory, below the run's output directory, that every pass of the sweep hands its files through. */
export const SWEEP_DIRECTORY: string = 'airness-sweep'

const SIGNED_IN_DIRECTORY: string = 'signed-in'

/**
 * Where a member's end-to-end run writes its results, below node_modules, so a run leaves the working tree
 * as it found it.
 * @param projectDirectory the member's directory.
 * @returns the output directory.
 */
export const e2eOutputDirectory = (projectDirectory: string): string =>
  path.join(projectDirectory, 'node_modules', '.airness', 'e2e', 'test-results')

/**
 * Whether the sweep visits the member signed in as well.
 * @param projectDirectory the member's directory.
 * @param isExistingFile whether a file exists.
 * @returns true when the member holds the sign-in setup.
 */
export const hasSignedInVisitor = (projectDirectory: string, isExistingFile: (file: string) => boolean): boolean =>
  isExistingFile(path.join(projectDirectory, SIGN_IN_SETUP))

/**
 * The visitor a test of the sweep visits as.
 * @param tags the test's tags.
 * @returns the visitor.
 */
export const visitorOfTags = (tags: readonly string[]): Visitor =>
  tags.includes(SIGNED_IN_TAG) ? 'signed-in' : 'anonymous'

/**
 * The directory one visitor's passes hand their files through. The anonymous visit keeps the sweep's own
 * directory, and the signed-in visit has one beneath it, so their queues and reports never meet.
 * @param sweepRoot the sweep's directory.
 * @param visitor the visitor.
 * @returns the visitor's directory.
 */
export const visitorDirectory = (sweepRoot: string, visitor: Visitor): string =>
  visitor === 'anonymous' ? sweepRoot : path.join(sweepRoot, SIGNED_IN_DIRECTORY)

/**
 * The file the sign-in setup leaves the signed-in session in, as Playwright's storage state.
 * @param outputDirectory the run's output directory.
 * @returns the file.
 */
export const sessionFile = (outputDirectory: string): string =>
  path.join(outputDirectory, SWEEP_DIRECTORY, SIGNED_IN_DIRECTORY, 'session.json')

/**
 * A page's structure as one short value: two renders with the same elements, roles and input types in the
 * same order have the same structure, whatever text they show.
 * @param signature the page's elements, each named by its tag, role and type, in document order.
 * @returns the structure's digest.
 */
export const structureOf = (signature: string): string => createHash('sha256').update(signature).digest('hex')

/** A location as the comparison of two visits reads it. */
interface StructuredLocation {
  readonly location: string
  readonly structure?: string
}

/**
 * The signed-in locations the passes must visit: those the anonymous visit did not reach, and those whose
 * structure signing in changed. A page the anonymous visit already judged in the same structure is not
 * judged again.
 * @param signedIn the locations the signed-in crawl reached.
 * @param anonymous the locations the anonymous visit handed its passes.
 * @returns the signed-in locations to visit, in crawl order.
 */
export const locationsToRevisit = <Location extends StructuredLocation>(
  signedIn: readonly Location[],
  anonymous: readonly StructuredLocation[],
): readonly Location[] =>
  signedIn.filter(
    (location: Location): boolean =>
      !anonymous.some(
        (earlier: StructuredLocation): boolean =>
          earlier.location === location.location &&
          earlier.structure !== undefined &&
          earlier.structure === location.structure,
      ),
  )

/**
 * The locations with each one kept once, at its first appearance. Two routes that both forward to the same
 * page, as every protected route forwards an anonymous visitor to its sign-in page, land on one location.
 * @param locations the locations in crawl order.
 * @returns each location once.
 */
export const distinctLocations = <Location extends { readonly location: string }>(
  locations: readonly Location[],
): readonly Location[] =>
  locations.filter(
    (location: Location, index: number): boolean =>
      locations.findIndex((other: Location): boolean => other.location === location.location) === index,
  )

/**
 * Whether the crawl has reached the route ceiling, which counts the routes of both visits together.
 * @param seen the routes this crawl has queued.
 * @param earlier the routes an earlier visit reached, empty for the anonymous one.
 * @param ceiling the route ceiling.
 * @returns true when no further route may be queued.
 */
export const isAtRouteCeiling = (seen: ReadonlySet<string>, earlier: readonly string[], ceiling: number): boolean =>
  seen.size + new Set<string>(earlier.filter((route: string): boolean => !seen.has(route))).size >= ceiling

const OK_FIRST: number = 200
const OK_LAST: number = 299

/**
 * Whether a status answers a request itself, without forwarding or refusing it.
 * @param status the HTTP status, without following a redirect.
 * @returns true for a 2xx status.
 */
export const isOwnAnswer = (status: number): boolean => status >= OK_FIRST && status <= OK_LAST

/**
 * Whether a route proves the session: the session opens it, and a visitor without the session is forwarded
 * or refused. The signed-in crawl checks the first such route after every later visit, so a visit that ends
 * the session is caught at the route that ended it.
 * @param signedInStatus what the route answered the session, without following a redirect.
 * @param anonymousStatus what it answered a visitor without the session, likewise.
 * @returns true when the route tells the two apart.
 */
export const opensOnlyWithSession = (signedInStatus: number, anonymousStatus: number): boolean =>
  isOwnAnswer(signedInStatus) && !isOwnAnswer(anonymousStatus)

/** What the signed-in crawl found out about its session. */
export interface SessionOutcome {
  /** The route that proves the session, when the crawl found one. */
  readonly probe: string | undefined
  /** The route whose visit ended the session, when one did. */
  readonly endedAt: string | undefined
  /** How many locations the signed-in passes visit. */
  readonly revisited: number
}

/**
 * Why the signed-in visit cannot be trusted, if it cannot. A visit that ended the session left the rest of
 * the crawl anonymous, and a crawl that found nothing signing in changed most likely never signed in.
 * @param outcome what the crawl found out.
 * @returns the problems, ready to be shown as failures.
 */
export const sessionProblems = (outcome: SessionOutcome): readonly string[] => {
  if (outcome.endedAt !== undefined) {
    return [
      `visiting ${outcome.endedAt} ended the signed-in session, so the signed-in crawl stopped there; an address ` +
        'a link reaches must not change state, so end the session only on a POST, or name the route in ' +
        'accessibilitySkipRoutes with the reason',
    ]
  }
  return outcome.probe === undefined && outcome.revisited === 0
    ? [
        `signing in changed nothing the crawl could see: no page refused a visitor without the session, and every ` +
          `page looked as it does without signing in; check that ${SIGN_IN_SETUP} signs in before it calls ` +
          'saveSweepSession',
      ]
    : []
}

/**
 * Why the session, checked again once every pass finished, shows that a pass ended it.
 * @param probe the route that proves the session.
 * @param status what it answered the stored session at the end, without following a redirect, or undefined
 * when it did not answer.
 * @returns the problem, or nothing while the session still opens the route.
 */
export const endedSessionProblems = (probe: string, status: number | undefined): readonly string[] =>
  status !== undefined && isOwnAnswer(status)
    ? []
    : [
        `the signed-in session no longer opened ${probe} once the sweep finished, so a pass ended it and the ` +
          'signed-in pages it visited afterwards were judged without it; find the control that ends the session ' +
          'on a GET or on hover and make it a POST',
      ]
