// The facet gates against real projects in temporary working trees: each reads as not applicable where its
// facet is absent, and reaches its finding where the facet is present.

import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type GateResult, passed } from '@/exec.js'
import { build } from '@/gate-build.js'
import { bundle } from '@/gate-bundle.js'
import { configReferences } from '@/gate-config-references.js'
import { css, cssWrite } from '@/gate-css.js'
import { tailwindTokens } from '@/gate-tailwind-tokens.js'
import { tests } from '@/gate-tests.js'
import { type Gate, gateById } from '@/gates.js'
import type { Member } from '@/project.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

// Stylelint and Prettier each start a process that reads the stylesheets.
const TOOL_TIMEOUT_MS: number = 60_000

const withMember = async (
  files: Readonly<Record<string, string>>,
  packageJson: unknown,
  use: (member: Member) => GateResult | Promise<GateResult>,
): Promise<GateResult> => {
  const root: string = temporaryProject(files)
  try {
    return await use(memberAt(root, packageJson))
  } finally {
    removeProject(root)
  }
}

describe('css', PROCESS_TIME, () => {
  it('is not applicable to a member without a stylesheet', async () => {
    const result: GateResult = await withMember({ 'src/total.ts': 'export const total = 1\n' }, {}, css)
    expect(result.isApplicable).toBe(false)
  })

  it(
    'passes a stylesheet that holds to the shipped rules and formatting',
    async () => {
      const result: GateResult = await withMember(
        {
          'src/site.css': 'a {\n  color: red;\n}\n',
          'src/site.scss': '$gap: 4px;\n\n.box {\n    margin: $gap;\n}\n',
        },
        {},
        css,
      )
      expect(result).toMatchObject({ ok: true, summary: '2 stylesheet(s) pass Stylelint and formatting' })
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'reports a Stylelint finding',
    async () => {
      const result: GateResult = await withMember({ 'src/site.css': 'a {\n  colr: red;\n}\n' }, {}, css)
      expect(result.ok).toBe(false)
      expect(result.findings.join('\n')).toContain('property-no-unknown')
    },
    TOOL_TIMEOUT_MS,
  )

  it(
    'reports SCSS the shipped Prettier options format differently',
    async () => {
      const result: GateResult = await withMember({ 'src/site.scss': '.box{margin:4px}\n' }, {}, css)
      expect(result.findings).toEqual(['src/site.scss: SCSS formatting differs from the shipped Prettier options'])
    },
    TOOL_TIMEOUT_MS,
  )

  it('refuses a Stylelint configuration or ignore file of the project', async () => {
    const result: GateResult = await withMember(
      { 'src/site.css': 'a {\n  color: red;\n}\n', '.stylelintrc.json': '{}\n', '.stylelintignore': 'src\n' },
      {},
      css,
    )
    expect(result.findings).toEqual([
      '.stylelintignore: delete it; the css gate runs the shipped configuration',
      '.stylelintrc.json: delete it; the css gate runs the shipped configuration',
    ])
  })

  it('leaves generated stylesheets and those outside the source roots out', async () => {
    const result: GateResult = await withMember(
      { 'public/vendor.css': 'a{colr:red}\n', 'src/generated/theme.css': 'a{colr:red}\n' },
      { airness: { generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'a theme generator writes it' }] } },
      css,
    )
    expect(result.isApplicable).toBe(false)
  })
})

