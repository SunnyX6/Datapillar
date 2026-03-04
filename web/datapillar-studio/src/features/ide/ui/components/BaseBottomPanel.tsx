/**
 * Bottom panel skeleton component
 * Available in foldable,Bottom panel structure that can be dragged and adjusted to height
 */

import { useState,useEffect,type ReactNode } from 'react'
import { ChevronUp } from 'lucide-react'
import { motion } from 'framer-motion'

export interface BottomPanelTab {
 id:string
 label:string
 icon?: ReactNode
}

interface BaseBottomPanelProps {
 /** Tab list */
 tabs:BottomPanelTab[]
 /** currently active Tab ID */
 activeTabId:string
 /** Tab Switch callback */
 onTabChange:(id:string) => void
 /** Content on the left side of the status bar */
 statusLeft?: ReactNode
 /** Content on the right side of the status bar */
 statusRight?: ReactNode
 /** Tab content area */
 children:ReactNode
 /** Default height */
 defaultHeight?: number
 /** minimum height */
 minHeight?: number
 /** maximum height ratio(Relative to window height) */
 maxHeightRatio?: number
 /** Is it foldable?*/
 collapsible?: boolean
 /** Whether to collapse by default */
 defaultCollapsed?: boolean
 /** External control of folded state */
 collapsed?: boolean
 /** Collapse state change callback */
 onCollapsedChange?: (collapsed:boolean) => void
}

export function BaseBottomPanel({
 tabs,activeTabId,onTabChange,statusLeft,statusRight,children,defaultHeight = 320,minHeight = 60,maxHeightRatio = 0.7,collapsible = true,defaultCollapsed = true,collapsed:controlledCollapsed,onCollapsedChange,}:BaseBottomPanelProps) {
 const [internalCollapsed,setInternalCollapsed] = useState(defaultCollapsed)
 const [height,setHeight] = useState(defaultHeight)
 const [isResizing,setIsResizing] = useState(false)

 // Supports controlled and uncontrolled modes
 const isCollapsed = controlledCollapsed?? internalCollapsed

 // Handling fold state changes
 const handleCollapsedChange = (collapsed:boolean) => {
 setInternalCollapsed(collapsed)
 onCollapsedChange?.(collapsed)
 }

 // Drag and drop to start
 const handleResizeStart = (e:React.MouseEvent) => {
 e.preventDefault()
 if (isCollapsed) return
 setIsResizing(true)
 document.body.style.userSelect = 'none'
 document.body.style.cursor = 'row-resize'
 }

 // Drag and drop process
 useEffect(() => {
 if (!isResizing) return

 const handleMouseMove = (e:MouseEvent) => {
 const newHeight = window.innerHeight - e.clientY - 28 // 28 is the status bar height
 const maxHeight = window.innerHeight * maxHeightRatio
 if (newHeight >= minHeight && newHeight <= maxHeight) {
 setHeight(newHeight)
 }
 }

 const handleMouseUp = () => {
 setIsResizing(false)
 document.body.style.userSelect = ''
 document.body.style.cursor = ''
 }

 window.addEventListener('mousemove',handleMouseMove)
 window.addEventListener('mouseup',handleMouseUp)

 return () => {
 window.removeEventListener('mousemove',handleMouseMove)
 window.removeEventListener('mouseup',handleMouseUp)
 }
 },[isResizing,minHeight,maxHeightRatio])

 // Tab Automatically expand on click
 const handleTabClick = (tabId:string) => {
 onTabChange(tabId)
 if (isCollapsed) {
 handleCollapsedChange(false)
 }
 }

 return (<div className="flex flex-col shrink-0 relative">
 {/* Drag mask layer */}
 {isResizing && <div className="fixed inset-0 z-[9999] cursor-row-resize" />}

 {/* content area */}
 <div
 style={{ '--panel-content-height':`${isCollapsed?0:height}px` } as React.CSSProperties}
 className={`h-[var(--panel-content-height)] bg-white dark:bg-slate-900 overflow-hidden relative shadow-[0_-4px_12px_rgba(0,0,0,0.02)] dark:shadow-[0_-4px_12px_rgba(0,0,0,0.2)] ${
 isResizing?'':'transition-[height] duration-150 ease-out'
 }`}
 >
 {/* Drag strip */}
 <div
 onMouseDown={handleResizeStart}
 className={`h-px w-full absolute top-0 z-50 bg-slate-200 dark:bg-slate-700 transition-colors ${
 isCollapsed?'':'cursor-row-resize hover:bg-indigo-500'
 }`}
 />

 {/* Tab column - at the top of the content area */}
 <div className="h-7 flex items-center border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 shrink-0">
 {tabs.map((tab) => {
 const isActive = activeTabId === tab.id
 return (<button
 key={tab.id}
 onClick={() => handleTabClick(tab.id)}
 className={`h-full px-2 flex items-center gap-1.5 text-tiny font-bold uppercase tracking-wider relative transition-all ${
 isActive?'text-indigo-600 dark:text-indigo-400 bg-slate-50 dark:bg-slate-800':'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'
 }`}
 >
 {tab.icon}
 {tab.label}
 {isActive && (<div className="absolute bottom-0 left-0 right-0 h-0.5 bg-indigo-500" />)}
 </button>)
 })}
 </div>

 {/* content */}
 <div className="h-[calc(100%-1.75rem)] overflow-auto bg-white dark:bg-slate-900">
 {children}
 </div>
 </div>

 {/* status bar */}
 <div className="h-7 flex items-center justify-between px-2 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-700 shrink-0 z-50">
 <div className="flex items-center gap-2 h-full">
 {/* Collapse button */}
 {collapsible && (<button
 onClick={() => handleCollapsedChange(!isCollapsed)}
 aria-label={isCollapsed?'Expand bottom panel':'Collapse bottom panel'}
 className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-all text-slate-500 dark:text-slate-400"
 >
 <motion.div animate={{ rotate:isCollapsed?0:180 }}>
 <ChevronUp size={12} />
 </motion.div>
 </button>)}

 {/* Left side of status bar */}
 {statusLeft}
 </div>

 {/* Right side of status bar */}
 <div className="flex items-center gap-3">
 {statusRight}
 </div>
 </div>
 </div>)
}
