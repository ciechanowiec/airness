// The two facts the shipped access-boundary specification reads about the project it runs in: the anonymous
// permissions the project grants on purpose, and the fields its built configuration stores nothing behind.
// The specification runs in a Playwright worker whose working directory is the member, so both are read from
// there. What the running application's answer means is decided in access-boundary.ts.
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { readKey } from '@airness/node/json-shapes'
import { type DatalessFields, datalessFieldsIn } from './access-boundary.js'
import { readJsonFile } from './json-file.js'
import { payloadConfigPathOf } from './payload-defaults.js'
import { type PublicAccess, readPayloadSettings } from './payload-settings.js'

/**
 * The anonymous permissions the member declares under `airnessPayload.publicAccess`.
 * @returns the declared grants; none when the member declares none.
 */
export const declaredPublicAccess = (): readonly PublicAccess[] => {
  const manifest: string = path.join(process.cwd(), 'package.json')
  return readPayloadSettings(readJsonFile(manifest)).publicAccess
}

/**
 * Every field path the member's Payload configuration reports but stores nothing behind. A configuration that
 * cannot be read yields nothing, which judges every reported path and so errs towards more findings.
 * @returns the dataless paths of each entity that has any, keyed by slug; empty when unreadable.
 */
export const datalessFields = async (): Promise<DatalessFields> => {
  try {
    const root: string = process.cwd()
    const tsconfig: unknown = readJsonFile(path.join(root, 'tsconfig.json'))
    const configModule: unknown = await import(pathToFileURL(path.join(root, payloadConfigPathOf(tsconfig))).href)
    // Awaited either way: `buildConfig` returns a promise, and a plain object is read the same.
    return datalessFieldsIn(await readKey(configModule, 'default'))
  } catch {
    return {}
  }
}
