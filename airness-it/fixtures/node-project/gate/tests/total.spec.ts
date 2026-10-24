import { member } from '@airness/fixture-member'
import { expect, it } from 'vitest'
import { total } from '@/total.js'

it('adds two amounts', () => {
  expect(total(member, 2)).toBe(3)
})
