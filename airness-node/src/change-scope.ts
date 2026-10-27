// What a change is, read from Git, and what it reaches, read from the import graph, handed to the scope
// policy in governance. Nothing here decides anything: which baseline counts, which file is global and
// which page a module reaches are `sweepScopeOf`'s, measured by its own specs.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import {
  type Baseline,
  baselineRemote,
  type ChangedFile,
  changeBaseline,
  type SweepScope,
  sweepScopeOf,
  trackingBranch,
} from './change-scope-policy.js'
import { failed, type GateResult, type RunResult, run } from './exec.js'
import { isNextApp } from './facets.js'
import { importGraph } from './gate-architecture.js'
import { asRecord, declaredDependencies } from './json-shapes.js'
import { type Member, membersOf } from './project.js'
import { appRootOf } from './route-map.js'
import { untrackedWorkingTreeFiles, workingTreeFiles } from './working-tree.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

interface Repository {
  readonly root: string
  readonly members: readonly Member[]
}

/** Read every reactor node root Maven declared, preserving paths relative to the Git baseline. */
const repositoryOf = (member: Member): Repository => {
  const root: string =
    process.env['AIRNESS_REPOSITORY_ROOT'] ??
    gitAnswer(member.root, ['rev-parse', '--show-toplevel']) ??
    member.projectRoot
  const roots: readonly string[] = (process.env['AIRNESS_NODE_ROOTS'] ?? member.projectRoot)
    .split(path.delimiter)
    .filter((entry: string): boolean => entry.length > 0)
  const members: readonly Member[] = roots
    .flatMap((directory: string): readonly Member[] => membersOf(directory))
    .map(
      (candidate: Member): Member => ({
        ...candidate,
        path: path.relative(root, candidate.root).split(path.sep).join('/') || ROOT_MEMBER_PATH,
      }),
    )
  return { root, members }
}

// Git's answer, or undefined when it has none: a missing ref and a failed merge-base are facts the
// policy names, not errors.
const gitAnswer = (root: string, commandArguments: readonly string[]): string | undefined => {
  const result: RunResult = run('git', commandArguments, { cwd: root })
  const answer: string = result.stdout.trim()
  return result.code === 0 && answer.length > 0 ? answer : undefined
}

const remoteHead = (root: string, remote: string): string | undefined =>
  gitAnswer(root, ['symbolic-ref', '--quiet', `refs/remotes/${remote}/HEAD`])

const existingBranch = (root: string, branch: string): string | undefined =>
  gitAnswer(root, ['rev-parse', '--verify', '--quiet', `refs/remotes/${branch}`]) === undefined ? undefined : branch

const baselineOf = (root: string): Baseline => {
  const remote: string | undefined = baselineRemote((gitAnswer(root, ['remote']) ?? '').split('\n'))
  const branch: string | undefined =
    remote === undefined || remote === ''
      ? undefined
      : existingBranch(root, trackingBranch(remote, remoteHead(root, remote)))
  return changeBaseline({
    branch,
    isShallow: gitAnswer(root, ['rev-parse', '--is-shallow-repository']) === 'true',
    mergeBase: branch === undefined ? undefined : gitAnswer(root, ['merge-base', 'HEAD', `refs/remotes/${branch}`]),
  })
}

const DELETED: string = 'D'

// `--name-status -z` writes a status and a path per file, each ended by a NUL.
const FIELDS_PER_FILE: number = 2

const statusEntries = (fields: readonly string[]): readonly ChangedFile[] =>
  Array.from({ length: Math.floor(fields.length / FIELDS_PER_FILE) }, (_unused: unknown, index: number) => ({
    path: fields[index * FIELDS_PER_FILE + 1] ?? '',
    isDeleted: fields[index * FIELDS_PER_FILE] === DELETED,
  }))

// `git diff <commit>` compares the commit with the working tree, so it holds every commit since the
// baseline and every staged and unstaged edit at once; renames are split into a deletion and an
// addition, so both paths count. `--relative` reports paths from the repository root, as the untracked
// listing does, even where that root sits below Git's own.
const changedSince = (root: string, baseline: string): readonly ChangedFile[] | GateResult => {
  const result: RunResult = run('git', ['diff', '--name-status', '--no-renames', '--relative', '-z', baseline, '--'], {
    cwd: root,
  })
  if (result.code !== 0) {
    return failed('the change since the baseline could not be read', [result.output])
  }
  return [
    ...statusEntries(result.stdout.split('\0').filter((field: string): boolean => field !== '')),
    ...untrackedWorkingTreeFiles(root).map((file: string): ChangedFile => ({ path: file, isDeleted: false })),
  ]
}

