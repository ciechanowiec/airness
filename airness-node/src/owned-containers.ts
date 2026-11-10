// The containers a run starts, each labelled with the run that owns it. A run that is killed outright, by
// SIGKILL, a timeout or a machine that went to sleep, cannot remove its containers, so every run first removes
// those whose owner is gone: the pinned browser server and the services a test wrapper starts alike. Only the
// owner decides, never a name, so concurrent runs on one machine leave each other's containers alone.
import { hostname } from 'node:os'
import { type RunResult, run } from './exec.js'

/** How the ownership checks run `ps` and `docker`, replaceable so their decisions can be measured. */
export type ProcessRunner = (command: string, commandArguments: readonly string[]) => RunResult

const runnerIn =
  (cwd: string): ProcessRunner =>
  (command: string, commandArguments: readonly string[]): RunResult =>
    run(command, commandArguments, { cwd })

/**
 * The run that started a container, recorded on it. A later run removes the container once this owner is gone.
 */
export interface ContainerOwner {
  readonly pid: number
  /** When the owner process started, as `ps` reports it, which a process that later reuses the PID does not share. */
  readonly started: string
  /** The machine the owner ran on: a PID means nothing on another machine sharing the Docker engine. */
  readonly host: string
}

/** The container labels that record a container's owner. */
const OWNER_LABELS: Readonly<Record<keyof ContainerOwner, string>> = {
  pid: 'airness.owner.pid',
  started: 'airness.owner.started',
  host: 'airness.owner.host',
}

const LISTING_SEPARATOR: string = '|'

/**
 * The `docker run` arguments that record an owner on the container they start.
 * @param owner the run starting it.
 * @returns one `--label` argument per field.
 */
export const ownerLabelArguments = (owner: ContainerOwner): readonly string[] => [
  `--label=${OWNER_LABELS.pid}=${String(owner.pid)}`,
  `--label=${OWNER_LABELS.started}=${owner.started}`,
  `--label=${OWNER_LABELS.host}=${owner.host}`,
]

/** The `docker` arguments that list every container recording its owner, one per line. */
const ownedContainerListingArguments: readonly string[] = [
  'ps',
  '--all',
  `--filter=label=${OWNER_LABELS.pid}`,
  `--format=${[
    '{{.Names}}',
    `{{.Label "${OWNER_LABELS.pid}"}}`,
    `{{.Label "${OWNER_LABELS.started}"}}`,
    `{{.Label "${OWNER_LABELS.host}"}}`,
  ].join(LISTING_SEPARATOR)}`,
]

/** A listed container, and its owner when the labels name one completely. */
interface ListedContainer {
  readonly name: string
  readonly owner: ContainerOwner | undefined
}

const ownerOf = (pid: string, started: string, host: string): ContainerOwner | undefined =>
  started !== '' && host !== '' && /^[1-9]\d*$/u.test(pid) ? { pid: Number(pid), started, host } : undefined

/**
 * Read the listing `ownedContainerListingArguments` produces.
 * @param listing what `docker` wrote.
 * @returns each listed container.
 */
const listedContainers = (listing: string): readonly ListedContainer[] =>
  listing
    .split('\n')
    .filter((line: string): boolean => line.trim() !== '')
    .map((line: string): ListedContainer => {
      const [name = '', pid = '', started = '', host = '']: readonly string[] = line.split(LISTING_SEPARATOR)
      return {
        name: name.trim(),
        owner: ownerOf(pid.trim(), started.trim(), host.trim()),
      }
    })

/**
 * Whether a container was left behind by a run that no longer exists. Only a container started on this machine
 * is judged, and its owner is gone when no process holds the PID or the one holding it started at another time.
 * @param owner the owner recorded on the container.
 * @param host this machine's name.
 * @param running when the process now holding the owner's PID started, or undefined when none does.
 * @returns true when the container may be removed.
 */
const isOrphanedContainer = (owner: ContainerOwner, host: string, running: string | undefined): boolean =>
  running !== owner.started && owner.host === host

/** Why a run could not take ownership of the containers it is about to start. */
export interface OwnershipFailure {
  readonly problem: string
  readonly details: readonly string[]
}

/**
 * Whether a claim failed.
 * @param claim what {@link claimOwnership} returned.
 * @returns true for a failure.
 */
export const isOwnershipFailure = (claim: ContainerOwner | OwnershipFailure): claim is OwnershipFailure =>
  'problem' in claim

// When a process started, as `ps` reports it; undefined when no process holds the PID.
const processStart = (execute: ProcessRunner, pid: number): string | undefined => {
  const result: RunResult = execute('ps', ['-o', 'lstart=', '-p', String(pid)])
  const started: string = result.output.trim().replaceAll(/\s+/gu, ' ')
  return started !== '' && result.code === 0 ? started : undefined
}

const isPresent = (execute: ProcessRunner, name: string): boolean =>
  execute('docker', ['container', 'inspect', name]).code === 0

// Another run may remove the same container at the same moment, which is why a failed removal only counts
// while the container is still there.
const removeOrphans = (execute: ProcessRunner, host: string): OwnershipFailure | undefined => {
  const listed: RunResult = execute('docker', ownedContainerListingArguments)
  if (listed.code !== 0) {
    return {
      problem: 'the containers earlier runs started could not be listed',
      details: [listed.output],
    }
  }
  const stuck: readonly string[] = listedContainers(listed.output)
    .filter(
      (container: ListedContainer): boolean =>
        container.owner !== undefined &&
        isOrphanedContainer(container.owner, host, processStart(execute, container.owner.pid)),
    )
    .filter(
      (container: ListedContainer): boolean =>
        execute('docker', ['rm', '--force', container.name]).code !== 0 && isPresent(execute, container.name),
    )
    .map((container: ListedContainer): string => container.name)
  return stuck.length === 0
    ? undefined
    : {
        problem: 'a container left by a run that no longer exists could not be removed',
        details: [`remove it with docker rm --force: ${stuck.join(', ')}`],
      }
}

/**
 * This process as the owner of the containers it is about to start, once the containers of runs that are gone
 * have been removed.
 * @param cwd the directory `ps` and `docker` run in.
 * @param execute how `ps` and `docker` are run, replaced only by tests.
 * @returns the owner to record, or why it could not be established.
 */
export const claimOwnership = (
  cwd: string,
  execute: ProcessRunner = runnerIn(cwd),
): ContainerOwner | OwnershipFailure => {
  const started: string | undefined = processStart(execute, process.pid)
  if (started === undefined) {
    return {
      problem: 'the run could not read its own start time, which marks the containers it starts',
      details: ['the harness reads it with `ps -o lstart= -p <pid>`; make `ps` available on the PATH'],
    }
  }
  const owner: ContainerOwner = { pid: process.pid, started, host: hostname() }
  return removeOrphans(execute, owner.host) ?? owner
}
