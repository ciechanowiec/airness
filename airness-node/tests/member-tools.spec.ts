import { describe, expect, it } from 'vitest'
import { memberBin } from '@/member-tools.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { memberAt, removeProject, temporaryProject } from './helpers/temporary-project.js'

describe('memberBin', PROCESS_TIME, () => {
  it('resolves a command named by a package of the member, in either form of bin', () => {
    const root: string = temporaryProject({
      'node_modules/single/package.json': '{"name":"single","bin":"cli.cjs"}\n',
      'node_modules/named/package.json': '{"name":"named","bin":{"other":"other.cjs"}}\n',
      'node_modules/bare/package.json': '{"name":"bare"}\n',
    })
    try {
      expect(memberBin(memberAt(root), 'single')).toMatch(/single\/cli\.cjs$/u)
      expect(memberBin(memberAt(root), 'named', 'other')).toMatch(/named\/other\.cjs$/u)
      expect(memberBin(memberAt(root), 'named')).toBeUndefined()
      expect(memberBin(memberAt(root), 'bare')).toBeUndefined()
      expect(memberBin(memberAt(root), 'absent')).toBeUndefined()
    } finally {
      removeProject(root)
    }
  })
})
