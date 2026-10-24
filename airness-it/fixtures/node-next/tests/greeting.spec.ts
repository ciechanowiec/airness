import { expect, it } from 'vitest'
import { greeting } from '@/greeting.js'

it('greets by name', () => {
  expect(greeting('Airness')).toBe('Hello, Airness')
})