describe('tailwind-tokens', PROCESS_TIME, () => {
  it('is not applicable to a member that does not declare tailwindcss', async () => {
    const result: GateResult = await withMember(
      { 'src/page.tsx': 'export const page = "bg-[#0a7]"\n' },
      {},
      tailwindTokens,
    )
    expect(result.isApplicable).toBe(false)
  })

  it('reports an arbitrary value with its place', async () => {
    const result: GateResult = await withMember(
      { 'src/page.tsx': 'export const page = <div className="p-4 bg-[#0a7]" />\n' },
      { devDependencies: { tailwindcss: '4.0.0' } },
      tailwindTokens,
    )
    expect(result.findings).toEqual([
      'src/page.tsx:1:43 arbitrary Tailwind value `-[#0a7]`; apply a theme token through a utility class',
    ])
  })

  it('passes markup that uses only theme utilities', async () => {
    const result: GateResult = await withMember(
      { 'src/page.tsx': 'export const page = <div className="p-4 bg-primary" />\n' },
      { dependencies: { tailwindcss: '4.0.0' } },
      tailwindTokens,
    )
    expect(result).toMatchObject({ ok: true, summary: '1 markup file(s) use only token-bound Tailwind utilities' })
  })
})

describe('the Next.js facet', PROCESS_TIME, () => {
  it('builds and measures nothing in a member that is no Next.js application', async () => {
    const built: GateResult = await withMember({}, {}, build)
    const measured: GateResult = await withMember({}, {}, bundle)
    expect(built.isApplicable).toBe(false)
    expect(measured.isApplicable).toBe(false)
  })

  it('fails a Next.js application whose next is not installed', async () => {
    const result: GateResult = await withMember({}, { dependencies: { next: '16.4.2' } }, (member: Member) =>
      build({ ...member, hasOwnRuntime: true }),
    )
    expect(result.summary).toBe('next could not be resolved from the member')
  })

  it('refuses to measure when no build of this tree is recorded', async () => {
    const result: GateResult = await withMember({}, { dependencies: { next: '16.4.2' } }, (member: Member) =>
      bundle({ ...member, hasOwnRuntime: true }),
    )
    expect(result.summary).toBe('no current production build to measure')
  })
})

// A stand-in for an installed Next.js: a command that succeeds, and the configuration loader the bundle gate's
// probe calls, which names the build directory.
const FAKE_NEXT: Readonly<Record<string, string>> = {
  'node_modules/next/package.json': '{"name":"next","version":"16.4.2","bin":{"next":"bin.cjs"}}\n',
  'node_modules/next/bin.cjs': 'process.exitCode = 0\n',
  'node_modules/next/dist/server/config.js': "module.exports = { default: async () => ({ distDir: '.next' }) }\n",
}

const NEXT_MANIFEST: Readonly<Record<string, unknown>> = { dependencies: { next: '16.4.2' } }

const asNext = (member: Member): Member => ({ ...member, hasOwnRuntime: true })

describe('the Next.js facet against an installed next', PROCESS_TIME, () => {
  it("builds with the member's own next, records the build, and measures only the build of the tree as it is", async () => {
    const files: Readonly<Record<string, string>> = {
      ...FAKE_NEXT,
      'src/page.ts': 'export const page = 1\n',
      '.next/BUILD_ID': 'first\n',
      '.next/static/chunks/main.js': 'x'.repeat(4096),
    }
    await withMember(files, NEXT_MANIFEST, (member: Member): GateResult => {
      const next: Member = asNext(member)
      expect(build(next).findings).toEqual([])
      expect(bundle(next).ok).toBe(true)
      const lowered: Member = { ...next, settings: { ...next.settings, bundleBudgetBytes: 1 } }
      expect(bundle(lowered).summary).toContain('is over budget')
      writeFileSync(path.join(member.root, '.next', 'BUILD_ID'), 'second\n')
      expect(bundle(next).findings.join('\n')).toContain('holds another build')
      writeFileSync(path.join(member.root, 'src', 'page.ts'), 'export const page = 2\n')
      expect(bundle(next).findings.join('\n')).toContain('the working tree changed since the build')
      return passed('checked')
    })
  })

  it('fails the build when the built directory carries no build id', async () => {
    const result: GateResult = await withMember(FAKE_NEXT, NEXT_MANIFEST, (member: Member) => build(asNext(member)))
    expect(result.summary).toBe('the production build cannot be recorded')
  })
})

