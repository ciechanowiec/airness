// Reading a configuration file a Payload gate consults, such as a tsconfig, which may carry comments and
// trailing commas. The parser is the node layer's; reading the file and treating an absent or unreadable one as
// undeclared is this stack's need.
import { readFileSync } from 'node:fs'
import { parseJsonc } from '@airness/node/json-shapes'

/**
 * Read a JSON file that may carry comments and trailing commas.
 * @param file the absolute path.
 * @returns the parsed value, or undefined when the file is absent or unparseable.
 */
export const readJsonFile = (file: string): unknown => {
  try {
    return parseJsonc(readFileSync(file, 'utf8')).value
  } catch {
    return undefined
  }
}
