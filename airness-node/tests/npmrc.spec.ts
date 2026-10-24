import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { DEFAULT_REGISTRY, type NpmConfig, npmConfigOf, parseNpmrc, registryFor, tokenFor } from '@/npmrc.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

describe('the npm configuration', PROCESS_TIME, () => {
  it('reads keys and values, skips comments, and expands variables', () => {
    expect(
      parseNpmrc(
        ['# comment', '; other', 'registry = https://npm.corp', '//npm.corp/:_authToken=$' + '{TOKEN}'].join('\n'),
        {
          TOKEN: 'x',
        },
      ),
    ).toEqual(
      new Map([
        ['registry', 'https://npm.corp'],
        ['//npm.corp/:_authToken', 'x'],
      ]),
    )
  })

  it('lets the project file override the user file, and the environment override both', () => {
    const root: string = temporaryProject({
      '.npmrc': 'registry=https://project.invalid/\n@corp:registry=https://corp.invalid\n',
    })
    try {
      const user: string = path.join(root, 'user.npmrc')
      writeFileSync(user, 'registry=https://user.invalid/\n@other:registry=https://other.invalid/\n')
      const config: NpmConfig = npmConfigOf(root, { npm_config_userconfig: user, NPM_CONFIG_STRICT_SSL: 'true' })
      expect(registryFor(config, 'left-pad')).toBe('https://project.invalid/')
      expect(registryFor(config, '@corp/ui')).toBe('https://corp.invalid/')
      expect(registryFor(config, '@other/kit')).toBe('https://other.invalid/')
      expect(config.get('STRICT_SSL')).toBe('true')
      const fromEnvironment: NpmConfig = npmConfigOf(root, {
        npm_config_userconfig: user,
        npm_config_registry: 'https://env.invalid',
      })
      expect(registryFor(fromEnvironment, 'left-pad')).toBe('https://env.invalid/')
    } finally {
      removeProject(root)
    }
  })

  it('falls back to the public registry, and finds the token of the longest matching prefix', () => {
    const config: NpmConfig = parseNpmrc('//npm.corp/:_authToken=short\n//npm.corp/private/:_authToken=long\n', {})
    expect(registryFor(config, 'left-pad')).toBe(DEFAULT_REGISTRY)
    expect(tokenFor(config, 'https://npm.corp/private/')).toBe('long')
    expect(tokenFor(config, 'https://npm.corp/')).toBe('short')
    expect(tokenFor(config, 'https://registry.npmjs.org/')).toBeUndefined()
  })
})
