// The lint blocks of the Next.js and browser-application facets, which the ESLint configuration mounts only
// for a member that has the facet. A library carries neither React components nor a page to serve, so these
// rules would judge nothing there and their plugins would load for nothing.
import nextPlugin from '@next/eslint-plugin-next'
import playwright from 'eslint-plugin-playwright'
import testingLibrary from 'eslint-plugin-testing-library'
import {
  type FlatConfigBlock,
  NO_INHERITANCE,
  NO_NETWORK_GUARD_ESCAPE,
  testIdiomPlugins,
  testIdiomRules,
  withoutWarnings,
} from './eslint-core.js'
import { SECURITY_RESTRICTIONS } from './eslint-security.js'
import { TIME_ZONE_RESTRICTIONS } from './eslint-time-zone.js'

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
    files: ['next.config.{ts,js,mjs}'],
    // Next loads this declarative module to compose its configuration before starting the application.
    rules: { 'unicorn/no-top-level-side-effects': 'off' },
  },
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

/** User-facing DOM assertions for component and application unit tests, including database-backed suites. */
export const componentTestBlocks: readonly FlatConfigBlock[] = [
  {
    files: ['tests/{int,unit,component}/**/*.{ts,tsx}'],
    ignores: [...END_TO_END_FILES],
    plugins: { 'testing-library': testingLibrary },
    // A CMS client's findByID is a database call, not a custom Testing Library query.
    settings: { 'testing-library/custom-queries': 'off' },
    rules: {
      'testing-library/await-async-queries': 'error',
      'testing-library/await-async-utils': 'error',
      'testing-library/no-await-sync-queries': 'error',
      'testing-library/no-container': 'error',
      'testing-library/no-debugging-utils': 'error',
      'testing-library/no-dom-import': ['error', 'react'],
      'testing-library/no-node-access': 'error',
      'testing-library/no-render-in-lifecycle': 'error',
      'testing-library/prefer-screen-queries': 'error',
      'testing-library/prefer-user-event': 'error',
      'testing-library/render-result-naming-convention': 'error',
    },
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
    plugins: { playwright, ...testIdiomPlugins },
    rules: {
      ...testIdiomRules,
      'no-restricted-syntax': [
        ...NO_INHERITANCE,
        ...SECURITY_RESTRICTIONS,
        ...TIME_ZONE_RESTRICTIONS,
        ...NO_NETWORK_GUARD_ESCAPE,
      ],
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
