import { Workflow } from 'lucide-react'

export function WorkflowHero() {
  return (
    <>
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,_rgba(99,102,241,0.15)_0%,_transparent_60%)] dark:opacity-70 pointer-events-none" />
      <div className="relative z-10 flex h-full w-full items-center justify-center px-6">
        <div className="relative text-center space-y-3 max-w-sm">
          <div className="relative w-20 h-20 mx-auto">
            <div className="absolute inset-0 bg-indigo-500/15 rounded-full blur-2xl animate-pulse" />
            <div className="relative w-full h-full rounded-full border border-indigo-200/40 dark:border-indigo-500/30 flex items-center justify-center bg-white/80 dark:bg-slate-900/70">
              <Workflow size={28} className="text-indigo-500 dark:text-indigo-300" />
            </div>
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 animate-orbit-clockwise">
              <div className="w-1 h-1 rounded-full bg-indigo-500 shadow-[0_0_10px_3px_rgba(99,102,241,0.8)]" />
            </div>
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 animate-orbit-counterclockwise">
              <div className="w-1 h-1 rounded-full bg-emerald-500 shadow-[0_0_10px_3px_rgba(16,185,129,0.8)]" />
            </div>
          </div>
          <h3 className="text-xl font-semibold tracking-tight text-slate-800 dark:text-white">等待任务</h3>
          <p className="text-xs text-slate-500 dark:text-slate-400">在左侧输入需求，AI 将自动绘制工作流蓝图。</p>
        </div>
      </div>
    </>
  )
}