describe('config-refs: skipped routes', PROCESS_TIME, () => {
  it('judges a skipped prefix against the pages a Next.js application declares', async () => {
    const files: Readonly<Record<string, string>> = {
      'src/app/layout.tsx': 'export default function Layout(): null {\n  return null\n}\n',
      'src/app/page.tsx': 'export default function Home(): null {\n  return null\n}\n',
      'src/app/drafts/page.tsx': 'export default function Drafts(): null {\n  return null\n}\n',
    }
    const manifest: Readonly<Record<string, unknown>> = {
      ...NEXT_MANIFEST,
      airness: {
        accessibilitySkipRoutes: [
          { pattern: '/drafts', reason: 'unpublished pages' },
          { pattern: '/gone', reason: 'removed pages' },
          { pattern: '/', reason: 'later' },
        ],
      },
    }
    const result: GateResult = await withMember(files, manifest, (member: Member) => configReferences(asNext(member)))
    expect(result.findings).toEqual([
      'accessibilitySkipRoutes entry "/gone" skips no page the member declares, so it records a decision with no effect',
      'accessibilitySkipRoutes entry "/" names no subtree; write a prefix such as /drafts, never one that covers the ' +
        'whole site',
    ])
  })
})

describe('tests: spec roles', PROCESS_TIME, () => {
  it('reports a spec no runner collects before running the suite', async () => {
    const result: GateResult = await withMember(
      { 'src/total.spec.ts': 'export const spec = 1\n', 'tests/e2e/home.e2e.spec.ts': 'export const home = 1\n' },
      {},
      tests,
    )
    expect(result.findings).toEqual([
      'src/total.spec.ts is a spec no runner collects; unit specs are tests/**/*.spec.{ts,tsx} and the end-to-end ' +
        'specs of a browser application tests/e2e/**/*.e2e.spec.ts',
      'tests/e2e/home.e2e.spec.ts is an end-to-end spec in a member that is no browser application, so nothing ' +
        'runs it',
    ])
  })
})

describe('stylesheet formatting', PROCESS_TIME, () => {
  it('writes SCSS with the same selection and options its gate checks', () => {
    const root: string = temporaryProject({ 'src/site.scss': '.box{margin:4px}\n' })
    try {
      const member: Member = memberAt(root)
      expect(cssWrite(member).ok).toBe(true)
      expect(readFileSync(path.join(root, 'src/site.scss'), 'utf8')).toContain('    margin: 4px;')
      expect(css(member).ok).toBe(true)
    } finally {
      removeProject(root)
    }
  })
})

describe('a stylesheet-only tooling project', PROCESS_TIME, () => {
  // The shape a Java application's frontend tooling directory takes: the pinned configurations and its stylesheets.
  const Tooling: Readonly<Record<string, string>> = {
    'eslint.config.mjs': "export { default } from '@airness/node/eslint'\n",
    'vitest.config.mts': "import config from '@airness/node/vitest'\n\nexport default config\n",
    'src/main/resources/static/css/site.css': 'a {\n  color: red;\n}\n',
  }
  const Manifest: unknown = { airness: { sourceRoots: ['src/main/resources/static'] } }

  it.each(['types', 'eslint', 'arch', 'type-coverage', 'knip'])(
    'reports %s as not applicable rather than asking for a tsconfig.json or a script',
    async (id: string) => {
      const gate: Gate | undefined = gateById(id)
      if (gate === undefined) {
        throw new Error(`no gate ${id}`)
      }
      const result: GateResult = await withMember(Tooling, Manifest, (member: Member) =>
        gate.run(member, { evidenceDirectory: undefined }),
      )
      expect(result).toMatchObject({ ok: true, isApplicable: false })
    },
  )

  it(
    'still judges its stylesheets',
    async () => {
      const result: GateResult = await withMember(Tooling, Manifest, css)
      expect(result).toMatchObject({ ok: true, summary: '1 stylesheet(s) pass Stylelint and formatting' })
    },
    TOOL_TIMEOUT_MS,
  )
})
