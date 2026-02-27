import type { CSSProperties } from 'react'
import { Activity, ArrowUp, Box, Cpu as ChipIcon, GitBranch, GitCommit, Network, Plus, RefreshCw, Zap } from 'lucide-react'
import { Card } from '@/components/ui'
import { surfaceSizeClassMap } from '@/design-tokens/dimensions'

const runtimeStats = {
  cpu: 45,
  memory: 62,
  kernel: 'Python 3.10 (PySpark)',
  status: 'Ready',
  uptime: '4h 21m'
}

const gitContext = {
  branch: 'feat/retention-analysis-v2',
  remote: 'origin/feat/retention-analysis-v2',
  status: 'ahead', // ahead, behind, synced
  commitsAhead: 3,
  commitsBehind: 0,
  changes: [
    { file: 'analytics/core/retention.py', status: 'M', additions: 24, deletions: 5 },
    { file: 'tests/unit/test_retention.py', status: 'A', additions: 142, deletions: 0 },
    { file: 'config/pipeline_settings.yaml', status: 'M', additions: 2, deletions: 1 },
    { file: 'docs/api_v2_changelog.md', status: 'A', additions: 45, deletions: 0 },
  ],
  lastCommit: {
    message: 'feat: implement sliding window logic',
    hash: '7b3f1a',
    time: '2h ago'
  }
}

