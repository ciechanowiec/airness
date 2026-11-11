// The facts a Payload member may declare under the `airnessPayload` key of its package.json. The node layer's
// settings live under `airness`; these two exist only because Payload does, so they belong to this layer.
// Every default is strict: a member that declares nothing grants no anonymous permission, and the analysis
// placeholders are the two variables Payload itself requires.
import { asRecord, asStringRecord, asText, isArray, readKey } from '@airness/node/json-shapes'

/** The key of package.json a Payload member declares its Payload settings under. */
const PAYLOAD_SETTINGS_KEY: string = 'airnessPayload'

/**
 * One anonymous permission a project grants on purpose, with the reason it does.
 *
 * The access-boundary sweep asks the running application what it grants an unauthenticated caller and fails
 * on what it finds. Some grants are the point of the site, so a project records those here and the sweep
 * passes them. An entry missing any field is dropped, which restores the finding rather than widening the
 * sweep.
 */
export interface PublicAccess {
  /** The collection or global slug, as Payload itself reports it. */
  readonly entity: string
  /** The operation granted: create, read, update, or delete. */
  readonly operation: string
  readonly reason: string
  /**
   * The fields of that entity a stranger reaches through this operation, each named by the path that
   * reaches it (`sessions.id`), and `*` for the map Payload collapsed because every field was open. A path
   * ending in `.**` declares that group and every field beneath it.
   */
  readonly fields?: readonly string[]
}

/** The Payload settings a gate reads, every field populated. */
export interface PayloadSettings {
  /** The anonymous permissions the project grants on purpose. */
  readonly publicAccess: readonly PublicAccess[]
  /**
   * Placeholder environment for the gates that import the configuration without running the application. A
   * Payload configuration validates `process.env` at module scope, so it cannot load in a bare shell. The
   * values are never connected to; they only let the module evaluate.
   */
  readonly analysisEnv: Readonly<Record<string, string>>
}

// The two variables Payload itself requires of every project. Inert placeholders: no host is contacted and no
// token is signed with them. The package manifest names the same values under airness.layer.analysisEnvironment,
// for the node gates that import the configuration, such as knip; a test holds the two equal.
const DEFAULT_ANALYSIS_ENV: Readonly<Record<string, string>> = {
  PAYLOAD_SECRET: 'airness-payload-static-analysis-placeholder',
  DATABASE_URL: 'postgres://127.0.0.1:5432/airness-payload-static-analysis',
}

const asTexts = (raw: unknown): readonly string[] =>
  isArray(raw) ? raw.filter((entry: unknown): entry is string => typeof entry === 'string') : []

// Dropped on any missing field: a granted anonymous permission is a decision the project must record, and a
// typo must re-expose the finding rather than excuse a permission nobody chose. A malformed `fields` falls
// back to declaring no field, since the entity-level grant beside it was stated correctly.
const asPublicAccess = (raw: unknown): readonly PublicAccess[] =>
  isArray(raw)
    ? raw.flatMap((entry: unknown): readonly PublicAccess[] => {
        const record: Record<string, unknown> = asRecord(entry)
        const entity: string = asText(record['entity']).trim()
        const operation: string = asText(record['operation']).trim()
        const reason: string = asText(record['reason']).trim()
        return entity.length > 0 && operation.length > 0 && reason.length > 0
          ? [{ entity, operation, reason, fields: asTexts(record['fields']) }]
          : []
      })
    : []

/**
 * Read the Payload settings a member declared.
 * @param packageJson the member's parsed package.json.
 * @returns the effective settings.
 */
export const readPayloadSettings = (packageJson: unknown): PayloadSettings => {
  const block: unknown = readKey(packageJson, PAYLOAD_SETTINGS_KEY)
  return {
    publicAccess: asPublicAccess(readKey(block, 'publicAccess')),
    analysisEnv: { ...DEFAULT_ANALYSIS_ENV, ...asStringRecord(readKey(block, 'analysisEnv')) },
  }
}
