import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { doesInstallPackages, isDockerfile, memberDockerfiles } from '@/dockerfiles.js'
import type { RunResult } from '@/exec.js'
import { image } from '@/gate-image.js'
import type { GateResult } from '@/gate-result.js'
import type { BootRequest, BootVerdict } from '@/image-boot.js'
import type { BootOutcome, ImageMachine } from '@/image-machine.js'
import type { Member } from '@/project.js'
import { readSettings } from '@/settings.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const answered = (stdout: string, code: number = 0): RunResult => ({
  code,
  output: stdout,
  stdout,
})

const SERVED_SETTINGS: Readonly<Record<string, unknown>> = {
  serve: 'serve',
  serverUrl: 'http://127.0.0.1:8080',
  serverUrlVariables: ['APP_ORIGIN'],
}

const SERVED: Readonly<Record<string, unknown>> = {
  airness: SERVED_SETTINGS,
  scripts: { serve: 'node server.ts' },
}

interface Recorded {
  readonly built: readonly string[]
  readonly removed: readonly string[]
  readonly boots: readonly BootRequest[]
  /** What the machine did to images, in order: `catalogue <tag>` and `remove <tag>`. */
  readonly events: readonly string[]
}

interface MachineOptions {
  readonly build?: (dockerfile: string) => RunResult
  readonly exposed?: RunResult
  readonly boot?: BootOutcome
  /** Whether a stop arrived before the gate began. */
  readonly stopped?: boolean
  /** The scanner image the machine names; the pinned one unless a test says it names none. */
  readonly scanner?: string | undefined
  readonly catalogue?: RunResult
}

interface Machine {
  readonly machine: ImageMachine
  readonly recorded: Recorded
}

const SCANNER: string = `aquasec/trivy:0.76.2@sha256:${'a'.repeat(64)}`

const LISTED_DOCUMENT: Readonly<Record<string, unknown>> = {
  bomFormat: 'CycloneDX',
  specVersion: '1.7',
  components: [],
}

const LISTED: string = JSON.stringify(LISTED_DOCUMENT)

const BOOTED: BootOutcome = {
  verdict: { ok: true, findings: [] },
  result: answered(''),
}

