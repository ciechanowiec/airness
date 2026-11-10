// The facets: parts of the node layer that switch on by what a member is, never by an opt-in flag. A facet
// gate run on a member without its facet reports itself not applicable rather than passing.
import { hasExtension, JAVASCRIPT_EXTENSIONS, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import { declaredDependencies } from './json-shapes.js'
import type { Member } from './project.js'
import { type Settings, VITEST_CONFIG_FILE } from './settings.js'
import { matchesAnyGlob, memberFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'
import { hasOwnRuntime } from './workspace.js'

// The harness's pinned configurations, which every node project carries whatever else it holds. They are the
// harness's own text, so they are no sign that the member keeps scripts of its own.
const MANAGED_SCRIPT_FILES: ReadonlySet<string> = new Set<string>(['eslint.config.mjs', VITEST_CONFIG_FILE])

const SCRIPT_EXTENSIONS: readonly string[] = [...TYPESCRIPT_SOURCE_EXTENSIONS, ...JAVASCRIPT_EXTENSIONS]

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

/**
 * The member's own JavaScript and TypeScript: every script file it holds outside its nested members, except
 * the harness's pinned configurations and the files a generator owns. A stylesheet-only tooling project holds
 * none, wherever its stylesheets sit.
 * @param files the member-relative paths of the files a commit would carry.
 * @param settings the member's settings.
 * @param nestedMembers the members nested inside it, relative to it.
 * @returns the script sources, in the order given.
 */
const scriptSourcesOf = (
  files: readonly string[],
  settings: Settings,
  nestedMembers: readonly string[],
): readonly string[] =>
  memberFiles(files, nestedMembers).filter(
    (file: string): boolean =>
      hasExtension(file, SCRIPT_EXTENSIONS) &&
      !MANAGED_SCRIPT_FILES.has(file) &&
      !matchesAnyGlob(file, settings.generatedArtefacts),
  )

/**
 * Whether the member keeps script sources of its own. The gates that judge code report themselves not
 * applicable to a member without any, rather than failing on a tsconfig.json or a test it has no use for.
 * @param member the member.
 * @returns true when the member holds a script source.
 */
export const hasScriptSources = (member: Member): boolean =>
  scriptSourcesOf(presentWorkingTreeFiles(member.root), member.settings, member.siblingPaths).length > 0
