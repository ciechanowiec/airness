// The half of the `payload-defaults` gate that runs inside the project: it imports the project's built Payload
// configuration and reports which operations of which entities still carry Payload's own `defaultAccess`. The
// gate runs it through tsx, with the member as the working directory so `payload` and the configuration's own
// imports resolve from the project, and parses the one marker-prefixed JSON line it prints. What the report
// means is decided in payload-defaults.ts; this only reads.
//
// It also asks every drafts-enabled entity's read rule what it answers a caller with no credentials. That is
// the one place the harness calls a project's function rather than inspecting it: the constraint a read
// returns is the half `/api/access` reports and the anonymous sweep never opens. The call is confined to
// entities that keep drafts, it is awaited so an asynchronous rule is not punished for being one, and a rule
// that throws is reported as undecidable rather than assumed safe.
//
// Identity with Payload's default is judged by reference first and by source text second, because a loader can
// register the same module twice under two URLs, and a project function cannot be byte-identical to Payload's
// by accident. Nothing here imports `payload` by name: the copy that matters is the one the project installed.
import { pathToFileURL } from 'node:url'
import { isArray, isRecord, readKey } from './json-shapes.js'
import {
  type AnonymousRead,
  accessOperationsFor,
  type DraftReadEntry,
  INHERITED_ACCESS_REPORT_MARKER,
  type InheritedAccessEntry,
  type InheritedAccessReport,
  type PayloadSubjectKind,
  QUERY_PRESETS_SLUG,
} from './payload-defaults.js'
import { payloadSecurityOf } from './payload-security.js'

// What Payload hands an access rule, cut down to the half a read of a stranger's turns on. A rule that reaches
// for anything else throws, and a throw is reported rather than read as a denial.
type AccessRule = (arguments_: { readonly req: { readonly user: null } }) => unknown

const isAccessRule = (value: unknown): value is AccessRule => typeof value === 'function'

const hasDrafts = (entity: unknown): boolean => Boolean(readKey(readKey(entity, 'versions'), 'drafts'))

const anonymousReadOf = async (entity: unknown): Promise<AnonymousRead> => {
  const rule: unknown = readKey(readKey(entity, 'access'), 'read')
  if (!isAccessRule(rule)) {
    // An undeclared read is Payload's own default, which the inherited half of the report names.
    return { kind: 'denied' }
  }
  try {
    const answer: unknown = await rule({ req: { user: null } })
    if (answer === true) {
      return { kind: 'open' }
    }
    return isRecord(answer) ? { kind: 'filtered', where: answer } : { kind: 'denied' }
  } catch (error: unknown) {
    return { kind: 'undecidable', reason: error instanceof Error ? error.message : String(error) }
  }
}

const draftReadsOf = async (
  entities: readonly unknown[],
  kind: PayloadSubjectKind,
): Promise<readonly DraftReadEntry[]> =>
  await Promise.all(
    entities
      .filter((entity: unknown): boolean => hasDrafts(entity))
      .map(
        async (entity: unknown): Promise<DraftReadEntry> => ({
          kind,
          slug: String(readKey(entity, 'slug')),
          anonymousRead: await anonymousReadOf(entity),
        }),
      ),
  )

const defaultAccessIn = async (defaultAccessFile: string): Promise<unknown> => {
  const defaultAccess: unknown = readKey(await import(pathToFileURL(defaultAccessFile).href), 'defaultAccess')
  if (typeof defaultAccess !== 'function') {
    throw new TypeError(`Payload's default access could not be established from ${defaultAccessFile}`)
  }
  return defaultAccess
}

/**
 * Import a built Payload configuration and report the access it leaves to Payload's defaults.
 * @param configFile the absolute path of the configuration.
 * @param defaultAccessFile the absolute path of Payload's `dist/auth/defaultAccess.js`.
 * @returns the report line the gate parses, marker included.
 */
export const inheritedAccessProbe = async (configFile: string, defaultAccessFile: string): Promise<string> => {
  const defaultAccess: unknown = await defaultAccessIn(defaultAccessFile)
  const defaultAccessSource: string = String(defaultAccess)
  // Undeclared counts as inherited: handed nothing, Payload falls through to `if (req.user) return true`, so a
  // missing rule is an open one, indistinguishable at runtime from Payload's own default.
  const isInherited = (rule: unknown): boolean =>
    rule === undefined || rule === defaultAccess || (typeof rule === 'function' && String(rule) === defaultAccessSource)
  // Awaited either way: `buildConfig` returns a promise, and a configuration handed over as a plain object is
  // read the same.
  const config: unknown = await readKey(await import(pathToFileURL(configFile).href), 'default')
  const collections: unknown = readKey(config, 'collections')
  const globals: unknown = readKey(config, 'globals')
  if (!(isArray(collections) && isArray(globals))) {
    throw new Error(`the default export of ${configFile} is not a built Payload configuration`)
  }
  // Payload builds the query-presets collection's rules itself, wrapping each operation in a closure that falls
  // back to its own default; the block the project wrote sits on the configuration instead.
  const accessOf = (entity: unknown, slug: string): unknown =>
    readKey(slug === QUERY_PRESETS_SLUG ? readKey(config, 'queryPresets') : entity, 'access')
  // Which operations an entity owes depends on what it is, read from the built object: `auth` survives as an
  // object on an auth collection, and `versions` survives where it is enabled.
  const entryOf = (entity: unknown, kind: PayloadSubjectKind): InheritedAccessEntry => {
    const slug: string = String(readKey(entity, 'slug'))
    const access: unknown = accessOf(entity, slug)
    const operations: readonly string[] = accessOperationsFor({
      kind,
      hasAuth: Boolean(readKey(entity, 'auth')),
      hasVersions: Boolean(readKey(entity, 'versions')),
    })
    return {
      slug,
      inherited: operations.filter((operation: string): boolean => isInherited(readKey(access, operation))),
    }
  }
  const report: InheritedAccessReport = {
    collections: collections.map((collection: unknown): InheritedAccessEntry => entryOf(collection, 'collection')),
    draftReads: [...(await draftReadsOf(collections, 'collection')), ...(await draftReadsOf(globals, 'global'))],
    globals: globals.map((global: unknown): InheritedAccessEntry => entryOf(global, 'global')),
  }
  return `${INHERITED_ACCESS_REPORT_MARKER}${JSON.stringify({ ...report, ...payloadSecurityOf(config) })}`
}
