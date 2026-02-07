import { useNavigate } from 'react-router-dom'
import { ChevronRight, Clock, Code2, Coffee, Database, FileCode, GitBranch, Search, Terminal, Zap } from 'lucide-react'
import { Card } from '@/components/ui'
import { cn } from '@/lib/utils'

const recentFiles = [
  { id: 1, name: 'user_retention_analysis.py', path: 'analytics/core/retention', type: 'python', updated: '10 mins ago', status: 'modified', size: '12 KB', branch: 'feat/retention-v2' },
  { id: 2, name: 'fact_daily_orders.sql', path: 'warehouse/production/orders', type: 'sql', updated: '2 hours ago', status: 'synced', size: '4 KB', branch: 'main' },
  { id: 3, name: 'DataIngestJob.java', path: 'src/main/java/jobs', type: 'java', updated: 'Yesterday', status: 'synced', size: '24 KB', branch: 'main' },
  { id: 4, name: 'deploy_k8s_config.yaml', path: 'ops/k8s/production', type: 'yaml', updated: '2 days ago', status: 'untracked', size: '2 KB', branch: '-' },
  { id: 5, name: 'utils.sh', path: 'scripts/common', type: 'shell', updated: '3 days ago', status: 'synced', size: '1.5 KB', branch: 'main' },
]

const templates = [
  {
    id: 't1',
    title: 'SQL Query',
    subtitle: 'Ad-hoc Analysis',
    desc: 'Standard ANSI SQL environment with auto-completion for warehouse tables.',
    icon: Database,
    colorClass: 'text-orange-500',
    bgClass: 'bg-orange-50',
    borderClass: 'hover:border-orange-200 hover:ring-orange-50',
    gradient: 'from-orange-500/10 to-transparent'
  },
  {
    id: 't2',
    title: 'Python Notebook',
    subtitle: 'Data Science & ML',
    desc: 'Jupyter-compatible environment with pre-installed Pandas, PySpark, and Torch.',
    icon: Code2,
    colorClass: 'text-blue-500',
    bgClass: 'bg-blue-50',
    borderClass: 'hover:border-blue-200 hover:ring-blue-50',
    gradient: 'from-blue-500/10 to-transparent'
  },
  {
    id: 't3',
    title: 'Java Service',
    subtitle: 'Stream Processing',
    desc: 'High-performance Flink/Spark jobs development with Maven support.',
    icon: Coffee,
    colorClass: 'text-red-500',
    bgClass: 'bg-red-50',
    borderClass: 'hover:border-red-200 hover:ring-red-50',
    gradient: 'from-red-500/10 to-transparent'
  },
  {
    id: 't4',
    title: 'Shell Script',
    subtitle: 'DevOps Automation',
    desc: 'Bash/Zsh environment for operational tasks and deployment scripts.',
    icon: Terminal,
    colorClass: 'text-slate-600 dark:text-slate-300',
    bgClass: 'bg-slate-100 dark:bg-slate-800/60',
    borderClass: 'hover:border-slate-300 hover:ring-slate-50 dark:hover:border-slate-600 dark:hover:ring-slate-900/30',
    gradient: 'from-slate-500/10 to-transparent'
  },
]

const FileIcon = ({ type }: { type: string }) => {
  const base = 'w-9 h-9 rounded-lg flex items-center justify-center font-bold text-micro shadow-sm border border-opacity-50'
  switch (type) {
    case 'python':
      return <div className={`${base} bg-blue-50 text-blue-600 border-blue-100`}>PY</div>
    case 'sql':
      return <div className={`${base} bg-orange-50 text-orange-600 border-orange-100`}>SQL</div>
    case 'java':
      return <div className={`${base} bg-red-50 text-red-600 border-red-100`}>JVM</div>
    case 'shell':
      return <div className={`${base} bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700`}>SH</div>
    case 'yaml':
      return <div className={`${base} bg-purple-50 text-purple-600 border-purple-100`}>YML</div>
    default:
      return (
        <div className={`${base} bg-slate-50 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:border-slate-700`}>
          <FileCode size={16} />
        </div>
      )
  }
}

