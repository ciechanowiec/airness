// The facets: parts of the node layer that switch on by what a member is, never by an opt-in flag. A facet
// gate run on a member without its facet reports itself not applicable rather than passing.
import { declaredDependencies } from './json-shapes.js'
import type { Member } from './project.js'
import type { Settings } from './settings.js'
import { hasOwnRuntime } from './workspace.js'

// The dependency that makes a member a Tailwind project.
const TAILWIND_PACKAGE: string = 'tailwindcss'

const declaresPackage = (member: Member, name: string): boolean =>
  Object.hasOwn(declaredDependencies(member.packageJson), name)

/**
 * Whether a package is a browser application, read from its manifest and settings, for a tool that is started
 * in its directory rather than handed a member.
 * @param packageJson its parsed manifest.
 * @param settings its settings.
 * @returns true for a browser application.
 */
export const isBrowserAppAt = (packageJson: unknown, settings: Settings): boolean =>
  hasOwnRuntime(packageJson) || settings.serve !== undefined

/**
 * Whether the member is a Next.js application: it declares `next`.
 * @param member the member.
 * @returns true for a Next.js application.
 */
export const isNextApp = (member: Member): boolean => member.hasOwnRuntime

/**
 * Whether the member uses Tailwind: it declares `tailwindcss`.
 * @param member the member.
 * @returns true for a Tailwind project.
 */
export const isTailwindProject = (member: Member): boolean => declaresPackage(member, TAILWIND_PACKAGE)

/**
 * Whether the member is a browser application, as decision 12 of the approach names one: a Next.js application,
 * or a member that declares the `package.json` script that serves it. Nothing else is guessed at: a library
 * built with a bundler, or one carrying a demo page, is no application.
 * @param member the member.
 * @returns true for a browser application.
 */
export const isBrowserApp = (member: Member): boolean => isBrowserAppAt(member.packageJson, member.settings)
