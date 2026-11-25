// The license, notice and copying files an installed npm package ships at its top level, read as text for the
// third-party notices of the compliance report. The rules match those the report applies inside a Maven
// archive: a bounded size, strict UTF-8 without NUL, and no name that suggests a secret.
import { type Dirent, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'

const MAX_BYTES: number = 262_144

const NAME: RegExp =
  /^(?:licen[cs]e|copying|notice)(?:-[a-z0-9]{1,20}){0,3}(?:\.(?:txt|md|markdown|rst|mit|bsd|apache))?$/iu

const PRIVATE: RegExp = /key|secret|token|private|password|credential|cert/iu

const STRICT_UTF8: TextDecoder = new TextDecoder('utf-8', { fatal: true })

const isNoticeName = (name: string): boolean => NAME.test(name) && !PRIVATE.test(name)

// A symbolic link is no regular file of the directory entry, so it is left out with every other kind.
const isCandidate = (entry: Dirent): boolean => entry.isFile() && isNoticeName(entry.name)

const textOf = (file: string): string | undefined => {
  const bytes: Buffer = readFileSync(file)
  if (bytes.length > MAX_BYTES) {
    return undefined
  }
  try {
    const text: string = STRICT_UTF8.decode(bytes)
    return text.includes('\0') ? undefined : text
  } catch {
    return undefined
  }
}

/**
 * The notice files at the top level of an installed package.
 * @param directory the installed package's directory.
 * @returns each readable file's text keyed by its name, sorted by name; a file that breaks a rule is left out.
 */
export const packageNotices = (directory: string): Readonly<Record<string, string>> =>
  Object.fromEntries(
    readdirSync(directory, { withFileTypes: true })
      .filter((entry: Dirent): boolean => isCandidate(entry))
      .map((entry: Dirent): readonly [string, string | undefined] => [
        entry.name,
        textOf(path.join(directory, entry.name)),
      ])
      .filter(
        (entry: readonly [string, string | undefined]): entry is readonly [string, string] => entry[1] !== undefined,
      )
      .toSorted((left: readonly [string, string], right: readonly [string, string]): number =>
        left[0].localeCompare(right[0], 'en'),
      ),
  )
