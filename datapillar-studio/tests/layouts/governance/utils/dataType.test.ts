import { describe, expect, it } from 'vitest'
import { DEFAULT_LENGTH, DEFAULT_MAX_LENGTH, getMaxLengthForType } from '@/layouts/governance/utils/dataType'

describe('dataType length defaults', () => {
  it('uses 1 as the default length', () => {
    expect(DEFAULT_LENGTH).toBe(1)
  })

  it('returns the max length for VARCHAR', () => {
    expect(getMaxLengthForType('VARCHAR')).toBe(DEFAULT_MAX_LENGTH)
  })

  it('returns the max length for FIXEDCHAR', () => {
    expect(getMaxLengthForType('FIXEDCHAR')).toBe(255)
  })

  it('falls back to default max length for unknown types', () => {
    expect(getMaxLengthForType('UNKNOWN')).toBe(DEFAULT_MAX_LENGTH)
  })
})
