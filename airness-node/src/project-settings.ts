// The settings of the node project ESLint was started in, read from the working directory.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { isBrowserAppAt } from './facets.js'
import { layerDefaultsFor } from './layer.js'
import { readSettings, type Settings } from './settings.js'
import { hasOwnRuntime } from './workspace.js'

// A project whose package.json cannot be read gets the defaults, which are the strict end of every
// setting. Failing to parse must never be the thing that loosens a rule.
const readPackageJson = (directory: string): unknown => {
  try {
    return JSON.parse(readFileSync(path.join(directory, 'package.json'), 'utf8'))
  } catch {
    return {}
  }
}

/** The member whose settings these are: the directory its tools are invoked in. */
export const projectDirectory: string = process.cwd()

const packageJson: unknown = readPackageJson(projectDirectory)

/** The member's effective settings, with the defaults of every layer package it declares. */
export const projectSettings: Settings = readSettings(
  packageJson,
  layerDefaultsFor(projectDirectory, packageJson, (file: string): unknown => JSON.parse(readFileSync(file, 'utf8'))),
)

/** Whether the member is a Next.js application, which the Next.js facet's lint blocks apply to. */
export const isNextProject: boolean = hasOwnRuntime(packageJson)

/** Whether the member is a browser application, whose end-to-end specs are Playwright's. */
export const isBrowserProject: boolean = isBrowserAppAt(packageJson, projectSettings)
