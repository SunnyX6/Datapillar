import { Book, MapPin } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import type { TrackingTab } from '../utils/types'

interface DataTrackingTabsProps {
  activeTab: TrackingTab
  onChange: (tab: TrackingTab) => void
}

export function DataTrackingTabs({ activeTab, onChange }: DataTrackingTabsProps) {
  const tabs: Array<{ id: TrackingTab; label: string; icon: typeof MapPin }> = [
    { id: 'PLAN', label: '埋点方案 (Tracking Plan)', icon: MapPin },
    { id: 'LIBRARY', label: '元事件库 (Event Library)', icon: Book }
  ]

  return (
    <div className="inline-flex items-center gap-1 bg-slate-100 dark:bg-slate-800/70 p-1 rounded-xl border border-slate-200 dark:border-slate-700">
      {tabs.map((tab) => {
        const isActive = activeTab === tab.id
        return (
          <button
            key={tab.id}
            type="button"
            onClick={() => onChange(tab.id)}
            className={`px-4 py-2 text-caption font-bold rounded-lg transition-all flex items-center ${
              isActive
                ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-900 dark:text-white'
                : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
            }`}
          >
            <tab.icon size={iconSizeToken.small} className="mr-2" />
            {tab.label}
          </button>
        )
      })}
    </div>
  )
}
