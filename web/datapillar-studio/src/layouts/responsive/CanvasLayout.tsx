import { type ReactNode, useState, useCallback } from 'react'
import { cn } from '@/lib/utils'
import { panelWidthClassMap, panelHeightClassMap } from '@/design-tokens/dimensions'

type PanelWidth = 'narrow' | 'normal'
type ResponsiveBehavior = 'collapse' | 'stack' | 'drawer'

interface CanvasLayoutProps {
  /** 全屏画布内容（必填） */
  canvas: ReactNode
  /** 左侧浮动面板（可选） */
  leftPanel?: ReactNode
  /** 右侧浮动面板（可选） */
  rightPanel?: ReactNode
  /** 底部工具栏（可选） */
  bottomBar?: ReactNode
  /** 顶部状态栏（可选） */
  topBar?: ReactNode
  /** 面板宽度，默认 narrow(320px) */
  panelWidth?: PanelWidth
  /** 受限视口行为（<1440px），默认 collapse */
  responsiveBehavior?: ResponsiveBehavior
  /** 面板初始折叠状态 */
  defaultCollapsed?: {
    left?: boolean
    right?: boolean
  }
  /** 画布背景色 */
  canvasBackground?: string
  /** 自定义类名 */
  className?: string
}

/**
 * CanvasLayout：全屏画布 + 浮动面板布局
 *
 * 适用场景：知识图谱、工作流画布、数据可视化等全屏交互页面
 *
 * 响应式行为（视口 <1440px）：
 * - collapse: 面板折叠为侧边占位，点击展开
 * - stack: 面板堆叠到画布下方
 * - drawer: 面板隐藏，通过触发器显示
 */
export function CanvasLayout({
  canvas,
  leftPanel,
  rightPanel,
  bottomBar,
  topBar,
  panelWidth = 'narrow',
  responsiveBehavior = 'collapse',
  defaultCollapsed = {},
  canvasBackground,
  className
}: CanvasLayoutProps) {
  const [leftCollapsed, setLeftCollapsed] = useState(defaultCollapsed.left ?? false)
  const [rightCollapsed, setRightCollapsed] = useState(defaultCollapsed.right ?? true)

  const toggleLeft = useCallback(() => setLeftCollapsed(prev => !prev), [])
  const toggleRight = useCallback(() => setRightCollapsed(prev => !prev), [])

  const panelWidthClass = panelWidthClassMap[panelWidth]
  const panelHeightClass = panelHeightClassMap.limited

  // 根据 responsiveBehavior 生成响应式类名
  return (
    <div
      className={cn(
        'relative h-full w-full overflow-hidden @container',
        className
      )}
    >
      {/* 画布层 */}
      <div
        className="absolute inset-0"
        style={canvasBackground ? { backgroundColor: canvasBackground } : undefined}
      >
        {canvas}
      </div>

      {/* 浮动层容器 */}
      <div className="absolute inset-0 pointer-events-none">
        {/* 顶部状态栏 */}
        {topBar && (
          <div className="absolute top-6 left-0 right-0 flex justify-center z-30 pointer-events-auto">
            {topBar}
          </div>
        )}

        {/* 左侧面板 */}
        {leftPanel && (
          <CanvasPanel
            position="left"
            collapsed={leftCollapsed}
            onToggle={toggleLeft}
            responsiveBehavior={responsiveBehavior}
            panelWidthClass={panelWidthClass}
            panelHeightClass={panelHeightClass}
          >
            {leftPanel}
          </CanvasPanel>
        )}

        {/* 右侧面板 */}
        {rightPanel && (
          <CanvasPanel
            position="right"
            collapsed={rightCollapsed}
            onToggle={toggleRight}
            responsiveBehavior={responsiveBehavior}
            panelWidthClass={panelWidthClass}
            panelHeightClass={panelHeightClass}
          >
            {rightPanel}
          </CanvasPanel>
        )}

        {/* 底部工具栏 */}
        {bottomBar && (
          <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-40 pointer-events-auto">
            {bottomBar}
          </div>
        )}
      </div>
    </div>
  )
}

interface CanvasPanelProps {
  children: ReactNode
  position: 'left' | 'right'
  collapsed: boolean
  onToggle: () => void
  responsiveBehavior: ResponsiveBehavior
  panelWidthClass: string
  panelHeightClass: string
}

