// The ESLint configuration of a node project, which the eslint gate hands ESLint whatever the project
// keeps beside its sources. It composes the framework-neutral blocks of `eslint-core.ts` against the
// project's own settings: every cap, ban and suppression rule there applies in full. The blocks keyed to
// a framework's layout belong to the layer that knows that framework.
import {
  allowlistedJavaScript,
  baseLayers,
  commonJavaScriptBlock,
  commonTypeScriptBlock,
  compose,
  type FlatConfigBlock,
  guidelineRules,
  immutabilityBlock,
  javascriptBlock,
  prettierLast,
  processConfigBlock,
  specEnvironmentBlock,
  testIdiomRules,
  testIntegrityRules,
  testSuiteSyntaxRules,
  typeAwareParsing,
  vitestPlugin,
} from './eslint-core.js'
import { componentTestBlocks, END_TO_END_FILES, endToEndBlocks, nextBlocks } from './eslint-next.js'
import { cloudFrontBlock } from './eslint-runtime.js'
import { JAVASCRIPT_EXTENSIONS, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import { CLOUDFRONT_RUNTIME, rolesFor } from './javascript-runtimes.js'
import { isBrowserProject, isNextProject, projectDirectory, projectSettings as settings } from './project-settings.js'
import { REEXPORT_CONFIG_FILES } from './settings.js'

// The allowlisted files whose entry declares the CloudFront runtime.
const isCloudFrontFile: (filePath: string) => boolean = allowlistedJavaScript(
  rolesFor(settings.javascriptRuntimes, CLOUDFRONT_RUNTIME),
  projectDirectory,
)

// `next-env.d.ts` is the ambient declaration file `next build` writes and rewrites, whatever a project keeps.
const IGNORED: readonly string[] = [
  'target/**',
  'dist/**',
  'node_modules/**',
  'coverage/**',
  '.next/**',
  'next-env.d.ts',
  'test-results/**',
  'playwright-report/**',
]

// `unicorn/prefer-export-from` rewrites `import x from 'y'` + `export default x` into
// `export { default } from 'y'` - and it AUTOFIXES, so a format run turned a correctly wired
// project into one the harness then rejected, every time it ran. Two rules this harness owns,
// contradicting each other, with the formatter casting the deciding vote.
const reexportConfigBlock: FlatConfigBlock = {
  files: [...REEXPORT_CONFIG_FILES],
  rules: { 'unicorn/prefer-export-from': 'off' },
}

const config: ReturnType<typeof compose> = compose(
  { ignores: [...IGNORED, ...settings.generatedArtefacts] },
  {
    files: [...TYPESCRIPT_SOURCE_EXTENSIONS, ...JAVASCRIPT_EXTENSIONS].map(
      (extension: string): string => `**/*${extension}`,
    ),
  },
  ...baseLayers,
  typeAwareParsing({ projectService: true }),
  { rules: { ...guidelineRules } } satisfies FlatConfigBlock,
  // Every authored module format has the same mutation policy. Generated artifacts are globally
  // ignored, and declared framework glue retains its role-specific allowance.
  immutabilityBlock(
    TYPESCRIPT_SOURCE_EXTENSIONS.map((extension: string): string => `**/*${extension}`),
    settings.frameworkGlue,
  ),

  // The setup file's role, straight after the block it narrows. It must come later: a block that sets
  // this rule replaces its options, so stating the role first would have the general block undo it.
  processConfigBlock(),
  // And the spec's, which is to vary the environment rather than to fix it. Same ordering constraint.
  specEnvironmentBlock(),
  // The JavaScript a library may carry is what its allowlist admits, its own flat ESLint config among
  // the defaults, so the file the conventions gate lets be JavaScript is linted as JavaScript here.
  javascriptBlock([allowlistedJavaScript(settings.javascriptAllowlist, projectDirectory)]),
  // A file whose entry declares the CloudFront runtime is also held to what that engine supports.
  cloudFrontBlock([isCloudFrontFile]),
  // A library's specs are held to the same integrity rules as an application's: the suite is a gate, so
  // a test that cannot fail is a gate that cannot fail.
  //
  // The plugin is mounted here rather than spread as an entry of its own. A plugin is not a config
  // block - ESLint rejects the whole config on the `meta` key it carries - and it belongs in the block
  // that states the rules naming it, so the two cannot be separated by an edit.
  //
  // All three tables are the shared ones. This block used to carry the integrity rules alone, which read
  // as complete and was not: a library's specs were held to the bare-number ban a Payload consumer's
  // specs are exempt from, and guarded by two of the nineteen selectors rather than all of them. Neither
  // gap is about serving an application, which is the only thing this config is meant to differ about.
  {
    files: ['tests/**'],
    // The end-to-end specs are Playwright's, held to the browser-application facet's block below instead.
    ignores: [...END_TO_END_FILES],
    plugins: { vitest: vitestPlugin },
    rules: {
      ...testIdiomRules,
      ...testIntegrityRules,
      ...testSuiteSyntaxRules,
    },
  } satisfies FlatConfigBlock,
  ...(isNextProject ? nextBlocks : []),
  ...(isBrowserProject ? endToEndBlocks : []),
  ...(isBrowserProject ? componentTestBlocks : []),
  reexportConfigBlock,
  commonJavaScriptBlock(settings.javascriptAllowlist, projectDirectory),
  commonTypeScriptBlock(),
  prettierLast,
)

export default config
