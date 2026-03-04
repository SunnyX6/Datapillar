import { type ReactNode,useState,useCallback } from 'react'
import { cn } from '@/utils'
import { panelWidthClassMap,panelHeightClassMap } from '@/design-tokens/dimensions'

type PanelWidth = 'narrow' | 'normal'
type ResponsiveBehavior = 'collapse' | 'stack' | 'drawer'

interface CanvasLayoutProps {
 /** Full screen canvas content(Required) */
 canvas:ReactNode
 /** left floating panel(Optional) */
 leftPanel?: ReactNode
 /** Floating panel on the right(Optional) */
 rightPanel?: ReactNode
 /** bottom toolbar(Optional) */
 bottomBar?: ReactNode
 /** top status bar(Optional) */
 topBar?: ReactNode
 /** Panel width,Default narrow(320px) */
 panelWidth?: PanelWidth
 /** Restricted viewport behavior(<1440px),Default collapse */
 responsiveBehavior?: ResponsiveBehavior
 /** Panel initial folded state */
 defaultCollapsed?: {
 left?: boolean
 right?: boolean
 }
 /** Canvas background color */
 canvasBackground?: string
 /** Custom class name */
 className?: string
}

/**
 * CanvasLayout:full screen canvas + floating panel layout
 *
 * Applicable scenarios:Knowledge graph,workflow canvas,Full-screen interactive pages such as data visualization
 *
 * Responsive behavior(viewport <1440px):* - collapse:The panel folds to take up space on the side,Click to expand
 * - stack:Panels stacked below canvas
 * - drawer:Panel hidden,Display via trigger
 */
export function CanvasLayout({
 canvas,leftPanel,rightPanel,bottomBar,topBar,panelWidth = 'narrow',responsiveBehavior = 'collapse',defaultCollapsed = {},canvasBackground,className
}:CanvasLayoutProps) {
 const [leftCollapsed,setLeftCollapsed] = useState(defaultCollapsed.left?? false)
 const [rightCollapsed,setRightCollapsed] = useState(defaultCollapsed.right?? true)

 const toggleLeft = useCallback(() => setLeftCollapsed(prev =>!prev),[])
 const toggleRight = useCallback(() => setRightCollapsed(prev =>!prev),[])

 const panelWidthClass = panelWidthClassMap[panelWidth]
 const panelHeightClass = panelHeightClassMap.limited

 // According to responsiveBehavior Generate responsive class names
 return (<div
 className={cn('relative h-full w-full overflow-hidden @container',className)}
 >
 {/* canvas layer */}
 <div
 className="absolute inset-0"
 style={canvasBackground?{ backgroundColor:canvasBackground }:undefined}
 >
 {canvas}
 </div>

 {/* floating layer container */}
 <div className="absolute inset-0 pointer-events-none">
 {/* top status bar */}
 {topBar && (<div className="absolute top-6 left-0 right-0 flex justify-center z-30 pointer-events-auto">
 {topBar}
 </div>)}

 {/* left panel */}
 {leftPanel && (<CanvasPanel
 position="left"
 collapsed={leftCollapsed}
 onToggle={toggleLeft}
 responsiveBehavior={responsiveBehavior}
 panelWidthClass={panelWidthClass}
 panelHeightClass={panelHeightClass}
 >
 {leftPanel}
 </CanvasPanel>)}

 {/* right panel */}
 {rightPanel && (<CanvasPanel
 position="right"
 collapsed={rightCollapsed}
 onToggle={toggleRight}
 responsiveBehavior={responsiveBehavior}
 panelWidthClass={panelWidthClass}
 panelHeightClass={panelHeightClass}
 >
 {rightPanel}
 </CanvasPanel>)}

 {/* bottom toolbar */}
 {bottomBar && (<div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-40 pointer-events-auto">
 {bottomBar}
 </div>)}
 </div>
 </div>)
}

interface CanvasPanelProps {
 children:ReactNode
 position:'left' | 'right'
 collapsed:boolean
 onToggle:() => void
 responsiveBehavior:ResponsiveBehavior
 panelWidthClass:string
 panelHeightClass:string
}

