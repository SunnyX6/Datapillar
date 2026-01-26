const formatMissingEnv = (key: string) => `请设置环境变量 ${key}`

export const getRequiredEnv = (key: string): string => {
  const value = process.env[key]
  if (!value) {
    throw new Error(formatMissingEnv(key))
  }
  return value
}

export const getNumberEnv = (key: string, fallback: number): number => {
  const value = process.env[key]
  if (!value) {
    return fallback
  }
  const parsed = Number(value)
  if (Number.isNaN(parsed)) {
    throw new Error(`${key} 必须是数字`)
  }
  return parsed
}

export const getPerfBudgets = () => ({
  lcp: getNumberEnv('PERF_LCP_MAX', 2500),
  cls: getNumberEnv('PERF_CLS_MAX', 0.1),
  inp: getNumberEnv('PERF_INP_MAX', 200)
})
