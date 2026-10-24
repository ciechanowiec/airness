import { version } from 'node:process'
import { member } from '@airness/fixture-member'
import { expect, it } from 'vitest'
import { total } from '@/total.js'

it('adds two amounts', () => {
  expect(total(member, 2)).toBe(3)
})

// The suite runs inside the tests gate, so this proves the gate ran on the Node the harness installed; the
// integration suite writes the pinned version in when it copies the fixture.
it('runs on the Node the harness pins', () => {
  expect(version).toBe('v@NODE_VERSION@')
})
