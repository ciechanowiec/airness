// The dependency gates against real projects in temporary working trees: the lockfile, the virtual store
// and the workspace file they read, and a registry on a local port for the release-age floor.
import { readFileSync } from 'node:fs'
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'
import type { AddressInfo } from 'node:net'
import path from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { installScripts } from '@/gate-install-scripts.js'
import { npmInventory } from '@/gate-npm-inventory.js'
import { npmSbom } from '@/gate-npm-sbom.js'
import { releaseAge } from '@/gate-release-age.js'
import type { GateResult } from '@/gate-result.js'
import { wiring } from '@/gate-wiring.js'
import type { Member } from '@/project.js'
import { installIntoStore, type StoredPackage } from './helpers/package-store.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const NOW: number = Date.parse('2026-10-24T12:00:00Z')
const HOUR_MS: number = 3_600_000

/**
 * A lockfile whose root importer declares the harness and resolves it with the given packages.
 * @param packages the package entries, each `name@version` with its resolution lines.
 * @returns the lockfile text.
 */
const lockfileWith = (packages: readonly string[]): string =>
  [
    "lockfileVersion: '9.0'",
    'importers:',
    '  .:',
    '    devDependencies:',
    "      '@airness/node':",
    '        specifier: 2.0.0',
    '        version: 2.0.0',
    '      aged:',
    '        specifier: 1.0.0',
    '        version: 1.0.0',
    'packages:',
    "  '@airness/node@2.0.0':",
    '    resolution: {integrity: sha512-abc}',
    ...packages,
    '',
  ].join('\n')

const AGED: string = '  aged@1.0.0:\n    resolution: {integrity: sha512-def}'
const LOCKFILE: string = lockfileWith([AGED])

const WORKSPACE: string = 'allowBuilds: {}\nminimumReleaseAge: 1440\nminimumReleaseAgeStrict: true\n'

// The harness and aged, as an install places them.
const INSTALLED: readonly StoredPackage[] = [
  { name: '@airness/node', version: '2.0.0', manifest: { license: 'MIT-0' } },
  { name: 'aged', version: '1.0.0', manifest: { license: 'MIT' } },
]

const withProject = async (
  files: Readonly<Record<string, string>>,
  use: (root: string) => Promise<void> | void,
): Promise<void> => {
  const root: string = temporaryProject({ 'package.json': '{"name":"shop"}\n', ...files })
  try {
    await use(root)
  } finally {
    removeProject(root)
  }
}

const nested = (root: string): Member => ({ ...memberAt(root), path: 'gate' })

const findingsOf = (result: GateResult): string => result.findings.join('\n')

describe('wiring', PROCESS_TIME, () => {
  it('reads the parent versions airness:node-gate supplies, and fails a harness at another version', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE }, (root: string): void => {
      process.env['AIRNESS_HARNESS_VERSIONS'] = '@airness/node=2.0.0'
      expect(wiring(memberAt(root)).findings).toEqual([])
      process.env['AIRNESS_HARNESS_VERSIONS'] = '@airness/node=2.0.1'
      expect(findingsOf(wiring(memberAt(root)))).toContain('resolves @airness/node 2.0.0')
      delete process.env['AIRNESS_HARNESS_VERSIONS']
    })
  })

  it('fails a project without a lockfile', async () => {
    await withProject({}, (root: string): void => {
      expect(findingsOf(wiring(memberAt(root)))).toContain('pnpm-lock.yaml is missing; run pnpm install to write it')
    })
  })

  it('refuses an override, a patch, an extension and a pnpmfile wherever the project configures one', async () => {
    const manifest: string = JSON.stringify({ name: 'shop', pnpm: { patchedDependencies: {} }, resolutions: {} })
    const files: Readonly<Record<string, string>> = {
      'package.json': `${manifest}\n`,
      'pnpm-lock.yaml': LOCKFILE.concat('overrides:\n  knip: npm:empty-npm-package@1.0.0\n'),
      'pnpm-workspace.yaml': 'overrides:\n  knip: npm:empty-npm-package@1.0.0\npackageExtensions: {}\n',
      '.npmrc': 'pnpmfile=hooks.cjs\n',
      '.pnpmfile.cjs': 'module.exports = {}\n',
    }
    await withProject(files, (root: string): void => {
      process.env['AIRNESS_HARNESS_VERSIONS'] = '@airness/node=2.0.0'
      const findings: string = findingsOf(wiring(memberAt(root)))
      delete process.env['AIRNESS_HARNESS_VERSIONS']
      for (const rewrite of [
        'pnpm-workspace.yaml override knip:',
        'pnpm-workspace.yaml packageExtensions:',
        'package.json resolutions:',
        'package.json pnpm.patchedDependencies:',
        '.npmrc pnpmfile:',
        '.pnpmfile.cjs: a pnpmfile hook',
        'pnpm-lock.yaml override knip:',
      ]) {
        expect(findings).toContain(rewrite)
      }
    })
  })
})

