import { describe, expect, it } from 'vitest'
import { hasInstallScript, type InstalledPackage, installedPackages } from '@/installed-packages.js'
import { installIntoStore } from './helpers/package-store.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const withStore = (use: (root: string) => void): void => {
  const root: string = temporaryProject({ 'package.json': '{}\n' })
  try {
    use(root)
  } finally {
    removeProject(root)
  }
}

describe('installed packages', () => {
  it('reads each package from the virtual store once, scoped ones included, and passes over the links', () => {
    withStore((root: string): void => {
      installIntoStore(root, [
        { name: 'zeta', version: '1.0.0' },
        { name: '@scope/alpha', version: '2.0.0' },
      ])
      expect(
        installedPackages(root)?.map((installed: InstalledPackage): string => `${installed.name}@${installed.version}`),
      ).toEqual(['@scope/alpha@2.0.0', 'zeta@1.0.0'])
    })
  })

  it('reads nothing from a project with no virtual store', () => {
    withStore((root: string): void => {
      expect(installedPackages(root)).toBeUndefined()
    })
  })

  it('tells a package that runs an install script from one that does not', () => {
    withStore((root: string): void => {
      installIntoStore(root, [
        { name: 'scripted', version: '1.0.0', manifest: { scripts: { postinstall: 'node install.js' } } },
        { name: 'native', version: '1.0.0', files: { 'binding.gyp': '{}' } },
        { name: 'plain', version: '1.0.0', manifest: { scripts: { test: 'vitest' } } },
      ])
      const scripted: readonly string[] = (installedPackages(root) ?? [])
        .filter((installed: InstalledPackage): boolean => hasInstallScript(installed))
        .map((installed: InstalledPackage): string => installed.name)
      expect(scripted).toEqual(['native', 'scripted'])
    })
  })
})
