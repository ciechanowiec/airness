// Which Next.js production build the bundle and e2e gates may judge: only the one the build gate produced from
// the tree as it is now. The build gate records the build's id, its directory and the working-tree
// fingerprint after the build in a stamp under the member's node_modules; a later gate refuses a build with no
// stamp, a tree that changed since, or a build directory holding another build. Without it, a failed build in
// a report-only run, or a gate run by its execution id, would measure and serve whatever build was on disk.
import { existsSync, readFileSync, rmSync } from 'node:fs'
import path from 'node:path'
import { BUILD_DIRECTORY_MARKER, parseBuildDirectory } from './bundle-budget.js'
import { type RunResult, runNode } from './exec.js'
import { asRecord, asText } from './json-shapes.js'
import { type Member, writeJson } from './project.js'
import { workingTreeFingerprint } from './working-tree.js'

/** The variables every Next.js command the facet runs is started with. */
export const NEXT_ENVIRONMENT: Readonly<Record<string, string>> = {
  NEXT_TELEMETRY_DISABLED: '1',
  NODE_ENV: 'production',
  NODE_OPTIONS: `${process.env['NODE_OPTIONS'] ?? ''} --no-deprecation`.trim(),
}

const PROBE_TIMEOUT_MS: number = 120_000
const BUILD_ID_FILE: string = 'BUILD_ID'

// Loads the configuration the production server would, through the member's installed Next.
const PROBE: string = [
  "import { createRequire } from 'node:module'",
  "import path from 'node:path'",
  "const project = createRequire(path.join(process.cwd(), 'package.json'))",
  "const load = project('next/dist/server/config.js').default",
  "const config = await load('phase-production-server', process.cwd())",
  `console.log(${JSON.stringify(BUILD_DIRECTORY_MARKER)} + JSON.stringify(path.resolve(process.cwd(), config.distDir)))`,
].join('\n')

/** The build the stamp vouches for. */
export interface StampedBuild {
  readonly buildId: string
  readonly directory: string
  readonly fingerprint: string
}

const stampFile = (member: Member): string => path.join(member.root, 'node_modules', '.airness', 'next-build.json')

/**
 * The member's build directory, from its own Next.js configuration, read in a process of its own.
 * @param member the Next.js application.
 * @returns the absolute directory, or the reason it could not be read.
 */
export const buildDirectoryOf = (member: Member): { readonly directory: string } | { readonly problem: string } => {
  // Node flags before the script, as runNode passes them to the interpreter: the probe is a module.
  const probe: RunResult = runNode('--input-type=module', ['--eval', PROBE], {
    cwd: member.root,
    env: NEXT_ENVIRONMENT,
    timeoutMs: PROBE_TIMEOUT_MS,
  })
  const directory: string | undefined = probe.code === 0 ? parseBuildDirectory(probe.stdout) : undefined
  return directory !== undefined && path.isAbsolute(directory) ? { directory } : { problem: probe.output }
}

const buildIdIn = (directory: string): string | undefined => {
  const file: string = path.join(directory, BUILD_ID_FILE)
  return existsSync(file) ? readFileSync(file, 'utf8').trim() : undefined
}

/**
 * Forget the previous build, before the build gate builds again.
 * @param member the Next.js application.
 */
export const forgetBuild = (member: Member): void => {
  rmSync(stampFile(member), { force: true })
}

/**
 * Record the build the build gate just produced.
 * @param member the Next.js application.
 * @param directory its build directory.
 * @returns the reason the build could not be recorded, or undefined.
 */
export const stampBuild = (member: Member, directory: string): string | undefined => {
  const buildId: string | undefined = buildIdIn(directory)
  if (buildId === undefined) {
    return `${path.join(directory, BUILD_ID_FILE)} is missing after the build, so the build cannot be vouched for`
  }
  const stamp: StampedBuild = { buildId, directory, fingerprint: workingTreeFingerprint(member.root) }
  writeJson(stampFile(member), stamp)
  return undefined
}

const readStamp = (member: Member): StampedBuild | undefined => {
  if (!existsSync(stampFile(member))) {
    return undefined
  }
  const text: string = readFileSync(stampFile(member), 'utf8')
  const record: Record<string, unknown> = asRecord(JSON.parse(text))
  return {
    buildId: asText(record['buildId']),
    directory: asText(record['directory']),
    fingerprint: asText(record['fingerprint']),
  }
}

/**
 * The build the build gate produced from the tree as it is now.
 * @param member the Next.js application.
 * @returns the build, or the reason no build may be judged.
 */
export const currentBuild = (member: Member): StampedBuild | { readonly problem: string } => {
  const stamp: StampedBuild | undefined = readStamp(member)
  if (stamp === undefined) {
    return { problem: 'no build of this tree is recorded; the build gate, which runs before this one, records it' }
  }
  if (stamp.fingerprint !== workingTreeFingerprint(member.root)) {
    return { problem: 'the working tree changed since the build gate built it; run the build gate again' }
  }
  return buildIdIn(stamp.directory) === stamp.buildId
    ? stamp
    : { problem: `${stamp.directory} holds another build than the one the build gate recorded; build again` }
}
