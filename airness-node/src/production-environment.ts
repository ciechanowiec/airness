// The production environment of a Next.js application, loaded into the Playwright runner before any spec
// is collected, through the application's own Next install so the precedence matches its build.
import { createRequire } from 'node:module'
import path from 'node:path'
import { readKey } from './json-shapes.js'

type EnvironmentLoader = (directory: string, isDevelopment: boolean) => unknown
const isLoader = (value: unknown): value is EnvironmentLoader => typeof value === 'function'

/**
 * Prepare spec imports with the same environment expansion and precedence as the installed Next build.
 * The caller runs with NODE_ENV=production, as the end-to-end gate requires.
 * @param root the application directory.
 */
export const loadProductionEnvironment = (root: string): void => {
  const requireProject: NodeJS.Require = createRequire(path.join(root, 'package.json'))
  const requireNext: NodeJS.Require = createRequire(requireProject.resolve('next/package.json'))
  const environmentModule: unknown = requireNext('@next/env')
  const load: unknown = readKey(environmentModule, 'loadEnvConfig')
  if (!isLoader(load)) {
    throw new TypeError('the installed Next production environment loader is unavailable')
  }
  load(root, false)
}
