import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { clearCompletedScans, completedScanRoutes, recordCompletedScan } from '@/scan-evidence'

const inDirectory = (check: (directory: string) => void): void => {
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-scan-evidence-'))
  try {
    check(directory)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

const recordFile = (directory: string): string => {
  const records: string = path.join(directory, 'airness-project-scans')
  mkdirSync(records, { recursive: true })
  return path.join(records, 'record.json')
}

describe('completed browser scan evidence', () => {
  it('reports no completed routes when this run produced no records', () => {
    inDirectory((directory: string): void => {
      expect(completedScanRoutes(directory, 'http://localhost:3000')).toEqual([])
      clearCompletedScans(directory)
      expect(completedScanRoutes(directory, 'http://localhost:3000')).toEqual([])
    })
  })

  it('records each test and URL while excluding evidence from other origins', () => {
    inDirectory((directory: string): void => {
      recordCompletedScan(directory, 'account-test', 'http://localhost:3000/account?tab=profile')
      recordCompletedScan(directory, 'second-test', 'http://localhost:3000/account?tab=profile')
      recordCompletedScan(directory, 'story-test', 'http://localhost:6006/account')
      expect(completedScanRoutes(directory, 'http://localhost:3000')).toEqual(['/account', '/account'])
      expect(readdirSync(path.join(directory, 'airness-project-scans'))).toHaveLength(3)
      recordCompletedScan(directory, 'account-test', 'http://localhost:3000/account?tab=profile')
      expect(readdirSync(path.join(directory, 'airness-project-scans'))).toHaveLength(3)
    })
  })

  it('clears previous scan evidence without removing other output', () => {
    inDirectory((directory: string): void => {
      const retained: string = path.join(directory, 'trace.zip')
      writeFileSync(retained, 'another browser artifact')
      recordCompletedScan(directory, 'old-test', 'http://localhost:3000/private')
      clearCompletedScans(directory)
      expect(completedScanRoutes(directory, 'http://localhost:3000')).toEqual([])
      expect(existsSync(path.join(directory, 'airness-project-scans'))).toBe(false)
      expect(readFileSync(retained, 'utf8')).toBe('another browser artifact')
    })
  })

  it.each([null, [], {}, { url: 1, testId: 'test' }, { url: '/account' }, { url: '/account', testId: 1 }])(
    'refuses malformed evidence %# instead of treating it as an empty scan',
    (record: unknown): void => {
      inDirectory((directory: string): void => {
        writeFileSync(recordFile(directory), JSON.stringify(record))
        expect(() => completedScanRoutes(directory, 'http://localhost:3000')).toThrow('Invalid completed scan record')
      })
    },
  )

  it('refuses invalid JSON and invalid URLs', () => {
    inDirectory((directory: string): void => {
      const file: string = recordFile(directory)
      writeFileSync(file, '{')
      expect(() => completedScanRoutes(directory, 'http://localhost:3000')).toThrow()
      writeFileSync(file, JSON.stringify({ url: 'invalid URL', testId: 'test' }))
      expect(() => completedScanRoutes(directory, 'http://localhost:3000')).toThrow()
    })
  })
})
