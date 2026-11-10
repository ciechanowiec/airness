import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type NpmConfig, npmConfigOf, registryFor, tokenFor } from '@/npmrc.js'
import { projectNpmConfig } from './helpers/npmrc-file.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { removeProject, temporaryProject } from './helpers/temporary-project.js'

describe('the npm configuration', PROCESS_TIME, () => {
  it('reads keys and values, skips comments, and expands variables', () => {
    expect(
      projectNpmConfig(
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
      const config: NpmConfig = npmConfigOf(root, { npm_config_userconfig: user, PNPM_CONFIG_STRICT_SSL: 'true' })
      expect(registryFor(config, 'left-pad')).toBe('https://project.invalid/')
      expect(registryFor(config, '@corp/ui')).toBe('https://corp.invalid/')
      expect(registryFor(config, '@other/kit')).toBe('https://other.invalid/')
      expect(config.get('strict-ssl')).toBe('true')
      const fromEnvironment: NpmConfig = npmConfigOf(root, {
        npm_config_userconfig: user,
        pnpm_config_registry: 'https://env.invalid',
      })
      expect(registryFor(fromEnvironment, 'left-pad')).toBe('https://env.invalid/')
    } finally {
      removeProject(root)
    }
  })

  it('reads the environment as pnpm 12 does, which is not as npm does', () => {
    const root: string = temporaryProject({ '.npmrc': 'registry=https://project.invalid/\n' })
    try {
      const user: string = path.join(root, 'user.npmrc')
      const other: string = path.join(root, 'other.npmrc')
      writeFileSync(user, '@user:registry=https://user.invalid/\n')
      writeFileSync(other, '@user:registry=https://other.invalid/\n')
      const config: NpmConfig = npmConfigOf(root, {
        npm_config_userconfig: user,
        pnpm_config_userconfig: other,
        npm_config_registry: 'https://ignored.invalid/',
        'pnpm_config_@corp:registry': 'https://ignored.invalid/',
        'npm_config_//npm.corp/:_authToken': 'npm',
        'npm_config_//npm.other/:_authToken': 'npm',
        'pnpm_config_//npm.other/:_authToken': 'pnpm',
        pnpm_config_node_linker: 'hoisted',
      })
      expect(registryFor(config, 'left-pad'), 'npm_config_registry changes nothing pnpm 12 does').toBe(
        'https://project.invalid/',
      )
      expect(registryFor(config, '@corp/ui'), 'nor does a scope registry in the environment').toBe(
        'https://project.invalid/',
      )
      expect(registryFor(config, '@user/kit'), 'pnpm_config_userconfig names the user file first').toBe(
        'https://other.invalid/',
      )
      expect(tokenFor(config, 'https://npm.corp/')).toBe('npm')
      expect(tokenFor(config, 'https://npm.other/')).toBe('pnpm')
      expect(config.get('node-linker')).toBe('hoisted')
    } finally {
      removeProject(root)
    }
  })

  it('falls back to the public registry, and finds the token of the longest matching prefix', () => {
    const config: NpmConfig = projectNpmConfig('//npm.corp/:_authToken=short\n//npm.corp/private/:_authToken=long\n')
    expect(registryFor(config, 'left-pad')).toBe('https://registry.npmjs.org/')
    expect(tokenFor(config, 'https://npm.corp/private/')).toBe('long')
    expect(tokenFor(config, 'https://npm.corp/')).toBe('short')
    expect(tokenFor(config, 'https://registry.npmjs.org/')).toBeUndefined()
  })
})
