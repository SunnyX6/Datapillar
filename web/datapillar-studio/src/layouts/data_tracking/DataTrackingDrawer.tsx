import { createPortal } from 'react-dom'
import { drawerWidthClassMap } from '@/design-tokens/dimensions'
import type { DrawerMode } from './types'
import { DataTrackingDrawerHeader } from './DataTrackingDrawerHeader'
import { SchemaDrawerBody } from './SchemaDrawerBody'
import { TrackingDrawerBody } from './TrackingDrawerBody'

interface DataTrackingDrawerProps {
  isOpen: boolean
  mode: DrawerMode
  onClose: () => void
}

export function DataTrackingDrawer({ isOpen, mode, onClose }: DataTrackingDrawerProps) {
  if (!isOpen) return null
  if (typeof document === 'undefined') return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex justify-end">
      <button
        type="button"
        className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"
        onClick={onClose}
        aria-label="关闭埋点抽屉"
      />
      <aside
        className={`relative h-full ${drawerWidthClassMap.wide} bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col animate-in slide-in-from-right duration-300`}
      >
        <DataTrackingDrawerHeader mode={mode} onClose={onClose} />
        <div className="flex-1 min-h-0 overflow-hidden bg-slate-50 dark:bg-[#0B1120]">
          {mode === 'SCHEMA' ? <SchemaDrawerBody /> : <TrackingDrawerBody />}
        </div>
      </aside>
    </div>,
    document.body
  )
}
