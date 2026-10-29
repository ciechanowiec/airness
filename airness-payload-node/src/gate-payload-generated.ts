// The generated Payload artefacts must match the configuration that produces them: the gate regenerates the
// types and the admin import map with the project's own Payload CLI and fails on any drift.
import { spawnSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { failed, type GateResult, notApplicable, passed, withOutput } from '@airness/node/gate-result'
import { readKey } from '@airness/node/json-shapes'
import { findGeneratedDrift, type RegeneratedArtefact } from './generated-drift.js'
import { readJsonFile } from './json-file.js'
import { analysisEnvironment, PAYLOAD_PACKAGE, type PayloadMember } from './payload-member.js'
import { type RunResult, runNode } from './process-run.js'
import { asFindings } from './tool-findings.js'

/** The files the Payload CLI generates in a member that keeps Payload's layout. */
const PAYLOAD_GENERATED_FILES: readonly string[] = [
  'src/payload-types.ts',
  'src/payload-generated-schema.ts',
  'src/app/(payload)/admin/importMap.js',
]

// A path with no glob syntax, which names one file a generator writes.
const GLOB_SYNTAX: RegExp = /[*?[\]{}!]/u

/** The generators the Payload CLI runs, in order. */
const GENERATORS: readonly string[] = ['generate:types', 'generate:importmap']

// A configuration import opens no socket, but a plugin might, and a hung import must end as a verdict.
const GENERATOR_TIMEOUT_MS: number = 300_000

const readIfPresent = (root: string, target: string): string | undefined => {
  const full: string = path.join(root, target)
  return existsSync(full) ? readFileSync(full, 'utf8') : undefined
}

// Tracked or about to be: a file git does not know is a drift of its own, since a fresh checkout lacks it.
// Without git the comparison of the bytes answers alone.
const isTracked = (root: string, target: string): boolean => {
  const listed: ReturnType<typeof spawnSync> = spawnSync('git', ['ls-files', '--', target], {
    cwd: root,
    encoding: 'utf8',
  })
  return listed.status !== 0 || String(listed.stdout).trim().length > 0
}

/**
 * The Payload CLI the member installed.
 * @param root the member's directory.
 * @returns the absolute path of its entry point, or undefined when Payload is not installed there.
 */
const payloadCliOf = (root: string): string | undefined => {
  const directory: string = path.join(root, 'node_modules', PAYLOAD_PACKAGE)
  try {
    const bin: unknown = readKey(readJsonFile(path.join(directory, 'package.json')), 'bin')
    const entry: unknown = typeof bin === 'string' ? bin : readKey(bin, PAYLOAD_PACKAGE)
    return typeof entry === 'string' ? path.join(directory, entry) : undefined
  } catch {
    return undefined
  }
}

const regenerate = (target: PayloadMember, cli: string): RunResult | undefined => {
  for (const generator of GENERATORS) {
    const result: RunResult = runNode('--no-deprecation', [cli, generator], {
      cwd: target.member.root,
      env: analysisEnvironment(target),
      timeoutMs: GENERATOR_TIMEOUT_MS,
    })
    if (result.code !== 0) {
      return result
    }
  }
  return undefined
}

/**
 * Regenerate the Payload types and the admin import map, then fail on any drift.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadGenerated = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const cli: string | undefined = payloadCliOf(target.member.root)
  if (cli === undefined) {
    return failed('the Payload CLI could not be resolved from the member', [
      `${target.member.path}: "payload" is declared but not installed; run pnpm install`,
    ])
  }
  const root: string = target.member.root
  // Payload's own files, and every single file the member declares as generated, such as an import map it moved.
  const targets: readonly string[] = [
    ...new Set<string>([
      ...PAYLOAD_GENERATED_FILES,
      ...target.member.settings.generatedArtefacts.filter((file: string): boolean => !GLOB_SYNTAX.test(file)),
    ]),
  ]
  // Read before the generators run, because they overwrite in place: this is the only moment the previous
  // bytes exist.
  const before: ReadonlyMap<string, string | undefined> = new Map(
    targets.map((file: string): readonly [string, string | undefined] => [file, readIfPresent(root, file)]),
  )
  const failure: RunResult | undefined = regenerate(target, cli)
  if (failure !== undefined) {
    return withOutput(
      failed('the Payload CLI could not regenerate the artefacts', asFindings(failure.output)),
      failure.output,
    )
  }
  const regenerated: readonly RegeneratedArtefact[] = targets.map(
    (file: string): RegeneratedArtefact => ({
      target: file,
      isTracked: isTracked(root, file),
      before: before.get(file),
      after: readIfPresent(root, file),
    }),
  )
  const drifted: readonly string[] = findGeneratedDrift(regenerated)
  return drifted.length > 0
    ? failed('the generated Payload artefacts drifted from the configuration', [
        ...drifted,
        'commit the regenerated files',
      ])
    : passed('the generated Payload artefacts match the configuration')
}
