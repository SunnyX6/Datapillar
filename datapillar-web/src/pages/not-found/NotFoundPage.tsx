import { Link } from 'react-router-dom'
import { AlertTriangle, Home, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui'
import { panelWidthClassMap, surfaceSizeClassMap } from '@/design-tokens/dimensions'

export function NotFoundPage() {
  return (
    <div className="min-h-dvh w-full bg-black flex items-center justify-center relative overflow-hidden font-mono selection:bg-red-500/30 selection:text-white">
      <div
        className="absolute inset-0 opacity-[0.03] pointer-events-none"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
        }}
      />
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#111_1px,transparent_1px),linear-gradient(to_bottom,#111_1px,transparent_1px)] bg-[size:40px_40px] opacity-20" />

      <div className="relative z-10 flex flex-col items-center px-4 text-center">
        <div className={`relative ${surfaceSizeClassMap.xl} mb-12 flex items-center justify-center`}>
          <div className="absolute inset-0 rounded-full border border-red-900/30 opacity-20 animate-[spin_10s_linear_infinite]" />
          <div className="absolute inset-4 rounded-full border border-red-500/20 opacity-40 animate-[spin_15s_linear_infinite_reverse]" />
          <div className="absolute inset-0 rounded-full bg-red-500 blur-[120px] opacity-10 animate-pulse" />

          <div className={`relative ${surfaceSizeClassMap.sm} bg-black rounded-full border border-red-500/50 shadow-[0_0_50px_-10px_rgba(220,38,38,0.5)] flex items-center justify-center`}>
            <span className="text-4xl font-bold text-red-500 animate-pulse">404</span>
          </div>
        </div>

        <div className="flex items-center justify-center gap-2 text-red-500 mb-4 uppercase tracking-[0.35em] text-xs">
          <AlertTriangle size={14} />
          system alert
        </div>
        <h1 className="text-5xl font-bold text-white tracking-tight mb-3">DATA VOID</h1>
        <p className="text-slate-400 text-sm max-w-md leading-relaxed">
          目标节点已漂移或被回收。跳转旧链接时，神经网络无法在图谱中定位该页面。
        </p>
        <p className="text-slate-500 text-xs uppercase tracking-[0.3em] mt-4">
          node id · undefined
        </p>

        <div className="mt-10 flex flex-wrap gap-4 justify-center">
          <Link
            to="/home"
            className="flex items-center gap-2 px-6 py-2 bg-white text-black hover:bg-slate-200 rounded-lg text-xs font-bold uppercase tracking-wide transition-all"
          >
            <Home size={14} />
            返回 Home
          </Link>
          <Button
            type="button"
            onClick={() => window.location.reload()}
            variant="ghost"
            size="small"
            className="flex items-center gap-2 px-6 py-2 bg-transparent border border-white/10 text-slate-400 hover:text-white hover:border-white/30 text-xs font-bold uppercase tracking-wide transition-all"
          >
            <RefreshCw size={14} />
            重新拉取
          </Button>
        </div>
      </div>

      <div className={`absolute top-8 right-8 ${panelWidthClassMap.medium} bg-black/80 border border-red-900/30 p-4 hidden md:block text-micro text-red-500/70`}>
        <div className="mb-2 border-b border-red-900/20 pb-1 flex justify-between">
          <span>ERR_LOG_DUMP</span>
          <span>LIVE</span>
        </div>
        <div className="space-y-1 opacity-80">
          <p>&gt; tracing route... FAILED</p>
          <p>&gt; ping gateway... TIMEOUT</p>
          <p>&gt; checksum... CORRUPTED</p>
          <p>
            &gt; initiating reroute... <span className="animate-pulse">_</span>
          </p>
        </div>
      </div>
    </div>
  )
}
