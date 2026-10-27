import { rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { javascriptAllowlist } from '@/gate-javascript-allowlist.js'
import { knip } from '@/gate-knip.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

const GENERATED: Readonly<Record<string, unknown>> = {
  name: 'fixture-generated',
  type: 'module',
  exports: { '.': './src/index.ts' },
  airness: { generatedArtefacts: [{ pattern: 'src/generated/**', reason: 'the static exporter writes these assets' }] },
}

describe('generated JavaScript ownership', PROCESS_TIME, () => {
  it('leaves generator output out of the JavaScript ban and Knip while retaining authored findings', () => {
    const root: string = temporaryProject({
      'package.json': JSON.stringify(GENERATED),
      'tsconfig.json': JSON.stringify({ compilerOptions: { strict: true }, include: ['src/**/*.ts'] }),
      'src/index.ts': 'export const value: number = 1\n',
      'src/generated/orphan.js': 'export const generated = 1\n',
    })
    try {
      expect(javascriptAllowlist(memberAt(root, GENERATED)).ok).toBe(true)
      expect(knip(memberAt(root, GENERATED)).ok).toBe(true)
      expect(javascriptAllowlist(memberAt(root)).findings.join('\n')).toContain('src/generated/orphan.js')
      writeFileSync(path.join(root, 'src/authored.js'), 'export const authored = 1\n')
      expect(javascriptAllowlist(memberAt(root, GENERATED)).findings.join('\n')).toContain('src/authored.js')
      expect(knip(memberAt(root, GENERATED)).findings.join('\n')).toContain('src/authored.js')
    } finally {
      removeProject(root)
    }
  })
})

const runtimeManifest = (runtime: string): Readonly<Record<string, unknown>> => ({
  airness: {
    javascriptAllowlist: [
      {
        pattern: String.raw`^edge/viewer-request\.cjs$`,
        reason: 'the edge function runs in CloudFront Functions',
        runtime,
      },
    ],
  },
})

describe('modules provided by a declared JavaScript runtime', PROCESS_TIME, () => {
  it('accepts cloudfront only when an actual CloudFront function belongs to the member', () => {
    const root: string = temporaryProject({
      'package.json': JSON.stringify({ name: 'runtime-module', exports: { '.': './src/index.ts' } }),
      'src/index.ts': 'export const value: number = 1\n',
      'edge/viewer-request.cjs': "const store = require('cloudfront').kvs()\nmodule.exports = { store }\n",
    })
    try {
      expect(knip(memberAt(root, runtimeManifest('cloudfront-js-2.0'))).ok).toBe(true)
      expect(knip(memberAt(root, runtimeManifest('node'))).findings.join('\n')).toContain('cloudfront')
      rmSync(path.join(root, 'edge/viewer-request.cjs'))
      writeFileSync(path.join(root, 'src/index.ts'), "import store from 'cloudfront'\nexport { store }\n")
      expect(knip(memberAt(root, runtimeManifest('cloudfront-js-2.0'))).findings.join('\n')).toContain('cloudfront')
    } finally {
      removeProject(root)
    }
  })
})
