import { useState } from 'react'
import { Activity, CheckCircle2, Copy, RefreshCw, ServerCrash } from 'lucide-react'
import { getLastFatalError } from '@/lib/error-center'
import { getStudioServiceHealth } from '@/services/healthService'
import { cn, formatTime } from '@/lib/utils'

const HEALTHY_SERVICE_STATUS = new Set(['UP', 'OK', 'HEALTHY'])

type ServiceHealthState = 'idle' | 'checking' | 'healthy' | 'unhealthy'

function resolveServiceHealthStatus(payload: unknown): string | null {
  if (!payload || typeof payload !== 'object' || !('status' in payload)) {
    return null
  }
  const status = (payload as { status?: unknown }).status
  if (typeof status !== 'string' || !status.trim()) {
    return null
  }
  return status.trim().toUpperCase()
}

export function ServerErrorPage() {
  const [fatalError] = useState(() => getLastFatalError())
  const [copied, setCopied] = useState(false)
  const [serviceHealthState, setServiceHealthState] = useState<ServiceHealthState>('idle')
  const referenceId = fatalError?.requestId ?? fatalError?.traceId ?? 'N/A'
  const hasReferenceId = referenceId !== 'N/A'
  const errorTime = fatalError?.timestamp ? formatTime(fatalError.timestamp) : '-'

  const handleRetry = () => {
    window.location.replace('/')
  }

  const handleServiceStatus = async () => {
    if (serviceHealthState === 'checking') {
      return
    }
    setServiceHealthState('checking')
    try {
      const payload = await getStudioServiceHealth()
      const status = resolveServiceHealthStatus(payload)
      if (status && HEALTHY_SERVICE_STATUS.has(status)) {
        setServiceHealthState('healthy')
        return
      }
      setServiceHealthState('unhealthy')
    } catch {
      setServiceHealthState('unhealthy')
    }
  }

  const handleCopy = async () => {
    if (!hasReferenceId) {
      return
    }
    try {
      await navigator.clipboard.writeText(referenceId)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  const serviceStatusLabel = serviceHealthState === 'checking'
    ? '检查中...'
    : serviceHealthState === 'healthy'
      ? '服务健康'
      : serviceHealthState === 'unhealthy'
        ? '服务异常'
        : '查看服务状态'

  return (
    <div className="relative flex min-h-dvh w-full items-center justify-center overflow-hidden bg-slate-50 selection:bg-rose-100 selection:text-rose-600">
      <div className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
        <div className="absolute top-[-10%] right-[-6%] size-[28rem] rounded-full bg-purple-200/40 blur-[100px]" />
        <div className="absolute bottom-[-12%] left-[-12%] size-[34rem] rounded-full bg-indigo-200/40 blur-[120px]" />
        <div className="absolute top-[42%] left-[42%] size-[22rem] rounded-full bg-rose-100/30 blur-[100px]" />
        <div
          className="absolute inset-0 opacity-[0.03]"
          style={{
            backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
          }}
        />
      </div>

      <div className="fixed top-0 right-0 left-0 z-20 h-1 bg-gradient-to-r from-rose-500 via-purple-500 to-indigo-500" />

      <div className="pointer-events-none absolute inset-0 z-0 flex items-center justify-center select-none">
        <h1 className="text-display translate-y-10 scale-[9.5] font-black leading-none tracking-tighter text-slate-900/[0.03]">
          500
        </h1>
      </div>

      <div className="relative z-10 flex max-w-2xl flex-col items-center px-6 text-center">
        <div className="group relative mb-6">
          <div className="absolute inset-0 rounded-full bg-rose-400/20 blur-xl transition-transform duration-700 group-hover:scale-110" />
          <div className="relative flex h-16 w-16 rotate-3 items-center justify-center rounded-2xl border border-white/50 bg-white shadow-xl transition-transform duration-300 group-hover:rotate-6">
            <ServerCrash className="h-8 w-8 text-rose-500" strokeWidth={1.5} />
          </div>
        </div>

        <h1 className="mb-4 text-4xl font-bold tracking-tight text-slate-900">
          服务器<span className="text-rose-500">出错了</span>
        </h1>
        <p className="mx-auto mb-8 max-w-xl text-base leading-relaxed font-light text-slate-600">
          抱歉，服务器遇到了一些意外情况。我们的技术团队已收到自动警报，正在紧急修复中。
        </p>

        <div className="flex items-center justify-center gap-4">
          <button
            type="button"
            onClick={handleRetry}
            className="group inline-flex items-center justify-center rounded-full bg-slate-900 px-6 py-3 text-sm font-medium text-white transition-all duration-300 hover:-translate-y-0.5 hover:bg-slate-800 hover:shadow-xl hover:shadow-slate-900/20"
          >
            <RefreshCw className="mr-2 h-4 w-4 transition-transform duration-500 group-hover:rotate-180" />
            刷新页面
          </button>

          <button
            type="button"
            onClick={handleServiceStatus}
            data-testid="server-health-button"
            disabled={serviceHealthState === 'checking'}
            className={cn(
              'inline-flex items-center justify-center rounded-full border px-6 py-3 text-sm font-medium transition-all duration-300',
              serviceHealthState === 'healthy'
                ? 'border-emerald-300 bg-emerald-50 text-emerald-700 hover:-translate-y-0.5 hover:border-emerald-400 hover:bg-emerald-100/70 hover:shadow-lg'
                : serviceHealthState === 'checking'
                  ? 'border-amber-300 bg-amber-50 text-amber-700 cursor-wait'
                  : 'border-rose-300 bg-rose-50 text-rose-600 hover:-translate-y-0.5 hover:border-rose-400 hover:bg-rose-100/70 hover:shadow-lg'
            )}
          >
            <Activity
              className={cn(
                'mr-2 h-4 w-4',
                serviceHealthState === 'healthy'
                  ? 'text-emerald-500'
                  : serviceHealthState === 'checking'
                    ? 'text-amber-500 animate-pulse'
                    : 'text-rose-500'
              )}
            />
            {serviceStatusLabel}
          </button>
        </div>

        <div className="group mt-10">
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex items-center gap-2 rounded-full border border-transparent bg-slate-100/50 px-4 py-2 text-slate-500 transition-all duration-300 hover:border-slate-200 hover:bg-white hover:text-indigo-600 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-60"
            disabled={!hasReferenceId}
          >
            <span className="text-xs font-semibold tracking-wider uppercase opacity-70">Request ID</span>
            <span className="border-l border-slate-300 pl-2 font-mono text-sm font-medium">{referenceId}</span>
            {copied && hasReferenceId ? (
              <CheckCircle2 className="ml-1 h-3.5 w-3.5 text-green-500" />
            ) : (
              <Copy className={`ml-1 h-3.5 w-3.5 transition-opacity ${hasReferenceId ? 'opacity-0 group-hover:opacity-100' : 'opacity-0'}`} />
            )}
          </button>
          <p className="mt-3 text-xs text-slate-400 opacity-0 transition-opacity duration-500 group-hover:opacity-100">
            {hasReferenceId ? '点击复制 Request ID 并提供给支持团队' : '当前错误未返回可追踪 ID'}
          </p>
          <div className="mt-4 space-y-1 text-xs text-slate-500">
            <p>模块：{fatalError?.module ?? '-'}</p>
            <p>状态码：{fatalError?.status ?? '-'}</p>
            <p>发生时间：{errorTime}</p>
          </div>
        </div>
      </div>

      <footer className="absolute right-0 bottom-6 left-0 text-center">
        <div className="inline-flex items-center gap-6 text-xs font-medium text-slate-400">
          <a href="/" className="transition-colors hover:text-slate-800">首页</a>
          <span className="h-1 w-1 rounded-full bg-slate-300" />
          <a href="/" className="transition-colors hover:text-slate-800">帮助中心</a>
          <span className="h-1 w-1 rounded-full bg-slate-300" />
          <a href="/" className="transition-colors hover:text-slate-800">联系支持</a>
        </div>
      </footer>
    </div>
  )
}
