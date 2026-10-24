// Where a spec file must live to be run. The shipped Vitest configuration collects the unit specs and the
// e2e gate's Playwright run collects the end-to-end specs of a browser application; a spec anywhere else is
// collected by neither, so it reads as coverage while asserting nothing.
import path from 'node:path'
import { JAVASCRIPT_EXTENSIONS, matchesGlob, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'

/** The unit specs the shipped Vitest configuration collects. */
export const UNIT_SPEC_GLOB: string = 'tests/**/*.spec.ts'

/** The end-to-end specs the e2e gate collects in a browser application, which Vitest leaves out. */
export const END_TO_END_SPEC_GLOB: string = 'tests/e2e/**'

const END_TO_END_SPEC: string = 'tests/e2e/**/*.e2e.spec.ts'

// A spec or test file by the names runners collect: `.spec.` or `.test.` before a code extension.
const isSpecFile = (file: string): boolean =>
  [...TYPESCRIPT_SOURCE_EXTENSIONS, ...JAVASCRIPT_EXTENSIONS].some((extension: string): boolean => {
    const name: string = path.posix.basename(file)
    return name.endsWith(`.spec${extension}`) || name.endsWith(`.test${extension}`)
  })

const isUnitSpec = (file: string): boolean =>
  matchesGlob(UNIT_SPEC_GLOB, file) && !matchesGlob(END_TO_END_SPEC_GLOB, file)

/**
 * Report every spec file no runner collects.
 * @param files the member-relative files a commit carries, nested members and generated files left out.
 * @param isBrowserApp whether the member is a browser application, whose end-to-end specs run.
 * @returns one finding per misplaced spec.
 */
export const misplacedSpecs = (files: readonly string[], isBrowserApp: boolean): readonly string[] =>
  files
    .filter((file: string): boolean => isSpecFile(file))
    .flatMap((file: string): readonly string[] => {
      if (isUnitSpec(file)) {
        return []
      }
      if (matchesGlob(END_TO_END_SPEC, file)) {
        return isBrowserApp
          ? []
          : [`${file} is an end-to-end spec in a member that is no browser application, so nothing runs it`]
      }
      return [
        `${file} is a spec no runner collects; unit specs are ${UNIT_SPEC_GLOB} and the end-to-end specs of a ` +
          `browser application ${END_TO_END_SPEC}`,
      ]
    })