/* eslint-disable functional/immutable-data -- the stand-in machine records what the gate built, booted and removed, which is what the tests read */
const stand = (options: MachineOptions = {}): Machine => {
  const recorded: { built: string[]; removed: string[]; boots: BootRequest[]; events: string[] } = {
    built: [],
    removed: [],
    boots: [],
    events: [],
  }
  const machine: ImageMachine = {
    tag: (): string => `airness-image-${String(recorded.built.length)}:verification`,
    build: async (_member: Member, dockerfile: string, tag: string): Promise<RunResult> => {
      recorded.built.push(`${dockerfile}=${tag}`)
      await Promise.resolve()
      return options.build?.(dockerfile) ?? answered('built')
    },
    exposedPorts: (): RunResult => options.exposed ?? answered('{"3000/tcp":{}}\n'),
    remove: (tag: string): void => {
      recorded.removed.push(tag)
      recorded.events.push(`remove ${tag}`)
    },
    scannerImage: (): string | undefined => ('scanner' in options ? options.scanner : SCANNER),
    catalogue: (tag: string, scanner: string): RunResult => {
      recorded.events.push(`catalogue ${tag} with ${scanner}`)
      return options.catalogue ?? answered(LISTED)
    },
    boot: async (_member: Member, request: BootRequest): Promise<BootOutcome> => {
      recorded.boots.push(request)
      await Promise.resolve()
      return options.boot ?? BOOTED
    },
    holdStops: async <T>(body: (isStopped: () => boolean) => Promise<T>): Promise<T> =>
      await body((): boolean => options.stopped === true),
  }
  return { machine, recorded }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

const withEvidence = async (body: (directory: string) => Promise<void>): Promise<void> => {
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-image-evidence-'))
  try {
    await body(directory)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

const withProject = async (
  files: Readonly<Record<string, string>>,
  body: (root: string) => Promise<void>,
): Promise<void> => {
  const root: string = temporaryProject(files)
  try {
    await body(root)
  } finally {
    removeProject(root)
  }
}

const DOCKERFILE: string = 'FROM node:26-alpine\nEXPOSE 3000\n'

// An image whose build installs the member's packages from a registry.
const INSTALLING: string =
  'FROM node:26-alpine\nCOPY package.json pnpm-lock.yaml ./\nRUN corepack enable && pnpm install --frozen-lockfile\n' +
  'EXPOSE 3000\n'

describe('the Dockerfiles that build a member', PROCESS_TIME, () => {
  it.each([
    'RUN npm ci',
    'RUN npm install',
    'RUN npm i --omit=dev',
    'RUN pnpm install --frozen-lockfile',
    'RUN pnpm i',
    'RUN pnpm add left-pad',
    'RUN pnpm fetch',
    'RUN pnpm deploy --filter=web /out',
    'RUN yarn',
    'RUN yarn install --immutable',
    'RUN yarn add left-pad',
    'RUN bun install',
    'RUN bun i',
    'RUN bun add left-pad',
    'RUN corepack enable && pnpm install',
    'run npm ci',
    'RUN apk add --no-cache git \\\n    && npm ci',
  ])('reads %j as a build that installs packages', (instruction: string) => {
    expect(doesInstallPackages(`FROM node:26-alpine\n${instruction}\n`)).toBe(true)
  })

  it.each([
    'COPY package.json ./',
    'RUN node --version',
    'RUN apk add --no-cache git',
    '# RUN npm ci',
    'CMD ["npm", "start"]',
    'RUN npm run build',
    'RUN yarnpkg-checker --version',
  ])('reads %j as a build that installs no package', (instruction: string) => {
    expect(doesInstallPackages(`FROM node:26-alpine\n${instruction}\n`)).toBe(false)
  })

  it('recognises the three spellings of a Dockerfile', () => {
    expect(['Dockerfile', 'docker/Dockerfile.web', 'web.Dockerfile'].every(isDockerfile)).toBe(true)
    expect(isDockerfile('docker/compose.yaml')).toBe(false)
  })

  it('are those in its own directory, without those of a member nested in it', async () => {
    await withProject(
      {
        Dockerfile: DOCKERFILE,
        'deploy/web.Dockerfile': DOCKERFILE,
        'site/Dockerfile': DOCKERFILE,
      },
      async (root: string): Promise<void> => {
        expect(memberDockerfiles({ ...memberAt(root), siblingPaths: ['site'] })).toEqual([
          'deploy/web.Dockerfile',
          'Dockerfile',
        ])
        await Promise.resolve()
      },
    )
  })
})

describe('the image gate', PROCESS_TIME, () => {
  it('does not apply to a member without a Dockerfile', async () => {
    await withProject({ 'package.json': '{}' }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.isApplicable).toBe(false)
      expect(recorded.built).toEqual([])
    })
  })

  // A SNAPSHOT harness package is served only by the build's loopback registry, which no Docker build can reach.
  it.each([
    { block: 'devDependencies', name: '@airness/node' },
    { block: 'dependencies', name: '@airness/node' },
  ])('waits for a released harness while $block names $name at a SNAPSHOT version', async ({ block, name }) => {
    await withProject({ Dockerfile: INSTALLING }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(
        memberAt(root, {
          ...SERVED,
          [block]: { [name]: '9.8.8-SNAPSHOT' },
        }),
        undefined,
        machine,
      )
      expect(result.isApplicable).toBe(false)
      expect(result.summary).toContain(`${name}@9.8.8-SNAPSHOT`)
      expect(recorded.built).toEqual([])
    })
  })

  // An image that installs nothing reaches no registry, so a SNAPSHOT harness keeps nothing from building it.
  it('builds and boots an image that installs no package while the harness is a SNAPSHOT', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(
        memberAt(root, { ...SERVED, devDependencies: { '@airness/node': '9.8.8-SNAPSHOT' } }),
        undefined,
        machine,
      )
      expect(result.ok).toBe(true)
      expect(recorded.built).toEqual(['Dockerfile=airness-image-0:verification'])
    })
  })

  it('builds only the Dockerfiles that install no package while the harness is a SNAPSHOT, and names the rest', async () => {
    await withProject({ Dockerfile: INSTALLING, 'web.Dockerfile': DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(
        memberAt(root, { ...SERVED, devDependencies: { '@airness/node': '9.8.8-SNAPSHOT' } }),
        undefined,
        machine,
      )
      expect(result.ok).toBe(true)
      expect(recorded.built).toEqual(['web.Dockerfile=airness-image-0:verification'])
      expect(result.summary).toContain('Dockerfile installs packages, and is built once the harness is released')
    })
  })

  it.each([
    { what: 'a released harness', dependencies: { '@airness/node': '1.0.11' } },
    {
      what: 'a SNAPSHOT of a package that is not the harness',
      dependencies: { 'left-pad': '1.0.0-SNAPSHOT' },
    },
  ])('builds and boots the image of a member that installs $what', async ({ dependencies }) => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(
        memberAt(root, { ...SERVED, devDependencies: dependencies }),
        undefined,
        machine,
      )
      expect(result.ok).toBe(true)
      expect(recorded.built).toEqual(['Dockerfile=airness-image-0:verification'])
    })
  })

  it('builds and boots the image of a served member against empty services, and removes it', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.ok).toBe(true)
      expect(result.summary).toBe('every Dockerfile (Dockerfile) builds an image that boots against empty services')
      expect(recorded.boots).toEqual([
        {
          image: 'airness-image-0:verification',
          containerPort: 3000,
          serverUrl: 'http://127.0.0.1:8080',
          serverUrlVariables: ['APP_ORIGIN'],
          probes: ['/'],
        },
      ])
      expect(recorded.removed).toEqual(['airness-image-0:verification'])
    })
  })

  it('asks the paths a stack layer names beside the path of the declared origin', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const layered: Member = {
        ...memberAt(root, SERVED),
        settings: readSettings(
          {
            airness: {
              ...SERVED_SETTINGS,
              serverUrl: 'http://127.0.0.1:8080/site',
            },
          },
          [
            {
              generatedArtefacts: [],
              frameworkGlue: [],
              javascriptRuntimes: [],
              imageProbes: ['/admin', '/site'],
            },
          ],
        ),
      }
      await image(layered, undefined, machine)
      expect(recorded.boots[0]?.probes).toEqual(['/site', '/admin'])
    })
  })

  it('only builds the image of a member that serves nothing', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(memberAt(root), undefined, machine)
      expect(result.summary).toBe('every Dockerfile (Dockerfile) builds')
      expect(recorded.boots).toEqual([])
      expect(recorded.removed).toHaveLength(1)
    })
  })

  it('fails an image that does not build, with the end of what docker printed, and removes no image', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand({
        build: (): RunResult => answered('#7 [runner 3/6] COPY --from=builder /app/public ./public\nnot found', 1),
      })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.ok).toBe(false)
      expect(result.findings).toEqual([
        "Dockerfile: the image does not build from the node project's directory with no build argument and no " +
          'service (docker build exited 1):',
        '| #7 [runner 3/6] COPY --from=builder /app/public ./public',
        '| not found',
      ])
      expect(recorded.boots).toEqual([])
      expect(recorded.removed).toEqual([])
    })
  })

  it.each([
    ['null', 'exposes no TCP port'],
    ['{"53/udp":{}}', 'exposes no TCP port'],
    ['{"8080/tcp":{},"8081/tcp":{}}', 'exposes the TCP ports 8080, 8081'],
  ])('refuses to guess the port of an image whose ports are %s', async (exposed: string, finding: string) => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand({
        exposed: answered(exposed),
      })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.findings[0]).toContain(finding)
      expect(recorded.boots).toEqual([])
    })
  })

  it('fails an image that cannot be inspected', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine }: Machine = stand({
        exposed: answered('No such image', 1),
      })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.findings).toEqual(['Dockerfile: the built image could not be inspected: No such image'])
    })
  })

  it('reports what the boot found under the Dockerfile it judged', async () => {
    const verdict: BootVerdict = {
      ok: false,
      findings: ['GET /admin answered 500'],
    }
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine }: Machine = stand({
        boot: { verdict, result: answered('', 1) },
      })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.findings).toEqual([
        'Dockerfile: the image does not boot against empty services:',
        '  GET /admin answered 500',
      ])
    })
  })

  it('fails a boot the test wrapper never completed, with the end of its output', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine }: Machine = stand({
        boot: {
          verdict: undefined,
          result: answered('[services] postgres did not answer', 1),
        },
      })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, machine)
      expect(result.findings).toEqual([
        'Dockerfile: the image could not be booted, because the test wrapper did not complete the boot (exit code 1):',
        '| [services] postgres did not answer',
      ])
    })
  })

  it('judges every Dockerfile, and none once the gate was stopped', async () => {
    await withProject({ Dockerfile: DOCKERFILE, 'web.Dockerfile': DOCKERFILE }, async (root: string): Promise<void> => {
      const judged: Machine = stand()
      await image(memberAt(root, SERVED), undefined, judged.machine)
      expect(judged.recorded.built).toEqual([
        'Dockerfile=airness-image-0:verification',
        'web.Dockerfile=airness-image-1:verification',
      ])
      const stopped: Machine = stand({ stopped: true })
      const result: GateResult = await image(memberAt(root, SERVED), undefined, stopped.machine)
      expect(result.findings).toEqual(['Dockerfile: not judged, because the gate was stopped'])
      expect(stopped.recorded.built).toEqual([])
    })
  })
})

