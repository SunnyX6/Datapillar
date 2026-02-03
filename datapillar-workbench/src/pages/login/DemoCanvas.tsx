/**
 * 登录页面左侧演示画布
 *
 * 功能：
 * 1. 3D 倾斜屏幕效果
 * 2. 数据流动画（Chaos Streams → Turbine → Beam）
 * 3. 涡轮引擎动画
 * 4. 节点流转动画
 * 5. 实时日志流
 * 6. 打字机输入框
 */

import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import {
  LayoutTemplate,
  ShoppingBag,
  Sparkles,
  Send
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import {
  useScenario,
  useScenarioState,
  useProgressLog,
  ScenarioPhase,
  type WorkflowNode,
  usePageVisibility
} from '@/hooks'
import * as LucideIcons from 'lucide-react'
import { cn } from '@/lib/utils'
import { inputContainerWidthClassMap } from '@/design-tokens/dimensions'
import { useLayout } from '@/layouts/responsive'

const STAGE_BASE_WIDTH = 1200
const STAGE_BASE_HEIGHT = 900
const STAGE_MAX_SCALE = 1.5
const STAGE_SCALE_FACTOR = 1.2
const CENTER_BASE_WIDTH = 520
const CENTER_BASE_HEIGHT = 310
const PANEL_BASE_WIDTH = 280
const PANEL_BASE_HEIGHT = 310
const STAGE_SECTION_GAP = 0
const STAGE_FRAME_CLASS = 'absolute left-1/2 top-[45%] w-[1200px] h-[900px] overflow-visible select-none'
const PANEL_SIZE_CLASS = 'w-[var(--panel-width)] h-[var(--panel-height)]'
const CENTER_SIZE_CLASS = 'w-[var(--center-width)] h-[var(--center-height)]'
const PREVIEW_CARD_SIZE_CLASS = 'w-[500px] h-[280px]'
const NODE_SIZE_CLASS = 'w-[82px] h-[49px]'
const ORB_MAIN_CLASS = 'w-[400px] h-[400px]'
const ORB_RING_CLASS = 'w-[800px] h-[800px]'
const ORB_RING_MID_CLASS = 'w-[94%] h-[94%]'
const ORB_RING_INNER_CLASS = 'w-[75%] h-[75%]'
const ORB_RING_CORE_CLASS = 'w-[45%] h-[45%]'
const ORB_CORE_CLASS = 'w-[100px] h-[100px]'

/**
 * 进度日志组件
 */
interface ProgressLogProps {
  lines: string[]
  progress: number
  tone?: 'indigo' | 'emerald'
}

function ProgressLog({ lines, progress, tone = 'indigo' }: ProgressLogProps) {
  const displayedLines = useProgressLog(lines, progress)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const animationFrameRef = useRef<number | null>(null)
  const targetScrollRef = useRef(0)
  const bulletColor =
    tone === 'emerald' ? 'text-emerald-300/70' : 'text-indigo-300/70'
  const lineToneClass =
    tone === 'emerald' ? 'log-line-emerald' : 'log-line-indigo'

  const scheduleAnimation = useCallback(() => {
    if (animationFrameRef.current !== null) return

    const step = () => {
      const container = containerRef.current
      if (!container) {
        animationFrameRef.current = null
        return
      }

      const currentTop = container.scrollTop
      const targetTop = targetScrollRef.current
      const diff = targetTop - currentTop

      if (Math.abs(diff) < 0.5) {
        container.scrollTop = targetTop
        animationFrameRef.current = null
        return
      }

      container.scrollTop = currentTop + diff * 0.18
      animationFrameRef.current = requestAnimationFrame(step)
    }

    animationFrameRef.current = requestAnimationFrame(step)
  }, [])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    targetScrollRef.current = Math.max(0, container.scrollHeight - container.clientHeight)

    scheduleAnimation()
  }, [displayedLines, scheduleAnimation])

  useEffect(() => {
    return () => {
      if (animationFrameRef.current !== null) {
        cancelAnimationFrame(animationFrameRef.current)
        animationFrameRef.current = null
      }
    }
  }, [])

  return (
    <div className="relative z-10 h-full">
      <div
        ref={containerRef}
        className="log-scroll-container h-full overflow-y-auto pr-0.5 md:pr-1 space-y-2"
      >
        {displayedLines.map((line, i) => (
          <div key={i} className={`log-line flex gap-2 items-start ${lineToneClass}`}>
            <span className={`${bulletColor} select-none font-semibold`}>{`>`}</span>
            <span className="text-micro md:text-xs font-mono tracking-wide">{line}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
interface DemoCanvasProps {
  className?: string
}

/**
 * 演示画布组件
 */
export function DemoCanvas({ className }: DemoCanvasProps) {
  const { t } = useTranslation('login')
  const isPageVisible = usePageVisibility()

  // 左右屏幕旋转状态
  const [leftRotated, setLeftRotated] = useState(false)
  const [rightRotated, setRightRotated] = useState(false)

  // 场景管理
  const { scenario, nextScenario } = useScenario()

  // 场景状态管理
  const state = useScenarioState(scenario, nextScenario, { isActive: isPageVisible })
  const typingBoxRef = useRef<HTMLDivElement | null>(null)
  const beforeCaretText = state.currentInputText.slice(0, state.caretPosition)
  const afterCaretText = state.currentInputText.slice(state.caretPosition)
  const virtualBeforeRef = useRef<HTMLSpanElement | null>(null)
  const { ref: stageContainerRef, scale: stageScale, ready: stageReady, width: _containerWidth, height: _containerHeight } = useLayout<HTMLDivElement>({
    baseWidth: STAGE_BASE_WIDTH,
    baseHeight: STAGE_BASE_HEIGHT,
    scaleFactor: STAGE_SCALE_FACTOR,
    minScale: 0.25,
    maxScale: STAGE_MAX_SCALE
  })

  useEffect(() => {
    const container = typingBoxRef.current
    const virtualBefore = virtualBeforeRef.current
    if (!container || !virtualBefore) return

    const caretOffset = virtualBefore.offsetWidth
    const visibleWidth = container.clientWidth
    const scrollLeft = container.scrollLeft
    const padding = 24

    if (caretOffset - scrollLeft > visibleWidth - padding) {
      container.scrollTo({ left: caretOffset - visibleWidth + padding, behavior: 'smooth' })
    } else if (caretOffset - padding < scrollLeft) {
      container.scrollTo({ left: Math.max(0, caretOffset - padding), behavior: 'smooth' })
    }
  }, [beforeCaretText])

  // 计算连接线路径（使用缩放后的坐标）
  const nodeScaleRatio = 0.82
  const getEdgePath = (sourceNode: WorkflowNode, targetNode: WorkflowNode) => {
    // 先缩放节点位置
    const scaledSourceX = sourceNode.position.x * nodeScaleRatio
    const scaledSourceY = sourceNode.position.y * nodeScaleRatio
    const scaledTargetX = targetNode.position.x * nodeScaleRatio
    const scaledTargetY = targetNode.position.y * nodeScaleRatio

    // 再加上缩放后的偏移量
    const scaledNodeWidth = 100 * nodeScaleRatio  // 82px
    const scaledNodeHeight = 60 * nodeScaleRatio  // 49px

    // 节点容器相对于 SVG 的偏移（节点容器 500px 在 520px 中居中）
    const containerOffsetX = (CENTER_BASE_WIDTH - 500) / 2  // (520 - 500) / 2 = 10px
    const containerOffsetY = -5  // 垂直偏移，根据实际调整

    const sourceX = scaledSourceX + scaledNodeWidth + containerOffsetX  // 右边缘
    const sourceY = scaledSourceY + scaledNodeHeight / 2 + containerOffsetY  // 垂直中心
    const targetX = scaledTargetX + containerOffsetX  // 左边缘
    const targetY = scaledTargetY + scaledNodeHeight / 2 + containerOffsetY  // 垂直中心

    const midX = (sourceX + targetX) / 2

    return `M ${sourceX} ${sourceY} Q ${midX} ${sourceY}, ${midX} ${(sourceY + targetY) / 2} T ${targetX} ${targetY}`
  }

  // 获取图标组件
  const iconLibrary = LucideIcons as Record<string, LucideIcon>
  const getIcon = (iconName: string): LucideIcon => iconLibrary[iconName] ?? ShoppingBag

  // 获取颜色类名
  const getColorClass = (color: string, type: 'border' | 'text' | 'shadow') => {
    const colorMap: Record<string, { border: string; text: string; shadow: string }> = {
      blue: {
        border: 'border-blue-500',
        text: 'text-blue-400',
        shadow: 'shadow-[0_0_25px_rgba(59,130,246,0.3)]'
      },
      pink: {
        border: 'border-pink-500',
        text: 'text-pink-400',
        shadow: 'shadow-[0_0_30px_rgba(236,72,153,0.4)]'
      },
      yellow: {
        border: 'border-yellow-500',
        text: 'text-yellow-400',
        shadow: 'shadow-[0_0_30px_rgba(234,179,8,0.4)]'
      },
      amber: {
        border: 'border-amber-500',
        text: 'text-amber-400',
        shadow: 'shadow-[0_0_30px_rgba(245,158,11,0.4)]'
      },
      orange: {
        border: 'border-orange-500',
        text: 'text-orange-400',
        shadow: 'shadow-[0_0_25px_rgba(249,115,22,0.3)]'
      },
      cyan: {
        border: 'border-cyan-500',
        text: 'text-cyan-400',
        shadow: 'shadow-[0_0_30px_rgba(6,182,212,0.4)]'
      },
      purple: {
        border: 'border-purple-500',
        text: 'text-purple-400',
        shadow: 'shadow-[0_0_30px_rgba(168,85,247,0.4)]'
      },
      green: {
        border: 'border-green-500',
        text: 'text-green-400',
        shadow: 'shadow-[0_0_30px_rgba(34,197,94,0.4)]'
      },
      indigo: {
        border: 'border-indigo-500',
        text: 'text-indigo-400',
        shadow: 'shadow-[0_0_30px_rgba(99,102,241,0.4)]'
      },
      teal: {
        border: 'border-teal-500',
        text: 'text-teal-400',
        shadow: 'shadow-[0_0_30px_rgba(20,184,166,0.4)]'
      },
      slate: {
        border: 'border-slate-500',
        text: 'text-slate-400',
        shadow: 'shadow-[0_0_30px_rgba(100,116,139,0.4)]'
      },
      emerald: {
        border: 'border-emerald-500',
        text: 'text-emerald-400',
        shadow: 'shadow-[0_0_25px_rgba(16,185,129,0.3)]'
      }
    }

    return colorMap[color]?.[type] || colorMap.blue[type]
  }

  const isRadarPhase =
    state.phase === ScenarioPhase.TYPING_INPUT || state.phase === ScenarioPhase.AGENT_ANALYZING
  const radarOpacity =
    state.phase === ScenarioPhase.AGENT_ANALYZING && state.leftLogProgress >= 100
      ? 0
      : isRadarPhase
        ? 1
        : 0
  let radarColor = '#ef4444'
  if (state.phase === ScenarioPhase.AGENT_ANALYZING) {
    const progress = Math.min(state.leftLogProgress / 100, 1)
    const r = Math.floor(239 * (1 - progress) + 16 * progress)
    const g = Math.floor(68 * (1 - progress) + 185 * progress)
    const b = Math.floor(68 * (1 - progress) + 129 * progress)
    radarColor = `rgb(${r},${g},${b})`
  }
  // 动态计算中心点，避免硬编码
  const centerWidth = CENTER_BASE_WIDTH
  const radarCenterX = centerWidth / 2
  const radarCenterY = CENTER_BASE_HEIGHT / 2 - 20

  const radarInboundPaths = [
    `M ${radarCenterX - 200} ${radarCenterY} Q ${radarCenterX - 60} ${radarCenterY} ${radarCenterX} ${radarCenterY}`,
    `M ${radarCenterX + 200} ${radarCenterY} Q ${radarCenterX + 60} ${radarCenterY} ${radarCenterX} ${radarCenterY}`,
    `M ${radarCenterX} ${radarCenterY - 135} Q ${radarCenterX} ${radarCenterY - 45} ${radarCenterX} ${radarCenterY}`,
    `M ${radarCenterX} ${radarCenterY + 135} Q ${radarCenterX} ${radarCenterY + 55} ${radarCenterX} ${radarCenterY}`
  ]
  const radarBlips = [
    { x: radarCenterX + 100, y: radarCenterY - 55, delay: '0s' },
    { x: radarCenterX - 70, y: radarCenterY - 75, delay: '0.6s' },
    { x: radarCenterX + 80, y: radarCenterY + 55, delay: '1.2s' },
    { x: radarCenterX - 30, y: radarCenterY + 65, delay: '1.8s' }
  ]
  const radarRadius = 160
  const sweepHalfAngle = 22
  const toRadians = (deg: number) => (deg * Math.PI) / 180
  const polarToCartesian = (angle: number, radius = radarRadius) => ({
    x: radarCenterX + radius * Math.cos(toRadians(angle)),
    y: radarCenterY + radius * Math.sin(toRadians(angle))
  })
  const sweepStart = polarToCartesian(-sweepHalfAngle)
  const sweepEnd = polarToCartesian(sweepHalfAngle)
  const wedgePath = `M${radarCenterX} ${radarCenterY} L${sweepStart.x.toFixed(2)} ${sweepStart.y.toFixed(2)} A${radarRadius} ${radarRadius} 0 0 1 ${sweepEnd.x.toFixed(2)} ${sweepEnd.y.toFixed(2)} Z`
  const pointerEnd = polarToCartesian(0, radarRadius)
  const centerHeight = CENTER_BASE_HEIGHT
  const panelWidth = PANEL_BASE_WIDTH
  const panelHeight = PANEL_BASE_HEIGHT
  const centerLeft = (STAGE_BASE_WIDTH - centerWidth) / 2
  // 左中右模块共用 STAGE_SECTION_GAP，缩放时仍保持等距
  const leftPanelLeft = centerLeft - STAGE_SECTION_GAP - panelWidth
  const rightPanelLeft = centerLeft + centerWidth + STAGE_SECTION_GAP
  const panelTop = (STAGE_BASE_HEIGHT - panelHeight) / 2 - 60

  return (
    <div
      ref={stageContainerRef}
      className={cn('relative w-full h-full overflow-visible bg-[#02040a]', className)}
    >
      <div
        className={STAGE_FRAME_CLASS}
        style={{
          transform: `translate(-50%, -50%) scale(${stageScale})`,
          transformOrigin: 'center center',
          opacity: stageReady ? 1 : 0
        }}
      >
        {/* 全局样式和动画 */}
        <style>{`
        .bg-grid-pattern {
          background-image: linear-gradient(to right, rgba(99, 102, 241, 0.05) 1px, transparent 1px),
                            linear-gradient(to bottom, rgba(99, 102, 241, 0.05) 1px, transparent 1px);
          background-size: 40px 40px;
        }

        .perspective-container {
          perspective: 1200px;
          transform-style: preserve-3d;
        }

        /* 3D 倾斜效果 */
        .screen-left {
          transform: rotateY(60deg);
          transform-origin: right center;
          transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
        }

        .screen-left.flipped {
          transform: rotateY(30deg);
          transform-origin: right center;
        }

        .screen-right {
          transform: rotateY(-60deg);
          transform-origin: left center;
          transition: transform 0.6s cubic-bezier(0.4, 0, 0.2, 1);
        }

        .screen-right.flipped {
          transform: rotateY(-30deg);
          transform-origin: left center;
        }

        .screen-center {
          transform: translateZ(0px);
          box-shadow: 0 0 100px rgba(99,102,241,0.15);
        }

        .cursor-blink::after {
          content: '|';
          animation: blink 1s step-end infinite;
          color: #818cf8;
        }

        @keyframes blink {
          0%, 100% { opacity: 1; }
          50% { opacity: 0; }
        }

        .typing-scroll {
          display: inline-flex;
          align-items: center;
          width: 100%;
          white-space: pre;
          overflow-x: auto;
          scrollbar-width: none;
        }

        .typing-scroll::-webkit-scrollbar {
          display: none;
        }

        .typing-before,
        .typing-after {
          white-space: pre;
        }

        .typing-caret {
          display: inline-block;
          width: 2px;
          height: 1.2em;
          margin: 0 1px;
          background: #818cf8;
          box-shadow: 0 0 6px rgba(99,102,241,0.8);
          animation: blink 1s step-end infinite;
        }

        @keyframes flow-dash {
          to { stroke-dashoffset: -24; }
        }

        @keyframes flow-stream {
          from { stroke-dashoffset: 0; }
          to { stroke-dashoffset: -1000; }
        }

        @keyframes spin-slow-reverse {
          from { transform: rotate(360deg); }
          to { transform: rotate(0deg); }
        }

        @keyframes beam-pulse {
          0% { opacity: 0.6; stroke-width: 4px; filter: drop-shadow(0 0 5px #6366f1); }
          50% { opacity: 1; stroke-width: 6px; filter: drop-shadow(0 0 15px #818cf8); }
          100% { opacity: 0.6; stroke-width: 4px; filter: drop-shadow(0 0 5px #6366f1); }
        }

        .flow-line {
          stroke-dasharray: 6 6;
          animation: flow-dash 1s linear infinite;
        }

        .stream-line {
          stroke-dasharray: 8 12;
          animation: flow-stream 8s linear infinite;
          opacity: 0.5;
        }

        .glass-panel {
          background: rgba(11, 17, 32, 0.95);
          backdrop-filter: blur(20px);
          box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        }

        .log-stream {
          position: relative;
          overflow: hidden;
        }

        .log-stream::before {
          content: '';
          position: absolute;
          inset: 0;
          background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.5) 100%);
          z-index: 0;
          pointer-events: none;
        }

        .log-stream::after {
          content: '';
          position: absolute;
          inset: -40% -30%;
          background: linear-gradient(125deg, transparent 25%, var(--log-scan-color, rgba(129, 140, 248, 0.4)) 50%, transparent 75%);
          mix-blend-mode: screen;
          opacity: 0.5;
          animation: log-scan 5s linear infinite;
          pointer-events: none;
        }

        .log-scroll-container {
          scrollbar-width: none;
          -ms-overflow-style: none;
        }

        .log-scroll-container::-webkit-scrollbar {
          display: none;
        }

        .log-stream.log-stream-indigo {
          --log-scan-color: rgba(129, 140, 248, 0.45);
        }

        .log-stream.log-stream-emerald {
          --log-scan-color: rgba(52, 211, 153, 0.4);
        }

        @keyframes log-scan {
          0% {
            transform: translate(-60%, -80%) rotate(6deg);
          }
          50% {
            opacity: 0.65;
          }
          100% {
            transform: translate(60%, 80%) rotate(6deg);
          }
        }

        .log-line {
          opacity: 0;
          color: rgba(148, 163, 184, 0.5);
          animation: log-fade 0.9s ease forwards;
        }

        .log-line-indigo {
          color: rgba(224, 231, 255, 0.8);
        }

        .log-line-emerald {
          color: rgba(209, 250, 229, 0.85);
        }

        @keyframes log-fade {
          0% {
            opacity: 0;
            transform: translateY(6px);
          }
          100% {
            opacity: 1;
            transform: translateY(0);
          }
        }

        /* 数字流动画 - 原地闪烁 */
        .matrix-blink {
            animation: matrix-blink 0.8s ease-in-out infinite;
        }

        @keyframes matrix-blink {
            0%, 100% { opacity: 0.3; }
            50% { opacity: 1; }
        }

        /* 涡轮环样式 */
        .turbine-ring {
            background: conic-gradient(from 0deg, transparent 0%, rgba(99, 102, 241, 0.15) 20%, transparent 40%, rgba(99, 102, 241, 0.15) 60%, transparent 100%);
            border-radius: 50%;
        }

        /* 能量粒子上升动画 - 到达中心脉冲点 */
        @keyframes energyPulse {
          0% {
            transform: translate(-50%, 0) scale(1);
            opacity: 0;
          }
          10% {
            opacity: 1;
          }
          50% {
            opacity: 0.8;
          }
          90% {
            opacity: 0.6;
          }
          100% {
            transform: translate(-50%, -280px) scale(0.5);
            opacity: 0;
          }
        }

        /* 左侧能量粒子 - 向左上移动 */
        @keyframes energyPulseLeft {
          0% {
            transform: translate(-50%, 0) scale(1);
            opacity: 0;
          }
          10% {
            opacity: 1;
          }
          50% {
            opacity: 0.8;
          }
          90% {
            opacity: 0.3;
          }
          100% {
            transform: translate(-50%, -500px) translateX(-150px) scale(0.3);
            opacity: 0;
          }
        }

        /* 右侧能量粒子 - 向右上移动 */
        @keyframes energyPulseRight {
          0% {
            transform: translate(-50%, 0) scale(1);
            opacity: 0;
          }
          10% {
            opacity: 1;
          }
          50% {
            opacity: 0.8;
          }
          90% {
            opacity: 0.3;
          }
          100% {
            transform: translate(-50%, -500px) translateX(150px) scale(0.3);
            opacity: 0;
          }
        }

        /* 能量波纹扩散动画 */
        @keyframes energyRipple {
          0% {
            transform: translate(-50%, -50%) scale(0.5);
            opacity: 0.8;
          }
          100% {
            transform: translate(-50%, -50%) scale(2);
            opacity: 0;
          }
        }

        /* 能量光束脉冲 */
        @keyframes beamPulse {
          0%, 100% {
            opacity: 0.3;
            filter: blur(8px);
          }
          50% {
            opacity: 0.6;
            filter: blur(12px);
          }
        }

        /* 核心强化脉冲 */
        @keyframes corePulse {
          0%, 100% {
            box-shadow: 0 0 40px rgba(99,102,241,0.5), 0 0 80px rgba(99,102,241,0.3);
          }
          50% {
            box-shadow: 0 0 60px rgba(99,102,241,0.8), 0 0 120px rgba(99,102,241,0.5);
          }
        }

      `}</style>

      {/* 背景环境 */}
      <div className="absolute inset-0 bg-grid-pattern opacity-30"></div>
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-indigo-900/15 via-[#02040a] to-[#02040a]"></div>

      {/* 可视化层：数据流 */}
      <svg className="absolute inset-0 w-full h-full pointer-events-none z-10 overflow-visible" viewBox="0 0 100 100" preserveAspectRatio="none">
        <defs>
           <linearGradient id="chaosGradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stopColor="#7f1d1d" stopOpacity="0.3" />
              <stop offset="50%" stopColor="#dc2626" stopOpacity="0.7" />
              <stop offset="100%" stopColor="#ef4444" stopOpacity="0.9" />
           </linearGradient>
        </defs>

        {/* 混乱数据流：从四面八方向中心汇聚 */}
        <g>
           {/* 左上角流入 */}
           <path d="M -10 0 Q 20 25 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" />
           <path d="M 0 -10 Q 25 20 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-1.5s'}} />
           <path d="M -5 15 Q 20 30 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-3.5s'}} />

           {/* 右上角流入 */}
           <path d="M 110 0 Q 80 25 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" style={{animationDelay: '-2s'}} />
           <path d="M 100 -10 Q 75 20 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-4s'}} />
           <path d="M 105 20 Q 80 35 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-5.5s'}} />

           {/* 左下角流入 */}
           <path d="M -10 100 Q 20 75 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" style={{animationDelay: '-1s'}} />
           <path d="M 0 110 Q 25 80 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-3s'}} />
           <path d="M 15 105 Q 30 75 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-0.5s'}} />

           {/* 右下角流入 */}
           <path d="M 110 100 Q 80 75 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" style={{animationDelay: '-2.5s'}} />
           <path d="M 100 110 Q 75 80 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-4.5s'}} />
           <path d="M 85 105 Q 70 75 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-6s'}} />

           {/* 左侧流入 */}
           <path d="M -10 50 Q 20 50 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-2.8s'}} />
           <path d="M 0 35 Q 25 42 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-5s'}} />

           {/* 右侧流入 */}
           <path d="M 110 50 Q 80 50 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.18" className="stream-line" style={{animationDelay: '-3.8s'}} />
           <path d="M 100 65 Q 75 58 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-1.8s'}} />

           {/* 上侧流入 */}
           <path d="M 50 -10 Q 50 20 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" style={{animationDelay: '-4.2s'}} />
           <path d="M 35 0 Q 42 25 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-0.8s'}} />

           {/* 下侧流入 */}
           <path d="M 50 110 Q 50 80 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.2" className="stream-line" style={{animationDelay: '-5.2s'}} />
           <path d="M 65 100 Q 58 75 50 50" fill="none" stroke="url(#chaosGradient)" strokeWidth="0.15" className="stream-line" style={{animationDelay: '-2.2s'}} />
        </g>
      </svg>

      {/* 3D 容器 */}
      <div className="relative w-full h-full perspective-container flex items-center justify-center z-20">

         <div className="relative flex h-full w-full items-center justify-center">

           {/* 左侧屏幕：Agent 日志 */}
          <div
            className={cn(
              'absolute glass-panel rounded-l-xl screen-left flex flex-col overflow-hidden z-20 border-l-2 border-l-indigo-500/60 shadow-[0_0_30px_rgba(99,102,241,0.1)]',
              PANEL_SIZE_CLASS,
              leftRotated && 'flipped'
            )}
            style={
              {
                '--panel-width': `${panelWidth}px`,
                '--panel-height': `${panelHeight}px`,
                left: `${leftPanelLeft}px`,
                top: `${panelTop}px`,
                willChange: 'transform'
              } as CSSProperties
            }
             onMouseEnter={() => setLeftRotated(true)}
             onMouseLeave={() => setLeftRotated(false)}
           >
              <div className="h-8 md:h-10 border-b border-white/5 bg-white/5 flex items-center px-3 md:px-4 gap-2 md:gap-3">
                 <div className="w-2 md:w-2.5 h-2 md:h-2.5 rounded-full bg-indigo-500 animate-pulse"></div>
                 <span className="relative px-1 text-nano md:text-legal font-bold tracking-[0.25em] text-indigo-100">
                   <span className="relative z-10 drop-shadow-[0_0_8px_rgba(99,102,241,0.85)]">
                     {t('demo.agentWorking')}
                   </span>
                   <span
                     className="absolute inset-0 bg-gradient-to-r from-indigo-500/25 via-indigo-200/20 to-indigo-500/25 blur-sm opacity-70 animate-[pulse_2.4s_ease-in-out_infinite]"
                     aria-hidden
                   ></span>
                 </span>
              </div>
              <div className="log-stream log-stream-indigo flex-1 px-3 py-4 md:px-4 md:py-5 lg:px-5 lg:py-6 font-mono text-micro md:text-legal leading-relaxed text-indigo-100/95 overflow-hidden relative bg-gradient-to-b from-white/5 via-transparent to-transparent">
                <ProgressLog
                  lines={scenario.leftLogs}
                  progress={
                    state.phase === ScenarioPhase.TYPING_INPUT
                      ? 0
                      : state.leftLogProgress
                  }
                  tone="indigo"
                />
                <div className="absolute inset-0 pointer-events-none">
                  <div className="absolute inset-0 opacity-45 mix-blend-screen bg-[radial-gradient(circle_at_15%_30%,rgba(99,102,241,0.18),transparent_45%),radial-gradient(circle_at_80%_25%,rgba(14,165,233,0.15),transparent_40%),radial-gradient(circle_at_55%_90%,rgba(79,70,229,0.2),transparent_35%)]"></div>
                  <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-[#0f172a] via-[#0f172a]/80 to-transparent" style={{ backdropFilter: 'blur(12px)' }}></div>
                </div>
              </div>
           </div>

           {/* 中央屏幕：业务价值流 */}
          <div
            className={cn(
              'glass-panel rounded-none screen-center flex flex-col absolute z-40 border border-indigo-500/40 bg-[#0B1120]/95',
              CENTER_SIZE_CLASS
            )}
            style={
              {
                '--center-width': `${centerWidth}px`,
                '--center-height': `${centerHeight}px`,
                 left: `${centerLeft}px`,
                 top: `${panelTop}px`,
                 willChange: 'transform'
               } as CSSProperties
             }
           >
              <div className="h-8 md:h-10 border-b border-indigo-500/20 bg-indigo-950/20 flex items-center justify-between pl-3 md:pl-4 lg:pl-5 pr-4 md:pr-5 lg:pr-6">
                 <div className="flex items-center gap-1 md:gap-2">
                    <LayoutTemplate size={12} className="md:w-3.5 md:h-3.5 text-indigo-400" />
                    <span className="text-micro md:text-xs font-bold text-indigo-100 tracking-wider">{t('demo.workflowStudio')}</span>
                 </div>
                 <div className="flex items-center gap-1.5 mr-0.5">
                   <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                   <span className="text-nano md:text-micro text-indigo-300 font-mono">{t('demo.realtime')}</span>
                 </div>
              </div>

              {/* 画布内容 */}
              <div className="flex-1 relative overflow-hidden">
                 <div className="absolute inset-0">

                    {/* 数字流背景 - 原地闪烁 */}
                    {(state.phase === ScenarioPhase.TYPING_INPUT || state.phase === ScenarioPhase.AGENT_ANALYZING) && (
                      <div
                        className="absolute inset-0 z-0 flex justify-around items-center overflow-hidden transition-opacity duration-1000"
                        style={{
                          opacity: state.phase === ScenarioPhase.TYPING_INPUT
                            ? 1
                            : Math.max(0, 1 * (1 - state.leftLogProgress / 100))
                        }}
                      >
                        {[...Array(12)].map((_, i) => (
                          <div
                            key={i}
                            className="text-xs text-indigo-400 font-mono whitespace-pre flex flex-col gap-3"
                          >
                            {[...Array(15)].map((_, j) => (
                              <div
                                key={j}
                                className="matrix-blink"
                                style={{
                                  animationDelay: `${(i * 0.1 + j * 0.05)}s`
                                }}
                              >
                                {String.fromCharCode(48 + Math.floor(Math.random() * 75))}
                              </div>
                            ))}
                          </div>
                        ))}
                      </div>
                    )}

                    {/* SVG 层 - 雷达在工作流节点之上 */}
                       <svg className="absolute inset-0 w-full h-full pointer-events-none z-20 overflow-visible">
                       <defs>
                           <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
                               <feGaussianBlur stdDeviation="3" result="blur" />
                               <feComposite in="SourceGraphic" in2="blur" operator="over" />
                           </filter>
                           <filter id="center-glow">
                               <feGaussianBlur stdDeviation="5" result="coloredBlur"/>
                               <feMerge>
                                   <feMergeNode in="coloredBlur"/>
                                   <feMergeNode in="SourceGraphic"/>
                               </feMerge>
                           </filter>
                           <radialGradient id="radarGradient" cx="50%" cy="50%" r="50%">
                             <stop offset="0%" stopColor="rgba(199, 210, 254, 0.35)" />
                             <stop offset="60%" stopColor="rgba(79, 70, 229, 0.12)" />
                             <stop offset="100%" stopColor="rgba(15, 23, 42, 0.1)" />
                           </radialGradient>
                           <linearGradient id="radarSweep" x1="0%" y1="0%" x2="100%" y2="0%">
                             <stop offset="0%" stopColor="rgba(129, 140, 248, 0.35)" />
                             <stop offset="100%" stopColor="rgba(129, 140, 248, 0.05)" />
                           </linearGradient>
                       </defs>

                       {/* 中心雷达 - 居中画布，调整尺寸 */}
                       <g style={{ opacity: radarOpacity, transition: 'opacity 0.6s ease' }}>
                         <circle cx={radarCenterX} cy={radarCenterY} r="140" fill="url(#radarGradient)" opacity="0.35" />

                         {[45, 85, 125].map((radius) => (
                           <circle
                             key={`static-ring-${radius}`}
                             cx={radarCenterX}
                             cy={radarCenterY}
                             r={radius}
                             stroke="rgba(148,163,184,0.2)"
                             strokeWidth="0.8"
                             fill="none"
                           />
                         ))}
                         <line x1={radarCenterX - 140} y1={radarCenterY} x2={radarCenterX + 140} y2={radarCenterY} stroke="rgba(148,163,184,0.25)" strokeWidth="0.8" />
                         <line x1={radarCenterX} y1={radarCenterY - 140} x2={radarCenterX} y2={radarCenterY + 140} stroke="rgba(148,163,184,0.25)" strokeWidth="0.8" />

                         {[0, 1, 2].map((i) => (
                           <circle
                             key={`radar-ring-${i}`}
                             cx={radarCenterX}
                             cy={radarCenterY}
                             r="30"
                             stroke="rgba(129,140,248,0.35)"
                             strokeWidth="1"
                             fill="none"
                           >
                             <animate attributeName="r" from="20" to="125" dur="3.5s" begin={`${i * 0.8}s`} repeatCount="indefinite" />
                             <animate attributeName="opacity" values="0;0.55;0" dur="3.5s" begin={`${i * 0.8}s`} repeatCount="indefinite" />
                           </circle>
                         ))}

                         <circle cx={radarCenterX} cy={radarCenterY} r="16" fill={radarColor} opacity="0.15" filter="url(#center-glow)" />
                         <circle cx={radarCenterX} cy={radarCenterY} r="8" fill={radarColor} opacity="0.6" />
                         <circle cx={radarCenterX} cy={radarCenterY} r="4" fill="#0f172a" stroke={radarColor} strokeWidth="1.5" opacity="0.95" />

                         <path d={wedgePath} fill="url(#radarSweep)" opacity="0.55">
                           <animateTransform attributeName="transform" type="rotate" from={`0 ${radarCenterX} ${radarCenterY}`} to={`360 ${radarCenterX} ${radarCenterY}`} dur="4s" repeatCount="indefinite" />
                         </path>

                         <line x1={radarCenterX} y1={radarCenterY} x2={pointerEnd.x} y2={pointerEnd.y} stroke={radarColor} strokeWidth="3" strokeLinecap="round" opacity="0.9">
                           <animateTransform attributeName="transform" type="rotate" from={`0 ${radarCenterX} ${radarCenterY}`} to={`360 ${radarCenterX} ${radarCenterY}`} dur="4s" repeatCount="indefinite" />
                         </line>

                         {radarInboundPaths.map((path, idx) => (
                           <circle key={`radar-inbound-${idx}`} r="5" fill={radarColor} opacity="0.85">
                             <animateMotion dur="3s" begin={`${idx * 0.4}s`} repeatCount="indefinite" path={path} />
                             <animate attributeName="r" values="3;6;3" dur="1.5s" repeatCount="indefinite" />
                           </circle>
                         ))}

                         {radarBlips.map((blip) => (
                           <circle key={`${blip.x}-${blip.y}`} cx={blip.x} cy={blip.y} r="4" fill="#a5b4fc" opacity="0.8">
                             <animate attributeName="opacity" values="0;1;0" dur="2.2s" begin={blip.delay} repeatCount="indefinite" />
                             <animate attributeName="r" values="2;5;2" dur="2.2s" begin={blip.delay} repeatCount="indefinite" />
                           </circle>
                         ))}
                       </g>

                       {/* 连接线 - 应用和节点相同的缩放和偏移 */}
                       <g>
                       {state.phase !== ScenarioPhase.TYPING_INPUT && scenario.edges.map((edge) => {
                         const sourceNode = scenario.nodes.find((n) => n.id === edge.source)
                         const targetNode = scenario.nodes.find((n) => n.id === edge.target)

                         if (!sourceNode || !targetNode) return null

                         // 判断连接线是否激活
                         const sourceIndex = scenario.nodes.findIndex((n) => n.id === edge.source)
                         const targetIndex = scenario.nodes.findIndex((n) => n.id === edge.target)
                         const isEdgeActive =
                           (state.phase === ScenarioPhase.AGENT_ANALYZING ||
                            state.phase === ScenarioPhase.BUILDING ||
                            state.phase === ScenarioPhase.COMPLETED ||
                            state.phase === ScenarioPhase.WAITING) &&
                           sourceIndex <= state.activeNodeIndex &&
                           targetIndex <= state.activeNodeIndex

                         return (
                           <g key={edge.id}>
                             <path
                               d={getEdgePath(sourceNode, targetNode)}
                               fill="none"
                               stroke={isEdgeActive ? '#818cf8' : '#475569'}
                               strokeWidth="2"
                               className={isEdgeActive ? 'flow-line' : ''}
                               opacity={isEdgeActive ? 0.8 : 0}
                             />
                             {isEdgeActive && (
                               <circle r="4" fill="#818cf8" opacity="0.8">
                                 <animateMotion
                                   dur="2s"
                                   repeatCount="indefinite"
                                   path={getEdgePath(sourceNode, targetNode)}
                                 />
                               </circle>
                             )}
                           </g>
                         )
                       })}
                       </g>

                    </svg>

                    {/* 动态渲染节点 - 整体居中缩放（相对于中央屏幕） */}
                    <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                    <div className={cn('relative pointer-events-auto', PREVIEW_CARD_SIZE_CLASS)}>
                    {scenario.nodes.map((node, index) => {
                      const Icon = getIcon(node.icon)

                      // 只在 Agent 分析阶段之后才显示节点
                      if (state.phase === ScenarioPhase.TYPING_INPUT) {
                        return null
                      }

                      // 在 Agent 分析阶段开始激活节点
                      const isActive =
                        state.phase === ScenarioPhase.AGENT_ANALYZING ||
                        state.phase === ScenarioPhase.BUILDING ||
                        state.phase === ScenarioPhase.COMPLETED ||
                        state.phase === ScenarioPhase.WAITING
                          ? index <= state.activeNodeIndex
                          : false

                      // 计算节点的出现进度（基于 Agent 分析进度）
                      const nodeProgress = state.phase === ScenarioPhase.AGENT_ANALYZING
                        ? Math.min(100, Math.max(0, (state.leftLogProgress - (index * 100 / scenario.nodes.length)) * (scenario.nodes.length / 100)))
                        : state.leftLogProgress >= 100 ? 100 : 0

                      // 动态缩放坐标以适配容器宽度（610 -> 500）
                      const scaleRatio = 0.82
                      const scaledX = node.position.x * scaleRatio
                      const scaledY = node.position.y * scaleRatio
                      const nodeSizeClass = NODE_SIZE_CLASS

                      return (
                        <div
                          key={node.id}
                          style={{
                            left: `${scaledX}px`,
                            top: `${scaledY}px`,
                            opacity: isActive ? 1 : Math.max(0, nodeProgress / 100),
                            transform: isActive
                              ? 'scale(1)'
                              : `scale(${0.5 + (nodeProgress / 200)})`,
                            filter: isActive ? 'blur(0px)' : `blur(${Math.max(0, 8 - nodeProgress / 12.5)}px)`
                          }}
                          className={`absolute ${nodeSizeClass} rounded-xl border bg-[#0f172a] flex flex-col items-center justify-center gap-1 transition-all duration-300 z-10 ${
                            isActive
                              ? `${getColorClass(node.color, 'border')} ${getColorClass(node.color, 'shadow')}`
                              : 'border-slate-700'
                          }`}
                        >
                          <Icon size={15} className={getColorClass(node.color, 'text')} />
                          <div className="text-tiny text-slate-200 font-bold text-center leading-tight">
                            {node.name}
                            <br />
                            <span className={`text-mini ${getColorClass(node.color, 'text')} font-normal`}>
                              {node.description}
                            </span>
                          </div>
                        </div>
                      )
                    })}
                      </div>
                    </div>
                 </div>
              </div>
           </div>

           {/* 右侧屏幕：构建日志 - 左边框紧贴中央屏幕右边框 */}
           <div
             className={`absolute glass-panel rounded-r-xl screen-right ${rightRotated ? 'flipped' : ''} flex flex-col overflow-hidden z-20 border-r-2 border-r-emerald-500/60 shadow-[0_0_30px_rgba(16,185,129,0.1)] w-[var(--panel-width)] h-[var(--panel-height)]`}
             style={
               {
                 '--panel-width': `${panelWidth}px`,
                 '--panel-height': `${panelHeight}px`,
                 left: `${rightPanelLeft}px`,
                 top: `${panelTop}px`,
                 willChange: 'transform'
               } as CSSProperties
             }
             onMouseEnter={() => setRightRotated(true)}
             onMouseLeave={() => setRightRotated(false)}
           >
              <div className="h-8 md:h-10 border-b border-white/5 bg-white/5 flex items-center px-3 md:px-4 gap-2 md:gap-3">
                 <div className="w-2 md:w-2.5 h-2 md:h-2.5 rounded-full bg-emerald-500 animate-pulse"></div>
                 <span className="relative px-1 text-nano md:text-legal font-bold tracking-[0.25em] text-emerald-100">
                   <span className="relative z-10 drop-shadow-[0_0_8px_rgba(16,185,129,0.85)]">
                     {t('demo.buildingETL')}
                   </span>
                   <span
                     className="absolute inset-0 bg-gradient-to-r from-emerald-400/25 via-emerald-200/15 to-emerald-400/25 blur-sm opacity-70 animate-[pulse_2.6s_ease-in-out_infinite]"
                     aria-hidden
                   ></span>
                 </span>
              </div>
              <div className="log-stream log-stream-emerald flex-1 px-3 py-4 md:px-4 md:py-5 lg:px-5 lg:py-6 font-mono text-micro md:text-legal leading-relaxed text-emerald-100/95 overflow-hidden relative bg-gradient-to-b from-white/5 via-transparent to-transparent">
                <ProgressLog
                  lines={scenario.rightLogs}
                   progress={
                     state.phase === ScenarioPhase.TYPING_INPUT || state.phase === ScenarioPhase.AGENT_ANALYZING
                       ? 0
                       : state.rightLogProgress
                   }
                  tone="emerald"
                 />
                <div className="absolute inset-0 pointer-events-none">
                  <div className="absolute inset-0 opacity-40 mix-blend-screen bg-[radial-gradient(circle_at_20%_30%,rgba(16,185,129,0.18),transparent_45%),radial-gradient(circle_at_75%_20%,rgba(52,211,153,0.12),transparent_40%),radial-gradient(circle_at_60%_85%,rgba(16,185,129,0.2),transparent_35%)]"></div>
                  <div className="absolute inset-x-0 bottom-0 h-16 bg-gradient-to-t from-[#0f172a] via-[#0f172a]/80 to-transparent" style={{ backdropFilter: 'blur(12px)' }}></div>
                </div>
              </div>
           </div>

         </div>
      </div>

      {/* 统一涡轮引擎 - 增强辨识度 */}
      <div className={cn('absolute left-1/2 bottom-[20px] flex items-center justify-center pointer-events-none z-10', ORB_MAIN_CLASS)} style={{ transform: 'translateX(-50%) rotateX(78deg)' }}>
         <div className={cn('absolute bg-indigo-500/25 blur-[80px] rounded-full', ORB_RING_CLASS)}></div>
         <div className="absolute w-full h-full rounded-full border-2 border-indigo-600/50 bg-[#0f1729] shadow-2xl shadow-indigo-500/30"></div>
         <div className={cn('absolute rounded-full border-2 border-indigo-400/50 border-dashed animate-[spin_25s_linear_infinite]', ORB_RING_MID_CLASS)}></div>
         <div className={cn('absolute rounded-full turbine-ring animate-[spin_6s_linear_infinite] opacity-80', ORB_RING_INNER_CLASS)}></div>
         <div className={cn('absolute rounded-full border-2 border-indigo-300/40 border-dotted animate-[spin-slow-reverse_12s_linear_infinite]', ORB_RING_CORE_CLASS)}></div>
         <div className={cn('absolute rounded-full bg-indigo-900 border-2 border-indigo-400/80 flex items-center justify-center shadow-lg shadow-indigo-500/50', ORB_CORE_CLASS)} style={{ animation: 'corePulse 2s ease-in-out infinite' }}>
             <div className="w-12 h-12 rounded-full bg-indigo-400 blur-md animate-pulse"></div>
         </div>
      </div>

      {/* 能量波动效果 - 对齐底座中心 */}
      <div className="absolute left-1/2 bottom-[20px] w-2 h-[500px] pointer-events-none z-15" style={{ transform: 'translateX(-50%)' }}>

        {/* 左侧能量束 */}
        <div
          className="absolute bottom-[200px] left-1/2 w-5 h-[280px]"
          style={{
            background: 'linear-gradient(to top, rgba(99, 102, 241, 0.8), rgba(99, 102, 241, 0.4), transparent)',
            animation: 'beamPulse 1.5s ease-in-out infinite',
            transform: 'translateX(-50%) rotate(-20deg)',
            transformOrigin: 'bottom center'
          }}
        ></div>

        {/* 中间能量束 */}
        <div
          className="absolute bottom-[200px] left-1/2 w-5 h-[280px]"
          style={{
            background: 'linear-gradient(to top, rgba(99, 102, 241, 0.8), rgba(99, 102, 241, 0.4), transparent)',
            animation: 'beamPulse 1.5s ease-in-out infinite',
            transform: 'translateX(-50%)',
            animationDelay: '0.2s'
          }}
        ></div>

        {/* 右侧能量束 */}
        <div
          className="absolute bottom-[200px] left-1/2 w-5 h-[280px]"
          style={{
            background: 'linear-gradient(to top, rgba(99, 102, 241, 0.8), rgba(99, 102, 241, 0.4), transparent)',
            animation: 'beamPulse 1.5s ease-in-out infinite',
            transform: 'translateX(-50%) rotate(20deg)',
            transformOrigin: 'bottom center',
            animationDelay: '0.4s'
          }}
        ></div>

        {/* 能量粒子 */}
        {[...Array(6)].map((_, i) => (
          <div
            key={`particle-left-${i}`}
            className="absolute left-1/2 bottom-[200px] w-4 h-4 rounded-full bg-indigo-300"
            style={{
              animation: `energyPulse 1.8s ease-out infinite`,
              animationDelay: `${i * 0.3}s`,
              transform: 'translateX(-50%) rotate(-20deg)',
              transformOrigin: 'bottom center',
              boxShadow: '0 0 10px rgba(99, 102, 241, 0.8)',
              filter: 'blur(2px)'
            }}
          />
        ))}

        {[...Array(6)].map((_, i) => (
          <div
            key={`particle-center-${i}`}
            className="absolute left-1/2 bottom-[200px] w-4 h-4 rounded-full bg-indigo-300"
            style={{
              animation: `energyPulse 1.8s ease-out infinite`,
              animationDelay: `${i * 0.3 + 0.1}s`,
              transform: 'translateX(-50%)',
              boxShadow: '0 0 10px rgba(99, 102, 241, 0.8)',
              filter: 'blur(2px)'
            }}
          />
        ))}

        {[...Array(6)].map((_, i) => (
          <div
            key={`particle-right-${i}`}
            className="absolute left-1/2 bottom-[200px] w-4 h-4 rounded-full bg-indigo-300"
            style={{
              animation: `energyPulse 1.8s ease-out infinite`,
              animationDelay: `${i * 0.3 + 0.2}s`,
              transform: 'translateX(-50%) rotate(20deg)',
              transformOrigin: 'bottom center',
              boxShadow: '0 0 10px rgba(99, 102, 241, 0.8)',
              filter: 'blur(2px)'
            }}
          />
        ))}

        {[...Array(4)].map((_, i) => (
          <div
            key={`ripple-${i}`}
            className="absolute left-1/2 bottom-[200px] w-4 h-4 rounded-full border-2 border-indigo-400/60"
            style={{
              animation: `energyRipple 2s ease-out infinite`,
              animationDelay: `${i * 0.5}s`
            }}
          />
        ))}
      </div>

      {/* 底部输入框 */}
      <div className="absolute bottom-[120px] z-30 w-full flex justify-center px-4">
         <div
            className={cn(
              'bg-slate-900/80 backdrop-blur-xl border border-white/10 rounded-full p-1.5 pl-4 md:pl-6 pr-2 flex items-center gap-2 md:gap-4 shadow-2xl ring-1 ring-indigo-500/20',
              inputContainerWidthClassMap.normal
            )}
         >
             <div className="w-7 h-7 md:w-8 md:h-8 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shrink-0">
                <Sparkles size={14} className="md:w-4 md:h-4 text-white" />
             </div>
             <div className="flex-1 overflow-hidden">
               <div
                 ref={typingBoxRef}
                 className="typing-scroll font-mono text-xs md:text-sm text-indigo-100/90 min-h-[18px] md:min-h-[20px]"
               >
                  <span
                    ref={virtualBeforeRef}
                    className="typing-before"
                  >
                    {beforeCaretText.length ? beforeCaretText : '\u200B'}
                  </span>
                  <span className="typing-caret" aria-hidden="true"></span>
                  <span className="typing-after">{afterCaretText}</span>
               </div>
             </div>
             <div className="h-8 w-8 md:h-9 md:w-9 rounded-full bg-white/5 flex items-center justify-center border border-white/5">
                <Send size={12} className="md:w-3.5 md:h-3.5 text-indigo-400" />
             </div>
          </div>
      </div>
        <div className="absolute bottom-[90px] left-1/2 -translate-x-1/2 z-30 text-micro md:text-xs text-slate-500 font-mono tracking-[0.2em] uppercase text-center">
            {t('demo.aiAgent')}
        </div>
      </div>
    </div>
  )
}
