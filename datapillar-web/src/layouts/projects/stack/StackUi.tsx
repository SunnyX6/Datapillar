import { AlertCircle, CheckCircle2, Pause, Play, RefreshCw } from 'lucide-react'

const STATUS_CONFIG: Record<string, { label: string; tone: string; icon: typeof CheckCircle2 }> = {
  healthy: {
    label: 'Healthy',
    tone: 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20',
    icon: CheckCircle2
  },
  success: {
    label: 'Success',
    tone: 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20',
    icon: CheckCircle2
  },
  running: {
    label: 'Running',
    tone: 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20',
    icon: RefreshCw
  },
  warning: {
    label: 'Warning',
    tone: 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20',
    icon: AlertCircle
  },
  error: {
    label: 'Error',
    tone: 'bg-red-50 text-red-700 border-red-100 dark:bg-red-500/10 dark:text-red-200 dark:border-red-500/20',
    icon: AlertCircle
  },
  failed: {
    label: 'Failed',
    tone: 'bg-red-50 text-red-700 border-red-100 dark:bg-red-500/10 dark:text-red-200 dark:border-red-500/20',
    icon: AlertCircle
  },
  paused: {
    label: 'Paused',
    tone: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800/60 dark:text-slate-200 dark:border-slate-700',
    icon: Pause
  },
  retried: {
    label: 'Retried',
    tone: 'bg-purple-50 text-purple-700 border-purple-100 dark:bg-purple-500/10 dark:text-purple-200 dark:border-purple-500/20',
    icon: Play
  }
}

export function StatusBadge({ status }: { status: string }) {
  const config = STATUS_CONFIG[status] ?? {
    label: status,
    tone: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800/60 dark:text-slate-200 dark:border-slate-700',
    icon: AlertCircle
  }
  const Icon = config.icon

  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-micro font-bold border ${config.tone}`}>
      <Icon size={12} className={config.icon === RefreshCw ? 'mr-1 animate-spin' : 'mr-1'} />
      {config.label}
    </span>
  )
}
