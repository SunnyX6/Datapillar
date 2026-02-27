import { getLastFatalError } from '@/api/errorCenter'
import type { FatalErrorSnapshot } from '@/api/errorCenter'

export type { FatalErrorSnapshot }

export function getLastFatalErrorSnapshot(): FatalErrorSnapshot | null {
  return getLastFatalError()
}