export function OneIdeLeftColumn() {
  const navigate = useNavigate()

  const getTemplateRoute = (templateId: string): string | null => {
    // 目前项目只落地了 SQL 编辑器入口
    if (templateId === 't1') return '/ide/sql'
    return null
  }

  const getFileRoute = (fileType: string): string | null => {
    if (fileType === 'sql') return '/ide/sql'
    return null
  }

  return (
    <div className="col-span-12 @md:col-span-8 flex flex-col gap-4 @md:gap-6">
      <section>
        <div className="flex items-center justify-between mb-4 @md:mb-5">
          <h2 className="text-legal font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest flex items-center gap-2">
            <Zap size={14} className="text-brand-500" />
            Initialize Workspace
          </h2>
        </div>

        <div className="grid grid-cols-1 @md:grid-cols-2 gap-4 @md:gap-6">
          {templates.map((tpl) => {
            const route = getTemplateRoute(tpl.id)
            const isDisabled = !route

            return (
              <Card
                key={tpl.id}
                padding="none"
                variant={isDisabled ? 'default' : 'interactive'}
                className={cn('overflow-hidden', !isDisabled && tpl.borderClass, isDisabled && 'opacity-60')}
              >
                <button
                  type="button"
                  disabled={isDisabled}
                  onClick={() => {
                    if (!route) return
                    navigate(route)
                  }}
                  className={cn(
                    'w-full h-full text-left p-4 @md:p-6 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500/40 focus-visible:ring-offset-2 focus-visible:ring-offset-white dark:focus-visible:ring-offset-slate-900',
                    isDisabled && 'cursor-not-allowed'
                  )}
                >
                  <div className="flex items-start gap-4">
                    <div
                      className={`size-12 rounded-xl ${tpl.bgClass} ${tpl.colorClass} flex items-center justify-center border border-slate-200/60 dark:border-slate-700/60`}
                    >
                      <tpl.icon size={22} strokeWidth={2} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <h3 className="text-subtitle font-bold text-slate-900 dark:text-slate-100 truncate">{tpl.title}</h3>
                        <span className="text-legal px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 font-semibold">
                          {tpl.subtitle}
                        </span>
                      </div>
                      <p className="mt-1 text-body-sm text-slate-500 dark:text-slate-400 leading-relaxed">{tpl.desc}</p>
                    </div>
                  </div>
                </button>
              </Card>
            )
          })}
        </div>
      </section>

      <section>
        <div className="flex items-center justify-between mb-4 @md:mb-5">
          <h2 className="text-legal font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-widest flex items-center gap-2">
            <Clock size={14} className="text-brand-500" />
            Resume Session
          </h2>
          <div className="flex items-center space-x-4">
            <div className="relative group w-64">
              <div className="absolute -inset-0.5 bg-gradient-to-r from-slate-200 to-slate-100 rounded-lg blur opacity-50 group-hover:opacity-100 transition duration-200 dark:from-slate-800 dark:to-slate-900"></div>
              <div className="relative flex items-center bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 shadow-sm h-9">
                <Search className="ml-3 text-slate-400 group-focus-within:text-brand-500 transition-colors" size={14} />
                <input
                  type="text"
                  placeholder="Jump to file..."
                  className="w-full h-full pl-2 pr-3 text-caption bg-transparent outline-none placeholder:text-slate-400 dark:placeholder:text-slate-500 text-slate-700 dark:text-slate-200"
                />
                <div className="absolute right-2 flex items-center space-x-1">
                  <kbd className="hidden @md:inline-block px-1.5 py-px bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded text-nano font-sans font-medium text-slate-400">⌘K</kbd>
                </div>
              </div>
            </div>

            <button className="text-caption font-semibold text-slate-400 hover:text-brand-600 flex items-center transition-colors group">
              View all files
              <ChevronRight size={12} className="ml-1 group-hover:translate-x-0.5 transition-transform" />
            </button>
          </div>
        </div>

        <Card padding="none" variant="default" className="overflow-hidden flex flex-col @md:h-[28rem]">
          <div className="flex-1 min-h-0 overflow-auto custom-scrollbar">
            <table className="w-full text-left">
              <thead className="sticky top-0 z-10 bg-slate-50/50 dark:bg-slate-800/40 border-b border-slate-100 dark:border-slate-800 text-micro text-slate-400 uppercase tracking-wider font-semibold">
                <tr>
                  <th className="px-6 py-4 font-bold">File Name</th>
                  <th className="px-6 py-4 font-bold">Location</th>
                  <th className="px-6 py-4 font-bold">Git Context</th>
                  <th className="px-6 py-4 font-bold text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50 dark:divide-slate-800">
                {recentFiles.map((file) => {
                  const route = getFileRoute(file.type)
                  const isDisabled = !route

                  return (
                    <tr
                      key={file.id}
                      onClick={() => {
                        if (!route) return
                        navigate(route)
                      }}
                      className={cn(
                        'group transition-colors',
                        isDisabled ? 'cursor-default opacity-60' : 'cursor-pointer hover:bg-slate-50/60 dark:hover:bg-slate-800/40'
                      )}
                    >
                      <td className="px-6 py-4">
                        <div className="flex items-center">
                          <FileIcon type={file.type} />
                          <div className="ml-4">
                            <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 group-hover:text-brand-600 transition-colors mb-0.5">
                              {file.name}
                            </div>
                            <div className="flex items-center text-micro text-slate-400">
                              <span>{file.size}</span>
                              <span className="mx-1.5 text-slate-300 dark:text-slate-700">•</span>
                              <span>{file.updated}</span>
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                      <div className="flex items-center text-caption text-slate-500 dark:text-slate-300 bg-slate-50/80 dark:bg-slate-800/40 px-2 py-1.5 rounded-md border border-slate-100 dark:border-slate-700 w-52 truncate">
                          <span className="text-slate-300 dark:text-slate-600 mr-1.5">/</span>
                          {file.path}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center space-x-2">
                          <GitBranch size={12} className="text-slate-400" />
                          <span className="text-caption text-slate-600 dark:text-slate-300 font-medium">{file.branch}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        {file.status === 'modified' && (
                          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-micro font-bold bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-100/50 dark:border-amber-500/20 shadow-sm">
                            <span className="w-1.5 h-1.5 rounded-full bg-amber-500 mr-1.5 animate-pulse"></span> Modified
                          </span>
                        )}
                        {file.status === 'synced' && (
                          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-micro font-bold bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-100/50 dark:border-emerald-500/20">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mr-1.5"></span> Synced
                          </span>
                        )}
                        {file.status === 'untracked' && (
                          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-micro font-bold bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200/50 dark:border-slate-700/60">
                            <span className="w-1.5 h-1.5 rounded-full bg-slate-400 mr-1.5"></span> Untracked
                          </span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="px-6 py-3 bg-slate-50/30 dark:bg-slate-800/20 border-t border-slate-100 dark:border-slate-800 text-micro text-slate-400 font-medium text-center hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors cursor-pointer uppercase tracking-widest">
            Load More Files
          </div>
        </Card>
      </section>
    </div>
  )
}