describe('install-scripts', PROCESS_TIME, () => {
  it('passes a record that decides the one package with an install script, at the project root only', async () => {
    const files: Readonly<Record<string, string>> = {
      'pnpm-lock.yaml': lockfileWith([AGED, '  esbuild@0.25.0:\n    resolution: {integrity: sha512-ghi}']),
      'pnpm-workspace.yaml': 'allowBuilds:\n  esbuild: true\n',
    }
    await withProject(files, (root: string): void => {
      installIntoStore(root, [
        ...INSTALLED,
        { name: 'esbuild', version: '0.25.0', manifest: { scripts: { postinstall: 'x' } } },
      ])
      expect(installScripts(memberAt(root)).ok).toBe(true)
      expect(installScripts(nested(root)).summary).toBe('the project root decides install scripts for every member')
    })
  })

  it('fails an undecided package, a missing install, and a project with nothing installed', async () => {
    const files: Readonly<Record<string, string>> = {
      'pnpm-lock.yaml': lockfileWith([AGED, '  esbuild@0.25.0:\n    resolution: {integrity: sha512-ghi}']),
      'pnpm-workspace.yaml': WORKSPACE,
    }
    await withProject(files, (root: string): void => {
      expect(findingsOf(installScripts(memberAt(root)))).toContain('airness:node-dependencies installs')
      installIntoStore(root, [{ name: 'esbuild', version: '0.25.0', manifest: { scripts: { install: 'x' } } }])
      const findings: string = findingsOf(installScripts(memberAt(root)))
      expect(findings).toContain('esbuild@0.25.0 runs an install script')
      expect(findings).toContain('aged@1.0.0 is resolved for this system but not installed')
    })
  })

  it('names the documents pnpm writes for config dependencies instead of reading the lockfile as missing', async () => {
    const files: Readonly<Record<string, string>> = {
      'pnpm-lock.yaml': `---\nimporters:\n  .: {}\n---\n${lockfileWith([AGED])}`,
      'pnpm-workspace.yaml': WORKSPACE,
    }
    await withProject(files, (root: string): void => {
      expect(findingsOf(installScripts(memberAt(root)))).toContain('pnpm-lock.yaml holds 2 YAML documents')
    })
  })

  it('keeps a decision about a package limited to other systems, and refuses a setting that moves the store', async () => {
    const native: string = '  native@1.0.0:\n    resolution: {integrity: sha512-jkl}\n    os: [aix]'
    const files: Readonly<Record<string, string>> = {
      'pnpm-lock.yaml': lockfileWith([AGED, native]),
      'pnpm-workspace.yaml': 'allowBuilds:\n  native: true\n',
    }
    await withProject(files, (root: string): void => {
      installIntoStore(root, INSTALLED)
      expect(installScripts(memberAt(root)).findings).toEqual([])
    })
    await withProject({ ...files, '.npmrc': 'node-linker=hoisted\n' }, (root: string): void => {
      installIntoStore(root, INSTALLED)
      expect(findingsOf(installScripts(memberAt(root)))).toContain('.npmrc node-linker: moves the store')
    })
  })
})

