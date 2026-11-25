import { symlinkSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { packageNotices } from '@/package-notices.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

const withPackage = (files: Readonly<Record<string, string>>, use: (directory: string) => void): void => {
  const directory: string = temporaryProject(files)
  try {
    use(directory)
  } finally {
    removeProject(directory)
  }
}

describe('the notice files of an installed package', () => {
  it('reads every spelling of a license, notice and copying file, sorted by name', () => {
    withPackage(
      {
        'package.json': '{}\n',
        LICENSE: 'MIT terms\n',
        'LICENCE.md': '# Licence\n',
        'NOTICE.txt': 'Notice\n',
        COPYING: 'Copying\n',
        'license-mit.apache': 'Variant\n',
        'README.md': 'Readme\n',
        'LICENSE.json': '{}\n',
        'docs/LICENSE': 'Nested\n',
      },
      (directory: string): void => {
        expect(packageNotices(directory)).toEqual({
          COPYING: 'Copying\n',
          'LICENCE.md': '# Licence\n',
          LICENSE: 'MIT terms\n',
          'license-mit.apache': 'Variant\n',
          'NOTICE.txt': 'Notice\n',
        })
      },
    )
  })

  it('leaves out a link, a name that suggests a secret, an oversized file, invalid UTF-8 and a NUL', () => {
    withPackage({ 'package.json': '{}\n', 'target.txt': 'Linked\n', 'LICENSE-key.txt': 'Secret\n' }, (directory) => {
      symlinkSync(path.join(directory, 'target.txt'), path.join(directory, 'LICENSE'))
      writeFileSync(path.join(directory, 'NOTICE'), 'a'.repeat(262_145))
      writeFileSync(path.join(directory, 'COPYING'), Buffer.from([0xc3, 0x28]))
      writeFileSync(path.join(directory, 'LICENCE'), 'Before\0after\n')
      writeFileSync(path.join(directory, 'LICENSE.md'), 'a'.repeat(262_144))
      expect(Object.keys(packageNotices(directory))).toEqual(['LICENSE.md'])
    })
  })
})