function CanvasPanel({
 children,position,collapsed,onToggle,responsiveBehavior,panelWidthClass,panelHeightClass
}:CanvasPanelProps) {
 // collapse mode:Normal display on large screen,Small screen folding
 if (responsiveBehavior === 'collapse') {
 return (<>
 {/* big screen:full panel */}
 <div
 className={cn('absolute z-50 pointer-events-auto','hidden @[1440px]:block',position === 'left'?'left-6 top-1/2 -translate-y-1/2':'right-6 top-1/2 -translate-y-1/2',panelWidthClass,panelHeightClass)}
 >
 {children}
 </div>

 {/* small screen:folded state */}
 <div
 className={cn('absolute z-50 pointer-events-auto','@[1440px]:hidden',position === 'left'?'left-4 top-1/2 -translate-y-1/2':'right-4 top-1/2 -translate-y-1/2')}
 >
 {collapsed?(<CollapsedPanelTrigger position={position} onToggle={onToggle} />):(<div className={cn(panelWidthClass,panelHeightClass,'relative')}>
 <button
 type="button"
 onClick={onToggle}
 className="absolute -top-2 -right-2 z-10 size-6 rounded-full bg-slate-800 border border-white/10 text-slate-400 hover:text-white flex items-center justify-center"
 aria-label="Collapse panel"
 >
 <svg className="size-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
 </svg>
 </button>
 {children}
 </div>)}
 </div>
 </>)
 }

 // stack mode:Large screen floating,Small screen stacking
 if (responsiveBehavior === 'stack') {
 return (<div
 className={cn('pointer-events-auto',// small screen:relative positioning,stacked within canvas area
 'relative w-full p-4',// big screen:absolute positioning,float on canvas
 '@[1440px]:absolute @[1440px]:w-auto @[1440px]:p-0',position === 'left'?'@[1440px]:left-6 @[1440px]:top-1/2 @[1440px]:-translate-y-1/2':'@[1440px]:right-6 @[1440px]:top-1/2 @[1440px]:-translate-y-1/2','@[1440px]:z-50')}
 >
 <div className={cn('@[1440px]:' + panelWidthClass,'@[1440px]:' + panelHeightClass)}>
 {children}
 </div>
 </div>)
 }

 // drawer mode:Large screen floating,Small screen drawer
 return (<>
 {/* big screen:full panel */}
 <div
 className={cn('absolute z-50 pointer-events-auto','hidden @[1440px]:block',position === 'left'?'left-6 top-1/2 -translate-y-1/2':'right-6 top-1/2 -translate-y-1/2',panelWidthClass,panelHeightClass)}
 >
 {children}
 </div>

 {/* small screen:drawer trigger */}
 <div
 className={cn('absolute z-50 pointer-events-auto','@[1440px]:hidden',position === 'left'?'left-4 top-1/2 -translate-y-1/2':'right-4 top-1/2 -translate-y-1/2')}
 >
 {collapsed?(<CollapsedPanelTrigger position={position} onToggle={onToggle} />):(<div
 className={cn(panelWidthClassMap.narrow,'fixed inset-y-0 z-[100] bg-slate-900/95 backdrop-blur-xl border-white/10 shadow-2xl',position === 'left'?'left-0 border-r':'right-0 border-l')}
 >
 <button
 type="button"
 onClick={onToggle}
 className="absolute top-4 right-4 size-8 rounded-lg bg-white/5 text-slate-400 hover:text-white flex items-center justify-center"
 aria-label="close drawer"
 >
 <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
 </svg>
 </button>
 <div className="h-full overflow-y-auto p-4 pt-14">
 {children}
 </div>
 </div>)}
 </div>
 </>)
}

interface CollapsedPanelTriggerProps {
 position:'left' | 'right'
 onToggle:() => void
}

function CollapsedPanelTrigger({ position,onToggle }:CollapsedPanelTriggerProps) {
 return (<button
 type="button"
 onClick={onToggle}
 className={cn('size-12 rounded-xl backdrop-blur-xl shadow-lg flex items-center justify-center transition-colors','bg-slate-900/70 border border-white/10 text-slate-400 hover:text-white hover:border-white/20')}
 aria-label={position === 'left'?'Expand left panel':'Expand right panel'}
 >
 <svg className="size-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
 {position === 'left'?(<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />):(<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />)}
 </svg>
 </button>)
}
