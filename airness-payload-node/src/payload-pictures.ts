// What the built configuration does to an uploaded picture before it stores it.
//
// Payload stores an uploaded file byte for byte unless the collection hands sharp an option to work with:
// `resizeOptions`, `formatOptions`, `trimOptions` or `constructorOptions`, the test Payload applies itself. A
// photograph from a phone carries what the camera wrote into it, such as where and when it was taken, and Payload
// serves the stored file to whoever may read the collection, which for a site's pictures is everybody. With any of
// those options Payload re-encodes the original, turned upright by its orientation tag and without that metadata,
// unless `withMetadata` keeps it. So the rule reads the configuration Payload actually builds and requires each
// upload collection that admits a picture to re-encode its originals and to keep no metadata. A collection that
// keeps the original as it was on purpose, such as an archive of photographs whose capture data is the point,
// records the collection under `airnessPayload.keptMetadata` with its reason.
import { isArray, readKey } from '@airness/node/json-shapes'
import { parsePayloadReport } from './payload-defaults.js'
import type { KeptMetadata } from './payload-settings.js'

// The types Payload hands to sharp, which is what `canResizeImage` in Payload's uploads lists. A collection that
// names no type admits every type, and `image/*` admits every picture.
const PROCESSED_TYPES: ReadonlySet<string> = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
  'image/tiff',
  'image/avif',
  'image/*',
  '*/*',
])

// The options whose presence makes Payload pass an upload through sharp.
const PROCESSING_OPTIONS: readonly string[] = ['resizeOptions', 'formatOptions', 'trimOptions', 'constructorOptions']

/** One upload collection of the built configuration, and what it does to an uploaded picture. */
interface PictureHandlingEntry {
  readonly slug: string
  /** Whether the collection admits a picture Payload can re-encode. */
  readonly pictures: boolean
  /** Whether Payload re-encodes the originals of the collection. */
  readonly reencoded: boolean
  /** Whether the collection asks Payload to keep the metadata of a re-encoded original, always or by request. */
  readonly keepsMetadata: boolean
}

/** Every upload collection of the built configuration, in configuration order. */
export interface PictureHandlingReport {
  readonly pictureHandling: readonly PictureHandlingEntry[]
}

const declaresPictures = (upload: unknown): boolean => {
  const mimeTypes: unknown = readKey(upload, 'mimeTypes')
  return (
    !isArray(mimeTypes) ||
    mimeTypes.length === 0 ||
    mimeTypes.some((type: unknown): boolean => typeof type === 'string' && PROCESSED_TYPES.has(type.toLowerCase()))
  )
}

const entryOf = (collection: unknown): readonly PictureHandlingEntry[] => {
  const upload: unknown = readKey(collection, 'upload')
  if (upload === undefined || upload === false) {
    return []
  }
  const slug: unknown = readKey(collection, 'slug')
  if (typeof slug !== 'string') {
    throw new TypeError('an upload collection has no resolved slug')
  }
  const withMetadata: unknown = readKey(upload, 'withMetadata')
  return [
    {
      slug,
      pictures: declaresPictures(upload),
      reencoded: PROCESSING_OPTIONS.some((option: string): boolean => Boolean(readKey(upload, option))),
      keepsMetadata: withMetadata !== undefined && withMetadata !== false,
    },
  ]
}

/**
 * Read what each upload collection of a built configuration does to an uploaded picture. `upload: true` names no
 * type and no option, so it admits every picture and stores each one as it arrived.
 * @param config the built Payload configuration.
 * @returns the report the probe prints.
 * @throws {TypeError} when the configuration holds no collections array, or an upload collection no slug.
 */
export const pictureHandlingOf = (config: unknown): PictureHandlingReport => {
  const collections: unknown = readKey(config, 'collections')
  if (!isArray(collections)) {
    throw new TypeError('the resolved Payload configuration has no collections array')
  }
  return {
    pictureHandling: collections.flatMap((collection: unknown): readonly PictureHandlingEntry[] => entryOf(collection)),
  }
}

const isEntry = (value: unknown): value is PictureHandlingEntry =>
  typeof readKey(value, 'slug') === 'string' &&
  typeof readKey(value, 'pictures') === 'boolean' &&
  typeof readKey(value, 'reencoded') === 'boolean' &&
  typeof readKey(value, 'keepsMetadata') === 'boolean'

/**
 * Read the picture half of the probe's report. Missing or malformed evidence is undefined rather than empty, so a
 * probe that did not report it fails the gate instead of passing it.
 * @param text the probe's standard output.
 * @returns the report, or undefined when the probe printed none.
 */
export const parsePictureHandlingReport = (text: string): PictureHandlingReport | undefined => {
  const pictureHandling: unknown = readKey(parsePayloadReport(text), 'pictureHandling')
  return isArray(pictureHandling) && pictureHandling.every(isEntry) ? { pictureHandling } : undefined
}

const carriesCameraMetadata = (entry: PictureHandlingEntry): boolean =>
  entry.pictures && (!entry.reencoded || entry.keepsMetadata)

const findingFor = (entry: PictureHandlingEntry): string =>
  entry.reencoded
    ? `upload collection "${entry.slug}" keeps the metadata of an uploaded picture through upload.withMetadata, ` +
      'so whoever reads the file learns where and when it was taken; drop withMetadata, or record the collection ' +
      'under airnessPayload.keptMetadata with the reason'
    : `upload collection "${entry.slug}" stores an uploaded picture as it arrived, with what the camera wrote into ` +
      'it, such as where and when it was taken; give upload an option Payload re-encodes the original with, such ' +
      'as constructorOptions: { limitInputPixels }, or record the collection under airnessPayload.keptMetadata ' +
      'with the reason'

/**
 * Every upload collection that keeps the metadata of an uploaded picture without the project saying it means to,
 * and every declaration that excuses no such collection, since a stale excuse would silently cover the next one.
 * @param report what the built configuration does to an uploaded picture in each upload collection.
 * @param declared the collections the project keeps the metadata of on purpose.
 * @returns one finding per defect, in configuration order, then declaration order.
 */
export const findKeptPictureMetadata = (
  report: PictureHandlingReport,
  declared: readonly KeptMetadata[],
): readonly string[] => {
  const excused: ReadonlySet<string> = new Set(declared.map((entry: KeptMetadata): string => entry.collection))
  const keeping: readonly PictureHandlingEntry[] = report.pictureHandling.filter(
    (entry: PictureHandlingEntry): boolean => carriesCameraMetadata(entry),
  )
  const undeclared: readonly string[] = keeping
    .filter((entry: PictureHandlingEntry): boolean => !excused.has(entry.slug))
    .map((entry: PictureHandlingEntry): string => findingFor(entry))
  const keepingSlugs: ReadonlySet<string> = new Set(keeping.map((entry: PictureHandlingEntry): string => entry.slug))
  const stale: readonly string[] = declared
    .filter((entry: KeptMetadata): boolean => !keepingSlugs.has(entry.collection))
    .map(
      (entry: KeptMetadata): string =>
        `airnessPayload.keptMetadata declares "${entry.collection}", which is no upload collection keeping the ` +
        'metadata of a picture; remove the declaration',
    )
  return [...undeclared, ...stale]
}