describe('npm-inventory', PROCESS_TIME, () => {
  it('writes the inventory of every resolved package beside the result and lists it as evidence', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE }, (root: string): void => {
      installIntoStore(root, INSTALLED)
      const result: GateResult = npmInventory(memberAt(root), { evidenceDirectory: root })
      expect(result.evidence).toEqual(['dependencies.json'])
      expect(JSON.parse(readFileSync(path.join(root, 'dependencies.json'), 'utf8'))).toEqual({
        contract: 1,
        packages: [
          { name: '@airness/node', version: '2.0.0', license: 'MIT-0', direct: true, installed: true },
          { name: 'aged', version: '1.0.0', license: 'MIT', direct: true, installed: true },
        ],
      })
      expect(npmInventory(memberAt(root), { evidenceDirectory: undefined }).evidence).toBeUndefined()
      expect(npmInventory(nested(root), { evidenceDirectory: root }).summary).toContain('project root')
    })
  })

  it('fails when nothing is installed, and when a package for every system is missing', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE }, (root: string): void => {
      expect(npmInventory(memberAt(root), { evidenceDirectory: root }).ok).toBe(false)
      installIntoStore(root, [{ name: 'aged', version: '1.0.0' }])
      expect(findingsOf(npmInventory(memberAt(root), { evidenceDirectory: root }))).toContain(
        '@airness/node@2.0.0 is resolved for this system but not installed',
      )
    })
  })
})

describe('npm-sbom', PROCESS_TIME, () => {
  it('writes the CycloneDX document of every resolved package beside the result and lists it as evidence', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE }, (root: string): void => {
      expect(npmSbom(memberAt(root, { name: 'shop' }), { evidenceDirectory: root }).ok).toBe(false)
      installIntoStore(root, INSTALLED)
      const result: GateResult = npmSbom(memberAt(root, { name: 'shop', version: '1.0.0' }), {
        evidenceDirectory: root,
      })
      expect(result.evidence).toEqual(['bom.cdx.json'])
      const document: unknown = JSON.parse(readFileSync(path.join(root, 'bom.cdx.json'), 'utf8'))
      expect(document).toMatchObject({
        bomFormat: 'CycloneDX',
        metadata: { component: { name: 'shop', version: '1.0.0' } },
      })
      expect(npmSbom(memberAt(root), { evidenceDirectory: undefined }).evidence).toBeUndefined()
      expect(npmSbom(nested(root), { evidenceDirectory: root }).summary).toContain('project root')
    })
  })

  it('fails when a package for every system is missing', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE }, (root: string): void => {
      installIntoStore(root, [{ name: 'aged', version: '1.0.0' }])
      expect(findingsOf(npmSbom(memberAt(root), { evidenceDirectory: root }))).toContain(
        '@airness/node@2.0.0 is resolved for this system but not installed',
      )
    })
  })
})

// The registry answers for `aged`, published a year before NOW, and for `fresh`, published an hour before.
const DOCUMENTS: Readonly<Record<string, unknown>> = {
  '/aged': { time: { '1.0.0': new Date(NOW - 365 * 24 * HOUR_MS).toISOString() } },
  '/fresh': { time: { '1.0.0': new Date(NOW - HOUR_MS).toISOString() } },
  '/@corp%2fui': { time: { '1.0.0': new Date(NOW - 365 * 24 * HOUR_MS).toISOString() } },
}

const NOT_FOUND: number = 404

const server: Server = createServer((request: IncomingMessage, response: ServerResponse): void => {
  const document: unknown = DOCUMENTS[request.url ?? '']
  response.writeHead(document === undefined ? NOT_FOUND : 200)
  response.end(JSON.stringify(document ?? {}))
})

const registry = (): string => `http://127.0.0.1:${String((server.address() as AddressInfo).port)}/`

// The npm configuration a run reads: the local registry for everything, and no user file.
const environment = (): NodeJS.ProcessEnv => ({
  pnpm_config_registry: registry(),
  npm_config_userconfig: '/nonexistent',
})

beforeAll(async (): Promise<void> => {
  await new Promise<void>((resolve: () => void): void => {
    server.listen(0, '127.0.0.1', resolve)
  })
})

afterAll(async (): Promise<void> => {
  await new Promise<void>((resolve: () => void): void => {
    server.close((): void => {
      resolve()
    })
  })
})

