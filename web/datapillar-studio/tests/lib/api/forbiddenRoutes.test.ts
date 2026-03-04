import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '../../..')
const scanRoots = [
  path.join(projectRoot, 'src/services'),
  path.join(projectRoot, 'src/features'),
  path.join(projectRoot, 'src/pages'),
  path.join(projectRoot, 'src/hooks')
]

const sourceFilePattern = /\.[cm]?[jt]sx?$/
const forbiddenPatterns = [
  /\/api\/onemeta\b/i,
  /\/plugins\/datapillar\b/i,
  /\/datapillar\//i,
  /["'`]\/api\/[^"'`]*["'`]/
]

function collectFiles(dir: string): string[] {
  const entries = readdirSync(dir)
  const files: string[] = []
  for (const entry of entries) {
    const absolutePath = path.join(dir, entry)
    const info = statSync(absolutePath)
    if (info.isDirectory()) {
      files.push(...collectFiles(absolutePath))
      continue
    }
    if (sourceFilePattern.test(absolutePath)) {
      files.push(absolutePath)
    }
  }
  return files
}

function listViolations(): string[] {
  const violations: string[] = []
  for (const root of scanRoots) {
    for (const file of collectFiles(root)) {
      const content = readFileSync(file, 'utf-8')
      for (const pattern of forbiddenPatterns) {
        if (pattern.test(content)) {
          violations.push(`${path.relative(projectRoot, file)} matches ${pattern}`)
        }
      }
    }
  }
  return violations
}

describe('API route guard', () => {
  it('should not introduce direct onemeta/airflow-plugin routes in frontend call layers', () => {
    expect(listViolations()).toEqual([])
  })
})
