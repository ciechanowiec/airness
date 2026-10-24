// The lint blocks of the Next.js and browser-application facets, which the ESLint configuration mounts only
// for a member that has the facet. A library carries neither React components nor a page to serve, so these
// rules would judge nothing there and their plugins would load for nothing.
import nextPlugin from '@next/eslint-plugin-next'
import playwright from 'eslint-plugin-playwright'
import { type FlatConfigBlock, withoutWarnings } from './eslint-core.js'

/** The end-to-end specs of a browser application, which Playwright runs rather than Vitest. */
export const END_TO_END_FILES: readonly string[] = ['tests/e2e/**/*.ts', 'tests/e2e/**/*.tsx']

/** The assertion helpers `@airness/node/a11y` ships, each of which fails the test it runs in on a finding. */
const END_TO_END_ASSERTION_HELPERS: readonly string[] = [
  'expectSweptPage',
  'expectNoLayoutDefects',
  'expectNoAxeDefects',
]

/**
 * The Next.js facet: React components need no return-type annotations, which add noise rather than safety,
 * and Next's Core Web Vitals preset is mounted whole, raised from warnings to errors. A vitals score moves
 * between two runs of an unchanged tree and is no gate; its causes, such as a raw `<img>` or a synchronous
 * `<script>`, are static, and the preset names exactly those. It judges `src/` only, since a test serves
 * nothing.
 */
export const nextBlocks: readonly FlatConfigBlock[] = [
  {
    files: ['src/**/*.tsx'],
    rules: {
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/explicit-module-boundary-types': 'off',
    },
  },
  {
    ...withoutWarnings(nextPlugin.configs['core-web-vitals']),
    files: ['src/**/*.ts', 'src/**/*.tsx'],
  },
]

/**
 * The browser-application facet: Playwright's own runner forbids a focused test, and every test must still
 * assert, which Playwright's rule decides by name: the helpers the harness ships, and a project's own helper
 * named `expect` and a capital, the convention those follow.
 */
export const endToEndBlocks: readonly FlatConfigBlock[] = [
  {
    files: [...END_TO_END_FILES],
    plugins: { playwright },
    rules: {
      'sonarjs/assertions-in-tests': 'off',
      'playwright/no-focused-test': 'error',
      'playwright/no-skipped-test': 'error',
      'playwright/expect-expect': [
        'error',
        { assertFunctionNames: [...END_TO_END_ASSERTION_HELPERS], assertFunctionPatterns: ['^expect[A-Z]'] },
      ],
    },
  },
]
