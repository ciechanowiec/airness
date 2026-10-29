import { describe, expect, it } from 'vitest'
import { findPinViolations, type InstalledPackage, pinnedVersionOf, REQUIRED_PINS } from '@/payload-pins.js'

const everyRequired: readonly string[] = Object.keys(REQUIRED_PINS)

const installedAtPins = (): readonly InstalledPackage[] =>
  everyRequired.map((name: string): InstalledPackage => ({ name, version: REQUIRED_PINS[name] }))

describe('pinnedVersionOf', () => {
  it('holds every first-party Payload package to the Payload version', () => {
    expect(pinnedVersionOf('@payloadcms/db-postgres')).toBe(REQUIRED_PINS['payload'])
  })

  it('pins the renderer and the type packages, and leaves every other package to the project', () => {
    expect(pinnedVersionOf('next')).toBe(REQUIRED_PINS['next'])
    expect(pinnedVersionOf('@types/react')).toBeDefined()
    expect(pinnedVersionOf('left-pad')).toBeUndefined()
  })
})

describe('findPinViolations', () => {
  it('passes an application that declares every pinned package at its pin', () => {
    expect(findPinViolations(everyRequired, installedAtPins())).toStrictEqual([])
  })

  it('names a required package the application does not declare', () => {
    const declared: readonly string[] = everyRequired.filter((name: string): boolean => name !== 'graphql')
    expect(findPinViolations(declared, installedAtPins())).toStrictEqual([
      `graphql is not declared; a Payload application declares it at exactly ${String(REQUIRED_PINS['graphql'])}`,
    ])
  })

  it('names a package at another version than the layer runs', () => {
    const installed: readonly InstalledPackage[] = [
      ...installedAtPins(),
      { name: '@payloadcms/db-postgres', version: '3.0.0' },
    ]
    expect(findPinViolations([...everyRequired, '@payloadcms/db-postgres'], installed)).toStrictEqual([
      `@payloadcms/db-postgres resolves to 3.0.0, but the Payload layer runs ${String(REQUIRED_PINS['payload'])}; ` +
        `declare exactly ${String(REQUIRED_PINS['payload'])}`,
    ])
  })

  it('names a declared package that is not installed', () => {
    const installed: readonly InstalledPackage[] = [
      ...installedAtPins().filter((entry: InstalledPackage): boolean => entry.name !== 'next'),
      { name: 'next', version: undefined },
    ]
    expect(findPinViolations(everyRequired, installed)).toStrictEqual([
      'next is declared but not installed; run pnpm install',
    ])
  })

  it('leaves a package the layer does not pin, or one the application does not declare, alone', () => {
    const installed: readonly InstalledPackage[] = [
      ...installedAtPins(),
      { name: 'left-pad', version: '1.0.0' },
      { name: '@types/react', version: '1.0.0' },
    ]
    expect(findPinViolations(everyRequired, installed)).toStrictEqual([])
  })
})
