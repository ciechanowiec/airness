import { approved } from '@airness/fixture-approved'
import { denied } from '@airness/fixture-denied'
import { expect, it } from 'vitest'
import { total } from '@/total.js'

it('adds two amounts', () => {
  expect(total(approved, denied)).toBe(2)
})
