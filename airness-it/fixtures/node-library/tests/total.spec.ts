import { expect, it } from 'vitest'
import { total } from '@/total.js'

it('adds two amounts', () => {
  expect(total(1, 2)).toBe(3)
})
