// What a gate needs to know about the node project it judges, resolved once per run.
import { type Dirent, existsSync, readdirSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { asStringRecord, type ParsedJson, parseJsonc, readKey } from './json-shapes.js'
import { layerDefaultsFor } from './layer.js'
import { readSettings, type Settings } from './settings.js'
import { workingTreeFiles } from './working-tree.js'
import { hasOwnRuntime, nestedWithin, ROOT_MEMBER_PATH, readWorkspacePackages, selectProjects } from './workspace.js'

const nodeRequire: NodeJS.Require = createRequire(import.meta.url)

/**
 * One member of the node project a gate runs in: the project itself, or a member its pnpm workspace
 * selects. Each member is judged as its own package.
 */
export interface Member {
  /** The member's own directory. */
  readonly root: string
  /** Relative to the node project, `.` for the project itself. */
  readonly path: string
  /** The member's parsed package.json, or undefined when it has none. */
  readonly packageJson: unknown
  /** The settings the member declared. */
  readonly settings: Settings
  /** Whether the member is an application, which holds its JSX to the accessibility rules. */
  readonly hasOwnRuntime: boolean
  /** The members nested inside this one, relative to its directory, where an analyzer must stop. */
  readonly siblingPaths: readonly string[]
}

/**
 * Read a JSON file, returning undefined when it is absent or unparseable. Comments and trailing commas
 * are tolerated, because a tsconfig legally carries them.
 * @param file the absolute path.
 * @returns the parsed value, or undefined.
 */
export const readJson = (file: string): unknown =>
  existsSync(file) ? parseJsonc(readFileSync(file, 'utf8')).value : undefined

/**
 * Read a text file, returning undefined when it is absent.
 * @param file the absolute path.
 * @returns its text, or undefined.
 */
export const readText = (file: string): string | undefined =>
  existsSync(file) ? readFileSync(file, 'utf8') : undefined

const MANIFEST: string = 'package.json'
const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'

// Bounded and exclusionary on purpose: nothing a node project governs is nested deeper than a couple of
// directories, and neither an installed package nor a tool's cache is a member.
const WALK_DEPTH: number = 3
const isSkipped = (name: string): boolean => name.startsWith('.') || name === 'node_modules' || name === 'target'

const walkManifestDirectories = (
  root: string,
  relative: string = ROOT_MEMBER_PATH,
  depth: number = 0,
): readonly string[] => {
  const absolute: string = relative === ROOT_MEMBER_PATH ? root : path.join(root, relative)
  const here: readonly string[] = existsSync(path.join(absolute, MANIFEST)) ? [relative] : []
  if (depth >= WALK_DEPTH) {
    return here
  }
  const children: readonly string[] = readdirSync(absolute, { withFileTypes: true })
    .filter((entry: Dirent): boolean => entry.isDirectory() && !isSkipped(entry.name))
    .flatMap((entry: Dirent): readonly string[] =>
      walkManifestDirectories(
        root,
        relative === ROOT_MEMBER_PATH ? entry.name : path.join(relative, entry.name),
        depth + 1,
      ),
    )
  return [...here, ...children]
}

// Candidate members come from the files a commit would carry, so a directory git ignores - a build
// output, a scratch copy - never becomes a member. Outside a git checkout the directory is walked.
const manifestDirectories = (root: string): readonly string[] => {
  try {
    return workingTreeFiles(root)
      .filter((file: string): boolean => path.basename(file) === MANIFEST)
      .map((file: string): string => {
        const directory: string = path.dirname(file)
        return directory === '' ? ROOT_MEMBER_PATH : directory
      })
  } catch {
    return walkManifestDirectories(root)
  }
}

const createMember = (root: string, memberPath: string, every: readonly string[]): Member => {
  const directory: string = memberPath === ROOT_MEMBER_PATH ? root : path.join(root, memberPath)
  const packageJson: unknown = readJson(path.join(directory, MANIFEST))
  return {
    root: directory,
    path: memberPath,
    packageJson,
    settings: readSettings(packageJson, layerDefaultsFor(directory, packageJson, readJson)),
    hasOwnRuntime: hasOwnRuntime(packageJson),
    siblingPaths: nestedWithin(memberPath, every),
  }
}

/**
 * The members of the node project rooted at a directory: the project itself, then each member its
 * `pnpm-workspace.yaml` selects.
 * @param root the node project directory.
 * @returns the members, the project first.
 */
export const membersOf = (root: string): readonly Member[] => {
  const workspaceFile: string = path.join(root, WORKSPACE_FILE)
  const patterns: readonly string[] = existsSync(workspaceFile)
    ? readWorkspacePackages(readFileSync(workspaceFile, 'utf8'))
    : []
  const paths: readonly string[] =
    patterns.length === 0 ? [ROOT_MEMBER_PATH] : selectProjects(patterns, manifestDirectories(root))
  return paths.map((memberPath: string): Member => createMember(root, memberPath, paths))
}

// Locating a tool is harder than it looks. A package may restrict its `exports` so `pkg/package.json` is
// not addressable, and it may publish an `import` condition only, so CJS resolution of its entry point
// fails too. Three routes are tried in turn, and the last one always works: find the entry point through
// ESM resolution and walk up to the manifest beside it.
const manifestFrom = (directory: string): string => {
  const candidate: string = path.join(directory, MANIFEST)
  if (existsSync(candidate)) {
    return candidate
  }
  const parent: string = path.dirname(directory)
  if (parent === directory) {
    throw new Error(`no package.json found above ${directory}`)
  }
  return manifestFrom(parent)
}

const manifestBeside = (entry: string): string => manifestFrom(path.dirname(entry))

const manifestOf = (packageName: string): string => {
  try {
    return nodeRequire.resolve(`${packageName}/package.json`)
  } catch {
    // fall through
  }
  try {
    return manifestBeside(nodeRequire.resolve(packageName))
  } catch {
    // fall through
  }
  try {
    return manifestBeside(fileURLToPath(import.meta.resolve(packageName)))
  } catch (error: unknown) {
    throw new Error(`airness-node could not locate the manifest of ${packageName}`, { cause: error })
  }
}

/**
 * The directory of a package the harness depends on, as the harness resolves it.
 * @param packageName the npm package.
 * @returns the package's own directory.
 */
export const packageDirectory = (packageName: string): string => path.dirname(manifestOf(packageName))

/** This package's own directory, where the shipped configurations live. */
export const harnessDirectory = (): string => path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * Resolve an executable script from a package the harness depends on, independent of hoisting. pnpm
 * keeps a package's own dependencies out of the consumer's `.bin`, so reading the tool's manifest finds
 * its entry point wherever the store put it.
 * @param packageName the npm package providing the tool.
 * @param binName the bin entry to resolve, defaulting to the package name.
 * @returns the absolute path to the tool's JavaScript entry point.
 */
export const resolveTool = (packageName: string, binName?: string): string => {
  const manifestPath: string = manifestOf(packageName)
  const read: ParsedJson = parseJsonc(readFileSync(manifestPath, 'utf8'))
  if (read.problem !== undefined) {
    throw new Error(`airness-node could not read ${manifestPath}: ${read.problem}`)
  }
  const declaredBin: unknown = readKey(read.value, 'bin')
  const wanted: string = binName ?? packageName
  const bin: string | undefined = typeof declaredBin === 'string' ? declaredBin : asStringRecord(declaredBin)[wanted]
  if (bin === undefined) {
    throw new Error(`airness-node could not resolve the "${wanted}" executable from ${packageName}`)
  }
  return path.join(path.dirname(manifestPath), bin)
}

/**
 * The version a package the harness depends on resolved to.
 * @param packageName the npm package.
 * @returns its version, or an empty string when its manifest names none.
 */
export const resolvedVersion = (packageName: string): string => {
  const version: unknown = readKey(readJson(manifestOf(packageName)), 'version')
  return typeof version === 'string' ? version : ''
}