describe('the packages of the images the gate builds', PROCESS_TIME, () => {
  it('are listed beside the result for every Dockerfile, each before its image is removed', async () => {
    await withProject({ Dockerfile: DOCKERFILE, 'web.Dockerfile': DOCKERFILE }, async (root: string): Promise<void> => {
      await withEvidence(async (directory: string): Promise<void> => {
        const { machine, recorded }: Machine = stand()
        const result: GateResult = await image(memberAt(root), { evidenceDirectory: directory }, machine)
        expect(result.ok).toBe(true)
        expect(result.evidence).toEqual(['image-Dockerfile.json', 'image-web.Dockerfile.json'])
        expect(recorded.events).toEqual([
          `catalogue airness-image-0:verification with ${SCANNER}`,
          'remove airness-image-0:verification',
          `catalogue airness-image-1:verification with ${SCANNER}`,
          'remove airness-image-1:verification',
        ])
        const written: unknown = JSON.parse(readFileSync(path.join(directory, 'image-web.Dockerfile.json'), 'utf8'))
        expect(written).toEqual({ contract: 1, dockerfile: 'web.Dockerfile', sbom: LISTED_DOCUMENT })
      })
    })
  })

  it('are not listed by a run that writes no result, since nothing would read them', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      const { machine, recorded }: Machine = stand()
      const result: GateResult = await image(memberAt(root), undefined, machine)
      expect(result.ok).toBe(true)
      expect(result.evidence).toBeUndefined()
      expect(recorded.events).toEqual(['remove airness-image-0:verification'])
    })
  })

  it('fail an image whose packages cannot be listed, with what the scanner printed, and still remove it', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      await withEvidence(async (directory: string): Promise<void> => {
        const { machine, recorded }: Machine = stand({ catalogue: answered('FATAL no space left on device', 1) })
        const result: GateResult = await image(memberAt(root), { evidenceDirectory: directory }, machine)
        expect(result.ok).toBe(false)
        expect(result.findings).toEqual([
          'Dockerfile: the packages of the image could not be listed (exit code 1):',
          '| FATAL no space left on device',
        ])
        expect(result.evidence).toBeUndefined()
        expect(existsSync(path.join(directory, 'image-Dockerfile.json'))).toBe(false)
        expect(recorded.removed).toEqual(['airness-image-0:verification'])
      })
    })
  })

  it.each(['{"bomFormat": "SPDX"}', 'Unable to find image locally'])(
    'fail a list that is no CycloneDX document rather than write it: %j',
    async (printed: string) => {
      await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
        await withEvidence(async (directory: string): Promise<void> => {
          const { machine }: Machine = stand({ catalogue: answered(printed) })
          const result: GateResult = await image(memberAt(root), { evidenceDirectory: directory }, machine)
          expect(result.ok).toBe(false)
          expect(result.findings[0]).toBe('Dockerfile: the packages of the image could not be listed (exit code 0):')
          expect(existsSync(path.join(directory, 'image-Dockerfile.json'))).toBe(false)
        })
      })
    },
  )

  it('fail when no scanner image is named, naming the command that passes the pinned one', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      await withEvidence(async (directory: string): Promise<void> => {
        const { machine, recorded }: Machine = stand({ scanner: undefined })
        const result: GateResult = await image(memberAt(root), { evidenceDirectory: directory }, machine)
        expect(result.ok).toBe(false)
        expect(result.findings[0]).toContain('AIRNESS_TRIVY_IMAGE names no scanner image')
        expect(result.findings[0]).toContain('mvn verify -Pextended')
        expect(recorded.events).toEqual(['remove airness-image-0:verification'])
      })
    })
  })

  it('are written under a name unique to the member, naming the Dockerfile from the node project', async () => {
    await withProject({ Dockerfile: DOCKERFILE }, async (root: string): Promise<void> => {
      await withEvidence(async (directory: string): Promise<void> => {
        const { machine }: Machine = stand()
        const member: Member = { ...memberAt(root), path: 'apps/web' }
        const result: GateResult = await image(member, { evidenceDirectory: directory }, machine)
        expect(result.evidence).toEqual(['image-apps-web-Dockerfile.json'])
        const written: unknown = JSON.parse(
          readFileSync(path.join(directory, 'image-apps-web-Dockerfile.json'), 'utf8'),
        )
        expect(written).toEqual({ contract: 1, dockerfile: 'apps/web/Dockerfile', sbom: LISTED_DOCUMENT })
      })
    })
  })
})