const repositoryPath = (member: Member, relative: string): string =>
  path.posix.normalize(member.path === ROOT_MEMBER_PATH ? relative : `${member.path}/${relative}`)

// The members whose change reaches this application without an import: each it starts as an auxiliary
// server from that member's directory, and each it declares as a dependency.
const directRelated = (repository: Repository, member: Member): readonly Member[] => {
  const servers: ReadonlySet<string> = new Set<string>(
    member.settings.auxiliaryServers.flatMap((server): readonly string[] =>
      server.cwd === undefined ? [] : [repositoryPath(member, server.cwd)],
    ),
  )
  const dependencies: ReadonlySet<string> = new Set<string>(Object.keys(declaredDependencies(member.packageJson)))
  return repository.members.filter((other: Member): boolean => {
    const name: unknown = asRecord(other.packageJson)['name']
    return (
      other.path !== member.path && (servers.has(other.path) || (typeof name === 'string' && dependencies.has(name)))
    )
  })
}

// A workspace dependency can itself depend on another member. Follow the closure, so a backend or
// library behind that intermediate package cannot disappear from the application's change scope.
const relatedClosure = (
  repository: Repository,
  frontier: readonly Member[],
  seen: ReadonlySet<string>,
): ReadonlySet<string> => {
  const fresh: readonly Member[] = frontier.filter((member: Member): boolean => !seen.has(member.path))
  if (fresh.length === 0) {
    return seen
  }
  const visited: ReadonlySet<string> = new Set([...seen, ...fresh.map((member: Member): string => member.path)])
  const next: readonly Member[] = fresh.flatMap((member: Member): readonly Member[] =>
    directRelated(repository, member),
  )
  return relatedClosure(repository, next, visited)
}

const relatedMembers = (repository: Repository, member: Member): readonly string[] =>
  [...relatedClosure(repository, directRelated(repository, member), new Set([member.path]))].filter(
    (relative: string): boolean => relative !== member.path,
  )

const isFailure = (value: object): value is GateResult => 'ok' in value && value.ok === false

/**
 * The scope a `delta` run hands one application's browser sweep.
 * @param original the application within its declared node project.
 * @returns the scope, or the failure that kept it from being read.
 */
export const sweepScopeFor = (original: Member): SweepScope | GateResult => {
  const repository: Repository = repositoryOf(original)
  const member: Member = {
    ...original,
    path: path.relative(repository.root, original.root).split(path.sep).join('/') || ROOT_MEMBER_PATH,
  }
  const baseline: Baseline = baselineOf(repository.root)
  if (baseline.commit === undefined) {
    return sweepScopeOf({
      baseline,
      changed: [],
      member: member.path,
      sourceRoots: member.settings.sourceRoots,
      members: [],
      related: [],
      files: [],
      graph: {},
    })
  }
  const changed: readonly ChangedFile[] | GateResult = changedSince(repository.root, baseline.commit)
  if (isFailure(changed)) {
    return changed
  }
  const files: readonly string[] = workingTreeFiles(member.root)
  if (!isNextApp(member) || appRootOf(files) === undefined) {
    return {
      isComplete: true,
      baseline: baseline.commit,
      branch: baseline.branch,
      changed: changed.map((entry: ChangedFile): string => entry.path),
      reasons: ['the application has no supported static route mapping; every page is swept'],
    }
  }
  const graph: Readonly<Record<string, readonly string[]>> | GateResult = importGraph(member)
  if (isFailure(graph)) {
    return graph
  }
  return sweepScopeOf({
    baseline,
    changed,
    member: member.path,
    sourceRoots: member.settings.sourceRoots,
    members: repository.members.map((other: Member): string => other.path),
    related: relatedMembers(repository, member),
    files,
    graph,
  })
}

/**
 * Whether a scope could not be read.
 * @param scope what `sweepScopeFor` answered.
 * @returns true for the failure.
 */
export const isScopeFailure = (scope: SweepScope | GateResult): scope is GateResult => isFailure(scope)

/**
 * Write a scope where the browser sweep reads it for the length of one run, outside the working tree.
 * @param scope the scope.
 * @param use what runs while the file exists.
 * @returns what `use` returned.
 */
export const withScopeFile = async <Result>(
  scope: SweepScope,
  use: (file: string) => Promise<Result>,
): Promise<Result> => {
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-scope-'))
  const file: string = path.join(directory, 'scope.json')
  writeFileSync(file, `${JSON.stringify(scope)}\n`)
  try {
    return await use(file)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}
