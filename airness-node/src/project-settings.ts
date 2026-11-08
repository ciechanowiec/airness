// The settings of the node project ESLint was started in, read from the working directory.
import path from 'node:path'
import { servedSettings } from './browser-origin.js'
import { isBrowserAppAt } from './facets.js'
import { layerDefaultsFor } from './layer.js'
import { readJson } from './project.js'
import { readSettings, type Settings } from './settings.js'
import { hasOwnRuntime } from './workspace.js'

/** The member whose settings these are: the directory its tools are invoked in. */
export const projectDirectory: string = process.cwd()

// A project whose package.json is absent or unparseable gets the defaults, which are the strict end of every
// setting. Failing to parse must never be the thing that loosens a rule.
const packageJson: unknown = readJson(path.join(projectDirectory, 'package.json'))

/**
 * The member's effective settings, with the defaults of every layer package it declares, and in verification
 * with the addresses the e2e gate assigned to this run.
 */
export const projectSettings: Settings = servedSettings(
  readSettings(packageJson, layerDefaultsFor(projectDirectory, packageJson, readJson)),
  process.env,
)

/** Whether the member is a Next.js application, which the Next.js facet's lint blocks apply to. */
export const isNextProject: boolean = hasOwnRuntime(packageJson)

/** Whether the member is a browser application, whose end-to-end specs are Playwright's. */
export const isBrowserProject: boolean = isBrowserAppAt(packageJson, projectSettings)
