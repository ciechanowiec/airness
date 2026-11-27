// Whether every entity of the built configuration that keeps drafts shows its page beside the editing form.
//
// A draft is work an editor checks before it goes out, and what an editor checks it for is the page it becomes:
// whether the excerpt fits its card, the picture its frame, the heading its line. Payload's Live Preview renders that
// page beside the form and renders it again after every save. Without it the editor approves fields, not a page,
// and publishes blind. So each collection and global that keeps drafts enables Live Preview, with a `url` the panel
// opens. The Preview button, `admin.preview`, only opens the page in another tab after a save, and doesn't count.
//
// The rule reads the configuration Payload builds rather than the source, because Live Preview has two spellings
// that sanitising leaves apart: the entity's own `admin.livePreview`, and the root `admin.livePreview` naming the
// entity in `collections` or `globals`. The admin panel reads both, as `isLivePreviewEnabled` and
// `getLivePreviewConfig` in `@payloadcms/ui` do: an entity is enabled when either names it, and its `url` is its
// own, or else the root's. A plugin's collection arrives here with the rest. Nothing a project declares excuses an
// entity: one that has no page to show keeps no drafts.
import { isArray, isRecord, readKey } from '@airness/node/json-shapes'
import { hasDrafts, type PayloadSubjectKind, parsePayloadReport } from './payload-defaults.js'

/** One entity of the built configuration that keeps drafts, and whether it shows its page beside the form. */
interface LivePreviewEntry {
  readonly kind: PayloadSubjectKind
  readonly slug: string
  /** Whether the admin panel shows a Live Preview for the entity. */
  readonly enabled: boolean
  /** Whether the Live Preview has an address to open, the entity's own or the root's. */
  readonly hasUrl: boolean
}

/** Every entity of the built configuration that keeps drafts, collections first, in configuration order. */
export interface LivePreviewReport {
  readonly livePreview: readonly LivePreviewEntry[]
}

// Where the root configuration names the entities of each kind it enables Live Preview for.
const ROOT_LISTS: Readonly<Record<PayloadSubjectKind, string>> = { collection: 'collections', global: 'globals' }

const isUrl = (url: unknown): boolean => typeof url === 'function' || (typeof url === 'string' && url.length > 0)

const isNamedAtRoot = (rootLivePreview: unknown, kind: PayloadSubjectKind, slug: string): boolean => {
  const named: unknown = readKey(rootLivePreview, ROOT_LISTS[kind])
  return isArray(named) && named.includes(slug)
}

const entriesOf = (
  entities: readonly unknown[],
  kind: PayloadSubjectKind,
  rootLivePreview: unknown,
): readonly LivePreviewEntry[] =>
  entities
    .filter((entity: unknown): boolean => hasDrafts(entity))
    .map((entity: unknown): LivePreviewEntry => {
      const slug: unknown = readKey(entity, 'slug')
      if (typeof slug !== 'string') {
        throw new TypeError(`a ${kind} that keeps drafts has no resolved slug`)
      }
      const own: unknown = readKey(readKey(entity, 'admin'), 'livePreview')
      // The entity's block is spread over the root's, so its own `url` wins and the root's fills in for a missing one.
      const ownUrl: unknown = readKey(own, 'url')
      return {
        kind,
        slug,
        enabled: Boolean(own) || isNamedAtRoot(rootLivePreview, kind, slug),
        hasUrl: isUrl(ownUrl === undefined ? readKey(rootLivePreview, 'url') : ownUrl),
      }
    })

/**
 * Read whether each entity of a built configuration that keeps drafts shows its page beside the editing form.
 * @param config the built Payload configuration.
 * @returns the report the probe prints.
 * @throws {TypeError} when the configuration holds no collections or globals array, or an entity no slug.
 */
export const livePreviewOf = (config: unknown): LivePreviewReport => {
  const collections: unknown = readKey(config, 'collections')
  const globals: unknown = readKey(config, 'globals')
  if (!(isArray(collections) && isArray(globals))) {
    throw new TypeError('the resolved Payload configuration has no collections or globals array')
  }
  const rootLivePreview: unknown = readKey(readKey(config, 'admin'), 'livePreview')
  return {
    livePreview: [
      ...entriesOf(collections, 'collection', rootLivePreview),
      ...entriesOf(globals, 'global', rootLivePreview),
    ],
  }
}

const isEntry = (value: unknown): value is LivePreviewEntry =>
  isRecord(value) &&
  (value['kind'] === 'collection' || value['kind'] === 'global') &&
  typeof value['slug'] === 'string' &&
  typeof value['enabled'] === 'boolean' &&
  typeof value['hasUrl'] === 'boolean'

/**
 * Read the Live Preview half of the probe's report. Missing or malformed evidence is undefined rather than empty,
 * so a probe that did not report it fails the gate instead of passing it.
 * @param text the probe's standard output.
 * @returns the report, or undefined when the probe printed none.
 */
export const parseLivePreviewReport = (text: string): LivePreviewReport | undefined => {
  const livePreview: unknown = readKey(parsePayloadReport(text), 'livePreview')
  return isArray(livePreview) && livePreview.every(isEntry) ? { livePreview } : undefined
}

const findingFor = (entry: LivePreviewEntry): readonly string[] => {
  const subject: string = `${entry.kind} "${entry.slug}"`
  if (!entry.enabled) {
    return [
      `${subject} keeps drafts but has no Live Preview, so an editor publishes a draft without seeing the page it ` +
        `becomes; give it admin.livePreview with a url, or name it in the root admin.livePreview.${ROOT_LISTS[entry.kind]}`,
    ]
  }
  return entry.hasUrl
    ? []
    : [
        `${subject} enables Live Preview without a url, so the panel has no page to show; set admin.livePreview.url ` +
          'on it or on the root admin.livePreview',
      ]
}

/**
 * Every entity that keeps drafts without showing its page beside the editing form, in configuration order.
 * @param report whether each entity that keeps drafts enables Live Preview with an address.
 * @returns one finding per entity, or nothing when every such entity shows its page.
 */
export const findMissingLivePreview = (report: LivePreviewReport): readonly string[] =>
  report.livePreview.flatMap((entry: LivePreviewEntry): readonly string[] => findingFor(entry))
