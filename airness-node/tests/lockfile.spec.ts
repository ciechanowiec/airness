import { describe, expect, it } from 'vitest'
import { type Lockfile, type ResolvedPackage, readLockfile, splitPackageKey } from '@/lockfile.js'

const LOCKFILE: string = [
  "lockfileVersion: '9.0'",
  'importers:',
  '  .:',
  '    devDependencies:',
  "      '@airness/node':",
  '        specifier: 2.0.0',
  '        version: 2.0.0',
  '      vitest:',
  '        specifier: 5.0.5',
  '        version: 5.0.5(@types/node@26.6.5)',
  '  gate:',
  '    dependencies:',
  '      local:',
  '        specifier: link:../local',
  '        version: link:../local',
  'packages:',
  "  '@airness/node@2.0.0':",
  '    resolution: {integrity: sha512-abc}',
  '  vitest@5.0.5:',
  '    resolution: {integrity: sha512-def}',
  '  remote@1.0.0:',
  '    resolution: {integrity: sha512-ghi, tarball: https://example.invalid/remote-1.0.0.tgz}',
  '  local@0.0.0:',
  '    resolution: {directory: ../local, type: directory}',
  '  forked@1.0.0:',
  '    resolution: {commit: abc, repo: https://example.invalid/forked.git, type: git}',
  '  native@1.0.0:',
  '    resolution: {integrity: sha512-jkl}',
  '    os: [linux]',
  '',
].join('\n')

describe('the lockfile', () => {
  it('splits a scoped key on its last at sign and drops the peers', () => {
    expect(splitPackageKey('@airness/node@2.0.0')).toEqual({ name: '@airness/node', version: '2.0.0' })
    expect(splitPackageKey('vitest@5.0.5(@types/node@26.6.5)')).toEqual({ name: 'vitest', version: '5.0.5' })
    expect(splitPackageKey('@scope/bare')).toEqual({ name: '@scope/bare', version: '' })
  })

  it('reads each importer with the version it resolved, without peers', () => {
    const read: Lockfile | undefined = readLockfile(LOCKFILE)
    expect(read?.importers.get('.')).toEqual([
      { name: '@airness/node', specifier: '2.0.0', version: '2.0.0' },
      { name: 'vitest', specifier: '5.0.5', version: '5.0.5' },
    ])
    expect(read?.importers.get('gate')).toEqual([
      { name: 'local', specifier: 'link:../local', version: 'link:../local' },
    ])
  })

  it('tells a registry package from a tarball and a directory', () => {
    const registry: Pick<ResolvedPackage, 'source' | 'tarball' | 'isPlatformLimited'> = {
      source: 'registry',
      tarball: '',
      isPlatformLimited: false,
    }
    expect(readLockfile(LOCKFILE)?.packages).toEqual([
      { name: '@airness/node', version: '2.0.0', ...registry },
      { name: 'vitest', version: '5.0.5', ...registry },
      {
        name: 'remote',
        version: '1.0.0',
        source: 'tarball',
        tarball: 'https://example.invalid/remote-1.0.0.tgz',
        isPlatformLimited: false,
      },
      { name: 'local', version: '0.0.0', source: 'directory', tarball: '', isPlatformLimited: false },
      { name: 'forked', version: '1.0.0', source: 'git', tarball: '', isPlatformLimited: false },
      { name: 'native', version: '1.0.0', source: 'registry', tarball: '', isPlatformLimited: true },
    ])
    expect(readLockfile(LOCKFILE)?.rewrites).toEqual([])
    expect(readLockfile(LOCKFILE)?.roots).toEqual(['@airness/node@2.0.0', 'vitest@5.0.5(@types/node@26.6.5)'])
  })

  it('names every install-time rewrite the lockfile records', () => {
    const rewritten: string = [
      LOCKFILE,
      'overrides:',
      '  knip: npm:empty-npm-package@1.0.0',
      'patchedDependencies:',
      "  '@airness/node': {hash: abc, path: patches/node.patch}",
      'pnpmfileChecksum: sha256-abc',
      'snapshots:',
      "  '@airness/node@2.0.0(patch_hash=abc)':",
      '    dependencies:',
      '      knip: 6.42.1',
      '',
    ].join('\n')
    expect(readLockfile(rewritten)?.rewrites).toEqual([
      'pnpm-lock.yaml records patchedDependencies',
      'pnpm-lock.yaml records pnpmfileChecksum',
      'pnpm-lock.yaml resolves the patched @airness/node',
    ])
    expect(readLockfile(rewritten)?.overrides).toEqual({ knip: 'npm:empty-npm-package@1.0.0' })
    expect(readLockfile(rewritten)?.edges.get('@airness/node@2.0.0(patch_hash=abc)')).toEqual(['knip@6.42.1'])
  })

  it('reads nothing from text that is no lockfile', () => {
    expect(readLockfile('importers: [\n')).toBeUndefined()
    expect(readLockfile('lockfileVersion: 9\n')).toBeUndefined()
  })
})