// The lockfile with the harness resolved some other way than from the registry.
const harnessFrom = (resolution: string): string =>
  lockfileWith([AGED]).replace('    resolution: {integrity: sha512-abc}', (): string => `    resolution: ${resolution}`)

describe('release-age', PROCESS_TIME, () => {
  it('passes releases older than the floor, read from the registry, and leaves the harness out', async () => {
    await withProject({ 'pnpm-lock.yaml': LOCKFILE, 'pnpm-workspace.yaml': WORKSPACE }, async (root: string) => {
      const result: GateResult = await releaseAge(memberAt(root), undefined, environment(), NOW)
      expect(result.findings).toEqual([])
      expect(result.summary).toContain('1 resolved release(s)')
      const delegated: GateResult = await releaseAge(nested(root), undefined, environment(), NOW)
      expect(delegated.ok).toBe(true)
    })
  })

  it('reads a scoped package from its scope registry, never from the default one', async () => {
    const scoped: string = "  '@corp/ui@1.0.0':\n    resolution: {integrity: sha512-mno}"
    // A scope's registry comes from an .npmrc, since pnpm reads none from the environment.
    const files: Readonly<Record<string, string>> = {
      'pnpm-lock.yaml': lockfileWith([scoped]),
      'pnpm-workspace.yaml': WORKSPACE,
      '.npmrc': `@corp:registry=${registry()}\n`,
    }
    await withProject(files, async (root: string) => {
      const defaultOnly: NodeJS.ProcessEnv = {
        pnpm_config_registry: 'http://127.0.0.1:9/',
        npm_config_userconfig: '/nonexistent',
      }
      const result: GateResult = await releaseAge(memberAt(root), undefined, defaultOnly, NOW)
      expect(result.findings).toEqual([])
    })
  })

  it('fails a young release, an undated resolution, and a release the registry cannot place, naming each', async () => {
    const lockfile: string = lockfileWith([
      '  fresh@1.0.0:\n    resolution: {integrity: sha512-ghi}',
      '  gone@1.0.0:\n    resolution: {integrity: sha512-jkl}',
      '  other@1.0.0:\n    resolution: {integrity: sha512-pqr}',
      '  forked@1.0.0:\n    resolution: {commit: abc, repo: https://example.invalid/forked.git, type: git}',
      '  remote@1.0.0:\n    resolution: {integrity: sha512-stu, tarball: https://example.invalid/remote-1.0.0.tgz}',
    ])
    await withProject({ 'pnpm-lock.yaml': lockfile, 'pnpm-workspace.yaml': WORKSPACE }, async (root: string) => {
      const findings: string = findingsOf(await releaseAge(memberAt(root), undefined, environment(), NOW))
      expect(findings).toContain('fresh@1.0.0 was published 1 hour(s) ago')
      expect(findings).toContain('could not read when 2 release(s) were published, gone@1.0.0 first')
      expect(findings).toContain('forked@1.0.0 resolves from a git, which no registry dates')
      expect(findings).toContain('remote@1.0.0 resolves from a tarball (https://example.invalid/remote-1.0.0.tgz)')
    })
  })

  it('exempts a harness package from the age floor only, never from coming from a registry', async () => {
    for (const resolution of [
      '{commit: abc, repo: https://example.invalid/node.git, type: git}',
      '{integrity: sha512-abc, tarball: https://example.invalid/node-2.0.0.tgz}',
    ]) {
      await withProject(
        { 'pnpm-lock.yaml': harnessFrom(resolution), 'pnpm-workspace.yaml': WORKSPACE },
        async (root) => {
          const findings: string = findingsOf(await releaseAge(memberAt(root), undefined, environment(), NOW))
          expect(findings).toContain('@airness/node@2.0.0 resolves from a ')
          expect(findings).toContain('which no registry dates')
        },
      )
    }
  })

  it('fails without a lockfile', async () => {
    await withProject({}, async (root: string) => {
      const result: GateResult = await releaseAge(memberAt(root), undefined, environment(), NOW)
      expect(result.ok).toBe(false)
      expect(findingsOf(result)).toContain('pnpm-lock.yaml is missing; run pnpm install to write it')
    })
  })
})
