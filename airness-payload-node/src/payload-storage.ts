// Where the built configuration stores uploaded files.
//
// Payload writes an upload collection's files to a directory on the server's own disk unless the collection
// turns that off, and every storage adapter (S3, Azure, Google Cloud Storage, R2, Vercel Blob, Uploadthing)
// turns it off for the collections it handles by setting `upload.disableLocalStorage`. Files on the local disk
// vanish with a replaced container, are missing on every instance but the one that received them, and leave a
// backup that holds the documents without their files. So the rule reads the configuration Payload actually
// builds, after every plugin has run, and requires each upload collection to store elsewhere; what the adapter
// is, and how the project spelled the collection, does not matter. A deployment that keeps one server with a
// persistent volume on purpose records the collection under `airnessPayload.localStorage` with its reason.
import { isArray, readKey } from '@airness/node/json-shapes'
import { parsePayloadReport } from './payload-defaults.js'
import type { LocalStorage } from './payload-settings.js'

/** One upload collection of the built configuration, and whether it writes its files to the local disk. */
interface UploadStorageEntry {
  readonly slug: string
  readonly local: boolean
}

/** Every upload collection of the built configuration, in configuration order. */
export interface UploadStorageReport {
  readonly uploadStorage: readonly UploadStorageEntry[]
}

/**
 * Read where each upload collection of a built configuration stores its files. Payload stores locally unless
 * `upload.disableLocalStorage` is exactly true, which is the test it applies itself, so `upload: true` and an
 * upload object without the flag both store locally.
 * @param config the built Payload configuration.
 * @returns the report the probe prints.
 * @throws {TypeError} when the configuration holds no collections array, or an upload collection no slug.
 */
export const uploadStorageOf = (config: unknown): UploadStorageReport => {
  const collections: unknown = readKey(config, 'collections')
  if (!isArray(collections)) {
    throw new TypeError('the resolved Payload configuration has no collections array')
  }
  return {
    uploadStorage: collections.flatMap((collection: unknown): readonly UploadStorageEntry[] => {
      const upload: unknown = readKey(collection, 'upload')
      if (upload === undefined || upload === false) {
        return []
      }
      const slug: unknown = readKey(collection, 'slug')
      if (typeof slug !== 'string') {
        throw new TypeError('an upload collection has no resolved slug')
      }
      return [{ slug, local: readKey(upload, 'disableLocalStorage') !== true }]
    }),
  }
}

const isEntry = (value: unknown): value is UploadStorageEntry =>
  typeof readKey(value, 'slug') === 'string' && typeof readKey(value, 'local') === 'boolean'

/**
 * Read the upload storage half of the probe's report. Missing or malformed evidence is undefined rather than
 * empty, so a probe that did not report it fails the gate instead of passing it.
 * @param text the probe's standard output.
 * @returns the report, or undefined when the probe printed none.
 */
export const parseUploadStorageReport = (text: string): UploadStorageReport | undefined => {
  const uploadStorage: unknown = readKey(parsePayloadReport(text), 'uploadStorage')
  return isArray(uploadStorage) && uploadStorage.every(isEntry) ? { uploadStorage } : undefined
}

/**
 * Every upload collection that writes its files to the local disk without the project saying it means to, and
 * every declaration that excuses no such collection, since a stale excuse would silently cover the next one.
 * @param report where the built configuration stores each upload collection's files.
 * @param declared the collections the project keeps on the local disk on purpose.
 * @returns one finding per defect, in configuration order, then declaration order.
 */
export const findLocalUploadStorage = (
  report: UploadStorageReport,
  declared: readonly LocalStorage[],
): readonly string[] => {
  const excused: ReadonlySet<string> = new Set(declared.map((entry: LocalStorage): string => entry.collection))
  const local: readonly string[] = report.uploadStorage
    .filter((entry: UploadStorageEntry): boolean => entry.local)
    .map((entry: UploadStorageEntry): string => entry.slug)
  const undeclared: readonly string[] = local
    .filter((slug: string): boolean => !excused.has(slug))
    .map(
      (slug: string): string =>
        `upload collection "${slug}" stores its files on the server's local disk, which a replaced container ` +
        'loses and a second instance never sees; store them through a storage adapter such as ' +
        '@payloadcms/storage-s3, or record the collection under airnessPayload.localStorage with the reason',
    )
  const stale: readonly string[] = declared
    .filter((entry: LocalStorage): boolean => !local.includes(entry.collection))
    .map(
      (entry: LocalStorage): string =>
        `airnessPayload.localStorage declares "${entry.collection}", which is no upload collection storing on ` +
        'the local disk; remove the declaration',
    )
  return [...undeclared, ...stale]
}