export function OneIdeRightColumn() {
  const totalAdditions = gitContext.changes.reduce((acc, file) => acc + file.additions, 0)
  const totalDeletions = gitContext.changes.reduce((acc, file) => acc + file.deletions, 0)

  return (
    <div className="col-span-12 @md:col-span-4 flex flex-col gap-4 @md:gap-6 min-h-0">
      <section className="flex flex-col min-h-0">
        <div className="flex items-center justify-between mb-4 @md:mb-5">
          <h2 className="text-legal font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest flex items-center gap-2">
            <Activity size={14} className="text-brand-500" />
            Environment Status
          </h2>
          <button className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors">
            <RefreshCw size={12} />
          </button>
        </div>

        <Card variant="default" className="relative overflow-hidden group flex flex-col flex-1">
          <div className={`absolute top-0 right-0 ${surfaceSizeClassMap.md} bg-gradient-to-br from-brand-50 to-indigo-50 rounded-bl-[100px] opacity-50 transition-all duration-700 group-hover:scale-110`}></div>

          <div className="relative z-10 flex flex-col flex-1 gap-6">
            <div>
              <div className="flex items-center space-x-4 mb-4">
                <div className="relative">
                  <div className="w-12 h-12 rounded-2xl bg-slate-900 flex items-center justify-center text-white shadow-lg shadow-slate-200 dark:shadow-black/20">
                    <Box size={20} />
                  </div>
                  <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-emerald-500 border-2 border-white rounded-full flex items-center justify-center">
                    <div className="w-1.5 h-1.5 bg-white rounded-full animate-pulse"></div>
                  </div>
                </div>
                <div>
                  <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">Dev Cluster A</div>
                  <div className="text-caption text-slate-400 mt-0.5 font-medium">4 Nodes • 16 vCPU • 64GB</div>
                </div>
              </div>

              <div className="space-y-3">
                <div className="group/gauge">
                  <div className="flex justify-between text-caption mb-1.5">
                    <span className="text-slate-500 dark:text-slate-400 font-medium flex items-center">
                      <ChipIcon size={12} className="mr-1.5" />
                      CPU
                    </span>
                    <span className="text-slate-900 dark:text-slate-100 font-bold group-hover/gauge:text-blue-600 transition-colors">{runtimeStats.cpu}%</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                    <div
                      style={{ '--bar-width': `${runtimeStats.cpu}%` } as CSSProperties}
                      className="h-full bg-gradient-to-r from-blue-400 to-blue-600 rounded-full shadow-[0_0_10px_rgba(59,130,246,0.3)] transition-all duration-1000 w-[var(--bar-width)]"
                    ></div>
                  </div>
                </div>

                <div className="group/gauge">
                  <div className="flex justify-between text-caption mb-1.5">
                    <span className="text-slate-500 dark:text-slate-400 font-medium flex items-center">
                      <Zap size={12} className="mr-1.5" />
                      Mem
                    </span>
                    <span className="text-slate-900 dark:text-slate-100 font-bold group-hover/gauge:text-purple-600 transition-colors">{runtimeStats.memory}%</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                    <div
                      style={{ '--bar-width': `${runtimeStats.memory}%` } as CSSProperties}
                      className="h-full bg-gradient-to-r from-purple-400 to-purple-600 rounded-full shadow-[0_0_10px_rgba(147,51,234,0.3)] transition-all duration-1000 w-[var(--bar-width)]"
                    ></div>
                  </div>
                </div>
              </div>
            </div>

            <div className="h-px bg-slate-100 dark:bg-slate-800" />

            <div className="flex flex-col flex-1 min-h-0">
              <div className="flex items-center justify-between mb-3">
                <div className="text-micro font-bold text-slate-400 uppercase tracking-wider">Active Kernels</div>
                <button className="text-micro font-bold text-brand-600 hover:text-brand-700 hover:underline">Manage</button>
              </div>

              <div className="space-y-2">
                {[
                  { name: 'Python 3.10 (Spark)', status: 'Idle', icon: 'PY', color: 'bg-blue-100 text-blue-700' },
                  { name: 'Java 17 (Flink)', status: 'Running', icon: 'JV', color: 'bg-red-100 text-red-700' },
                ].map((k, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-2.5 bg-slate-50 dark:bg-slate-800/40 hover:bg-white dark:hover:bg-slate-800 border border-transparent hover:border-slate-200 dark:hover:border-slate-700 rounded-xl transition-all cursor-pointer group/kernel"
                  >
                    <div className="flex items-center space-x-2.5">
                      <div className={`w-7 h-7 rounded-lg ${k.color} flex items-center justify-center text-nano font-bold`}>{k.icon}</div>
                      <div>
                        <div className="text-caption font-semibold text-slate-700 dark:text-slate-200 group-hover/kernel:text-slate-900 dark:group-hover/kernel:text-slate-100">{k.name}</div>
                        <div className="flex items-center">
                          <span className={`w-1 h-1 rounded-full mr-1.5 ${k.status === 'Running' ? 'bg-emerald-500' : 'bg-amber-500'}`}></span>
                          <span className="text-nano text-slate-400 font-medium">{k.status}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <button className="mt-auto w-full py-2 mt-3 text-micro font-bold text-slate-400 border border-dashed border-slate-300 dark:border-slate-700 rounded-xl hover:border-brand-400 hover:text-brand-600 hover:bg-brand-50/50 dark:hover:bg-brand-500/10 transition-all flex items-center justify-center group/btn">
                <Plus size={12} className="mr-1.5 group-hover/btn:scale-110 transition-transform" />
                Launch New Kernel
              </button>
            </div>
          </div>
        </Card>
      </section>

      <section className="flex flex-col min-h-0">
        <div className="flex items-center justify-between mb-4 @md:mb-5">
          <h2 className="text-legal font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest flex items-center gap-2">
            <GitBranch size={14} className="text-brand-500" />
            Version Control
          </h2>
          <button className="text-micro font-bold text-brand-600 bg-brand-50 px-2 py-1 rounded hover:bg-brand-100 transition-colors">Sync</button>
        </div>

        <Card variant="default" className="hover:shadow-md transition-shadow overflow-hidden flex flex-col @md:h-[24rem]">
          <div className="flex items-center justify-between mb-4 @md:mb-5">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-purple-50 dark:bg-purple-500/10 text-purple-600 dark:text-purple-400 rounded-lg shadow-sm border border-purple-100 dark:border-purple-500/20">
                <GitBranch size={18} />
              </div>
              <div>
                <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 leading-tight">{gitContext.branch}</div>
                <div className="text-micro text-slate-400 flex items-center mt-0.5">
                  <Network size={10} className="mr-1" />
                  {gitContext.remote}
                </div>
              </div>
            </div>

            <div className="flex flex-col items-end">
              <div className="flex items-center space-x-1 text-micro font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 px-2 py-1 rounded border border-emerald-100 dark:border-emerald-500/20">
                <ArrowUp size={10} strokeWidth={3} />
                <span>{gitContext.commitsAhead} Ahead</span>
              </div>
            </div>
          </div>

          <div className="h-px bg-slate-100 dark:bg-slate-800"></div>

          <div className="mt-3 @md:mt-4 flex flex-col flex-1 min-h-0 gap-3 @md:gap-4">
            <div className="flex items-center justify-between">
              <span className="text-micro font-bold text-slate-400 uppercase tracking-wider">Unstaged Changes ({gitContext.changes.length})</span>
              <button className="text-micro text-brand-600 font-medium hover:underline">View Diff</button>
            </div>

            <div className="flex-1 min-h-0 overflow-auto custom-scrollbar">
              <div className="space-y-2">
                {gitContext.changes.map((file, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between text-caption group cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 p-1.5 rounded -mx-1.5 transition-colors"
                  >
                    <div className="flex items-center truncate">
                      <span
                        className={`w-4 h-4 flex items-center justify-center rounded text-nano font-bold mr-2 border shadow-sm ${
                          file.status === 'M'
                            ? 'bg-amber-50 text-amber-600 border-amber-100'
                            : file.status === 'A'
                              ? 'bg-green-50 text-green-600 border-green-100'
                              : 'bg-red-50 text-red-600 border-red-100'
                        }`}
                      >
                        {file.status}
                      </span>
                      <span className="text-slate-600 dark:text-slate-300 truncate w-44 group-hover:text-slate-900 dark:group-hover:text-slate-100 font-medium">{file.file}</span>
                    </div>
                    <div className="flex items-center space-x-1.5 text-nano opacity-0 group-hover:opacity-100 transition-opacity">
                      <span className="text-green-600 font-bold">+{file.additions}</span>
                      <span className="text-slate-300 dark:text-slate-700">/</span>
                      <span className="text-red-500 font-bold">-{file.deletions}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-xl border border-slate-100 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-800/30 p-3 flex flex-col justify-between">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="text-micro font-bold text-slate-400 uppercase tracking-wider">Last Commit</div>
                  <div className="mt-1 text-caption font-semibold text-slate-700 dark:text-slate-200 truncate">{gitContext.lastCommit.message}</div>
                  <div className="mt-1 text-nano text-slate-400 flex items-center gap-2">
                    <span className="inline-flex items-center gap-1">
                      <GitCommit size={10} className="text-slate-400" />
                      {gitContext.lastCommit.hash}
                    </span>
                    <span className="text-slate-300 dark:text-slate-700">•</span>
                    <span>{gitContext.lastCommit.time}</span>
                  </div>
                </div>
                <button className="text-micro font-semibold text-brand-600 hover:underline whitespace-nowrap">History</button>
              </div>

              <div className="mt-3 flex items-center justify-between text-nano text-slate-500 dark:text-slate-400">
                <span>
                  <span className="font-bold text-slate-700 dark:text-slate-200">{gitContext.changes.length}</span> files
                </span>
                <div className="flex items-center gap-2">
                  <span className="text-green-600 font-bold">+{totalAdditions}</span>
                  <span className="text-slate-300 dark:text-slate-700">/</span>
                  <span className="text-red-500 font-bold">-{totalDeletions}</span>
                </div>
              </div>
            </div>

            <div className="flex space-x-2">
              <button className="flex-1 py-2 bg-slate-900 text-white text-caption font-semibold rounded-lg hover:bg-black dark:hover:bg-slate-800 transition-all shadow-sm flex items-center justify-center hover:-translate-y-0.5">
                <GitCommit size={14} className="mr-2" />
                Commit Changes
              </button>
              <button className="px-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-slate-700 dark:text-slate-200 text-caption font-semibold rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">
                <RefreshCw size={14} />
              </button>
            </div>
          </div>
        </Card>
      </section>
    </div>
  )
}
