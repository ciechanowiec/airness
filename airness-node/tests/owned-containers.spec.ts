/* eslint-disable functional/immutable-data -- one test's stand-in machine records the containers it is asked to remove */
import { hostname } from 'node:os'
import { describe, expect, it } from 'vitest'
import type { RunResult } from '@/exec.js'
import {
  type ContainerOwner,
  claimOwnership,
  isOwnershipFailure,
  type OwnershipFailure,
  ownerLabelArguments,
} from '@/owned-containers.js'

const OWNER: ContainerOwner = {
  pid: 4321,
  started: 'Mon Oct 26 09:52:44 2026',
  host: 'builder',
}

describe('the owner a run records on its containers', () => {
  it('is written as labels', () => {
    expect(ownerLabelArguments(OWNER)).toEqual([
      '--label=airness.owner.pid=4321',
      '--label=airness.owner.started=Mon Oct 26 09:52:44 2026',
      '--label=airness.owner.host=builder',
    ])
  })
})

const answered = (stdout: string, code: number = 0): RunResult => ({ code, output: stdout, stdout })

// A machine on which this process started at RUN_START, one other live run owns `airness-browsers-1`, and
// the runs that owned the other two are gone. `removable` names the containers `docker rm` can remove, and
// `present` those `docker container inspect` still finds.
const RUN_START: string = 'Sun Nov  1 07:00:00 2026'

// 300 was given to another process after the run that owned `dam-cd-objects` ended.
const STARTS: Readonly<Record<string, string>> = {
  [String(process.pid)]: RUN_START,
  '100': 'Mon Oct 26 09:52:44 2026',
  '300': 'Mon Oct 26 10:01:07 2026',
}

const processStartOf = (pid: string): RunResult => answerIf(STARTS[pid] !== undefined, STARTS[pid] ?? '')

const answerIf = (isAnswered: boolean, stdout: string): RunResult => (isAnswered ? answered(stdout) : answered('', 1))

const machine =
  (removable: readonly string[], listing: RunResult, present: readonly string[] = []) =>
  (command: string, commandArguments: readonly string[]): RunResult => {
    const last: string = commandArguments.at(-1) ?? ''
    if (command === 'ps') {
      return processStartOf(last)
    }
    const answers: Readonly<Record<string, RunResult>> = {
      ps: listing,
      rm: answerIf(removable.includes(last), ''),
      container: answerIf(present.includes(last), '[]'),
    }
    return answers[commandArguments[0] ?? ''] ?? answered('', 1)
  }

const host: string = hostname()
const LISTING: RunResult = answered(
  [
    `airness-browsers-1|100|Mon Oct 26 09:52:44 2026|${host}`,
    `teron-ab-db|200|Mon Oct 26 09:52:44 2026|${host}`,
    `dam-cd-objects|300|Mon Oct 26 09:52:44 2026|${host}`,
    'elsewhere|400|Mon Oct 26 09:52:44 2026|another-machine',
  ].join('\n'),
)

/** A claim on a machine, the containers it removed, and the `docker` calls it made. */
interface Claimed {
  readonly claim: ContainerOwner | OwnershipFailure
  readonly removed: readonly string[]
  readonly docker: readonly (readonly string[])[]
}

const claimed = (listing: RunResult, removable: readonly string[]): Claimed => {
  const removed: string[] = []
  const docker: (readonly string[])[] = []
  const claim: ContainerOwner | OwnershipFailure = claimOwnership(
    '.',
    (command: string, commandArguments: readonly string[]): RunResult => {
      if (command === 'docker') {
        docker.push(commandArguments)
      }
      if (commandArguments[0] === 'rm') {
        removed.push(commandArguments.at(-1) ?? '')
      }
      return machine(removable, listing)(command, commandArguments)
    },
  )
  return { claim, removed, docker }
}

describe('a run claiming the containers it starts', () => {
  it('removes the containers of runs that are gone and keeps those of live runs and other machines', () => {
    const { claim, removed }: Claimed = claimed(LISTING, ['teron-ab-db', 'dam-cd-objects'])
    // `ps` pads a one-digit day; the owner records it with single spaces, as every later `ps` is read.
    expect(claim).toEqual({ pid: process.pid, started: 'Sun Nov 1 07:00:00 2026', host })
    // teron-ab-db's owner is gone, and dam-cd-objects' PID is held by a process that started at another time.
    expect(removed).toEqual(['teron-ab-db', 'dam-cd-objects'])
  })

  it('lists only the containers that record an owner, and reads their owners back from the labels', () => {
    const { removed, docker }: Claimed = claimed(
      answered(
        [
          ` airness-browsers-4100 | 4321 |Mon Oct 26 09:52:44 2026|${host}`,
          '',
          `airness-browsers-4200||Mon Oct 26 09:52:44 2026|${host}`,
          `airness-browsers-4300|0|Mon Oct 26 09:52:44 2026|${host}`,
          `airness-browsers-4400|4400||${host}`,
          'airness-browsers-4500|4500|Mon Oct 26 09:52:44 2026|',
        ].join('\n'),
      ),
      ['airness-browsers-4100'],
    )
    expect(docker[0]).toContain('--filter=label=airness.owner.pid')
    expect(docker[0]?.at(-1)).toBe(
      '--format={{.Names}}|{{.Label "airness.owner.pid"}}|{{.Label "airness.owner.started"}}|{{.Label "airness.owner.host"}}',
    )
    // Only the container whose labels name an owner completely is judged, and its owner is gone.
    expect(removed).toEqual(['airness-browsers-4100'])
  })

  it('counts a removal another run made at the same moment as done', () => {
    const claim: ContainerOwner | OwnershipFailure = claimOwnership('.', machine([], LISTING))
    expect(isOwnershipFailure(claim)).toBe(false)
  })

  it('fails naming a container it could not remove that is still there', () => {
    const claim: ContainerOwner | OwnershipFailure = claimOwnership('.', machine([], LISTING, ['dam-cd-objects']))
    expect(isOwnershipFailure(claim) && claim.details).toEqual(['remove it with docker rm --force: dam-cd-objects'])
  })

  it('fails when the containers cannot be listed, or its own start time cannot be read', () => {
    const unlisted: ContainerOwner | OwnershipFailure = claimOwnership('.', machine([], answered('no docker', 1)))
    expect(isOwnershipFailure(unlisted) && unlisted.problem).toBe(
      'the containers earlier runs started could not be listed',
    )
    const unstarted: ContainerOwner | OwnershipFailure = claimOwnership('.', () => answered('', 1))
    expect(isOwnershipFailure(unstarted) && unstarted.problem).toContain('could not read its own start time')
  })
})
/* eslint-enable functional/immutable-data -- nothing after the stand-ins records anything */
