import type { TrackingStatus } from './types'

export const STATUS_STYLES: Record<TrackingStatus, { label: string; className: string }> = {
  implemented: {
    label: '已上线',
    className:
      'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
  },
  tested: {
    label: '已测试',
    className:
      'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20'
  },
  planned: {
    label: '规划中',
    className:
      'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
  }
}

export const DOMAIN_ACCENTS: Record<
  string,
  {
    bar: string
    badge: string
    icon: string
  }
> = {
  Trade: {
    bar: 'bg-blue-500',
    badge: 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20',
    icon: 'bg-blue-50 text-blue-600 dark:bg-blue-500/10 dark:text-blue-300'
  },
  Product: {
    bar: 'bg-emerald-500',
    badge: 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20',
    icon: 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300'
  },
  Marketing: {
    bar: 'bg-amber-500',
    badge: 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20',
    icon: 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300'
  },
  Growth: {
    bar: 'bg-purple-500',
    badge: 'bg-purple-50 text-purple-700 border-purple-100 dark:bg-purple-500/10 dark:text-purple-200 dark:border-purple-500/20',
    icon: 'bg-purple-50 text-purple-600 dark:bg-purple-500/10 dark:text-purple-300'
  },
  Tech: {
    bar: 'bg-slate-500',
    badge: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
    icon: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
  }
}

export const DEFAULT_DOMAIN_ACCENT = {
  bar: 'bg-brand-500',
  badge: 'bg-brand-50 text-brand-700 border-brand-100 dark:bg-brand-500/10 dark:text-brand-200 dark:border-brand-500/20',
  icon: 'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300'
}