function CanvasPanel({
  children,
  position,
  collapsed,
  onToggle,
  responsiveBehavior,
  panelWidthClass,
  panelHeightClass
}: CanvasPanelProps) {
  // collapse 模式：大屏正常显示，小屏幕折叠
  if (responsiveBehavior === 'collapse') {
    return (
      <>
        {/* 大屏幕：完整面板 */}
        <div
          className={cn(
            'absolute z-50 pointer-events-auto',
            'hidden @[1440px]:block',
            position === 'left'
              ? 'left-6 top-1/2 -translate-y-1/2'
              : 'right-6 top-1/2 -translate-y-1/2',
            panelWidthClass,
            panelHeightClass
          )}
        >
          {children}
        </div>

        {/* 小屏幕：折叠状态 */}
        <div
          className={cn(
            'absolute z-50 pointer-events-auto',
            '@[1440px]:hidden',
            position === 'left'
              ? 'left-4 top-1/2 -translate-y-1/2'
              : 'right-4 top-1/2 -translate-y-1/2'
          )}
        >
          {collapsed ? (
            <CollapsedPanelTrigger position={position} onToggle={onToggle} />
          ) : (
            <div className={cn(panelWidthClass, panelHeightClass, 'relative')}>
              <button
                type="button"
                onClick={onToggle}
                className="absolute -top-2 -right-2 z-10 size-6 rounded-full bg-slate-800 border border-white/10 text-slate-400 hover:text-white flex items-center justify-center"
                aria-label="收起面板"
              >
                <svg className="size-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
              {children}
            </div>
          )}
        </div>
      </>
    )
  }

  // stack 模式：大屏浮动，小屏堆叠
  if (responsiveBehavior === 'stack') {
    return (
      <div
        className={cn(
          'pointer-events-auto',
          // 小屏幕：相对定位，堆叠在画布区域内
          'relative w-full p-4',
          // 大屏幕：绝对定位，浮动在画布上
          '@[1440px]:absolute @[1440px]:w-auto @[1440px]:p-0',
          position === 'left'
            ? '@[1440px]:left-6 @[1440px]:top-1/2 @[1440px]:-translate-y-1/2'
            : '@[1440px]:right-6 @[1440px]:top-1/2 @[1440px]:-translate-y-1/2',
          '@[1440px]:z-50'
        )}
      >
        <div className={cn('@[1440px]:' + panelWidthClass, '@[1440px]:' + panelHeightClass)}>
          {children}
        </div>
      </div>
    )
  }

  // drawer 模式：大屏浮动，小屏抽屉
  return (
    <>
      {/* 大屏幕：完整面板 */}
      <div
        className={cn(
          'absolute z-50 pointer-events-auto',
          'hidden @[1440px]:block',
          position === 'left'
            ? 'left-6 top-1/2 -translate-y-1/2'
            : 'right-6 top-1/2 -translate-y-1/2',
          panelWidthClass,
          panelHeightClass
        )}
      >
        {children}
      </div>

      {/* 小屏幕：抽屉触发器 */}
      <div
        className={cn(
          'absolute z-50 pointer-events-auto',
          '@[1440px]:hidden',
          position === 'left'
            ? 'left-4 top-1/2 -translate-y-1/2'
            : 'right-4 top-1/2 -translate-y-1/2'
        )}
      >
        {collapsed ? (
          <CollapsedPanelTrigger position={position} onToggle={onToggle} />
        ) : (
          <div
            className={cn(
              panelWidthClassMap.narrow,
              'fixed inset-y-0 z-[100] bg-slate-900/95 backdrop-blur-xl border-white/10 shadow-2xl',
              position === 'left' ? 'left-0 border-r' : 'right-0 border-l'
            )}
          >
            <button
              type="button"
              onClick={onToggle}
              className="absolute top-4 right-4 size-8 rounded-lg bg-white/5 text-slate-400 hover:text-white flex items-center justify-center"
              aria-label="关闭抽屉"
            >
              <svg className="size-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
            <div className="h-full overflow-y-auto p-4 pt-14">
              {children}
            </div>
          </div>
        )}
      </div>
    </>
  )
}

interface CollapsedPanelTriggerProps {
  position: 'left' | 'right'
  onToggle: () => void
}

function CollapsedPanelTrigger({ position, onToggle }: CollapsedPanelTriggerProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={cn(
        'size-12 rounded-xl backdrop-blur-xl shadow-lg flex items-center justify-center transition-colors',
        'bg-slate-900/70 border border-white/10 text-slate-400 hover:text-white hover:border-white/20'
      )}
      aria-label={position === 'left' ? '展开左侧面板' : '展开右侧面板'}
    >
      <svg className="size-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        {position === 'left' ? (
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        ) : (
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        )}
      </svg>
    </button>
  )
}
