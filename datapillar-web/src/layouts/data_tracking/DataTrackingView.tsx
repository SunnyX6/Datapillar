import { useState } from 'react'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import type { DrawerMode, TrackingTab } from './types'
import { DataTrackingHeader } from './DataTrackingHeader'
import { DataTrackingTabs } from './DataTrackingTabs'
import { DataTrackingDrawer } from './DataTrackingDrawer'
import { EventLibraryGrid } from './EventLibraryGrid'
import { TrackingPlanTable } from './TrackingPlanTable'

export function DataTrackingView() {
  const [activeTab, setActiveTab] = useState<TrackingTab>('LIBRARY')
  const [isDrawerOpen, setIsDrawerOpen] = useState(false)
  const [drawerMode, setDrawerMode] = useState<DrawerMode>('TRACKING')

  const openDrawer = (mode: DrawerMode) => {
    setDrawerMode(mode)
    setIsDrawerOpen(true)
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-slate-50 dark:bg-[#020617]">
      <div className="flex-1 overflow-y-auto custom-scrollbar">
        <div className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
          <div className={`px-4 @md:px-6 @xl:px-8 pt-6 pb-5 ${contentMaxWidthClassMap.full} mx-auto space-y-5`}>
            <DataTrackingHeader
              onCreateSchema={() => openDrawer('SCHEMA')}
              onCreateTracking={() => openDrawer('TRACKING')}
            />
            <DataTrackingTabs activeTab={activeTab} onChange={setActiveTab} />
          </div>
        </div>

        <div className={`px-4 @md:px-6 @xl:px-8 py-6 ${contentMaxWidthClassMap.full} mx-auto`}>
          {activeTab === 'PLAN' ? (
            <TrackingPlanTable />
          ) : (
            <EventLibraryGrid onCreateSchema={() => openDrawer('SCHEMA')} />
          )}
        </div>
      </div>

      <DataTrackingDrawer
        isOpen={isDrawerOpen}
        mode={drawerMode}
        onClose={() => setIsDrawerOpen(false)}
      />
    </div>
  )
}
