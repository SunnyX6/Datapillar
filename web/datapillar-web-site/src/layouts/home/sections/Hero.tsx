import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import {
  ArrowRight,
  Play,
  Database,
  Package,
  Link,
  Table,
  Sparkles,
  Bot,
  User,
  Check,
  BarChart3,
  Terminal,
  Paperclip,
  Send
} from 'lucide-react'
import { SmartVisualizationChart } from '../components/SmartVisualizationChart'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'

interface HeroProps {
  onRequestAccess: () => void
}

const getTypingDuration = (text: string, speed: number, min = 800, max = 2200) =>
  Math.min(max, Math.max(min, text.length * speed))

const TypingText = ({ text, speed }: { text: string; speed: number }) => {
  const [typed, setTyped] = useState('')

  useEffect(() => {
    if (!text) return undefined
    let index = 0
    const timer = window.setInterval(() => {
      index += 1
      setTyped(text.slice(0, index))
      if (index >= text.length) {
        window.clearInterval(timer)
      }
    }, speed)

    return () => window.clearInterval(timer)
  }, [text, speed])

  return <>{typed.length ? typed : '\u200B'}</>
}

export function Hero({ onRequestAccess }: HeroProps) {
  const { t, i18n } = useTranslation('home')
  const scenarios = useMemo(
    () => [
      {
        id: 'integration',
        title: t('hero.scenarios.integration.title'),
        description: t('hero.scenarios.integration.description'),
        icon: <Database className="w-3.5 h-3.5" />,
        duration: 5000
      },
      {
        id: 'analytics',
        title: t('hero.scenarios.analytics.title'),
        description: t('hero.scenarios.analytics.description'),
        icon: <BarChart3 className="w-3.5 h-3.5" />,
        duration: 6000
      }
    ],
    [t]
  )
  const [activeScenario, setActiveScenario] = useState(0)
  const studioRef = useRef<HTMLDivElement>(null)
  const scenarioKey = `${scenarios[activeScenario]?.id ?? activeScenario}-${i18n.language}`

  const handleDotClick = (index: number) => {
    setActiveScenario(index)
  }

  useEffect(() => {
    const duration = scenarios[activeScenario]?.duration ?? 5000
    const timer = window.setTimeout(() => {
      setActiveScenario((prev) => (prev + 1) % scenarios.length)
    }, duration)
    return () => window.clearTimeout(timer)
  }, [activeScenario, scenarios])

  return (
    <section id="studio" className="relative pt-40 pb-24 overflow-hidden border-b border-white/5 bg-[#020410]">
      <div className="absolute inset-0 bg-cyber-grid opacity-30 pointer-events-none" />

      <div className={`absolute top-0 left-1/2 -translate-x-1/2 w-full ${contentMaxWidthClassMap.ultraWide} h-[800px] pointer-events-none`}>
        <div className="absolute top-[-150px] left-[15%] w-[40rem] h-[40rem] bg-violet-600/20 rounded-full blur-[120px] animate-pulse" />
        <div className="absolute top-[50px] right-[10%] w-[32rem] h-[32rem] bg-cyan-500/10 rounded-full blur-[100px]" />
      </div>

      <div className={`${contentMaxWidthClassMap.ultraWide} relative mx-auto px-8 flex flex-col items-center text-center z-10`}>
        <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-violet-900/30 border border-violet-500/30 mb-8 backdrop-blur-md hover:border-violet-500/50 transition-colors cursor-pointer group shadow-[0_0_15px_rgba(99,102,241,0.3)]">
          <span className="flex h-2 w-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,0.8)] animate-pulse" />
          <span className="text-violet-200 text-xs font-mono tracking-wide uppercase">{t('hero.release')}</span>
        </div>

        <h1 className="text-6xl font-bold text-white tracking-tight mb-8 leading-[1.2]">
          {t('hero.titleLine')} <br />
          <span className="bg-clip-text text-transparent bg-gradient-to-r from-violet-400 via-violet-200 to-cyan-200 text-glow">
            {t('hero.titleHighlight')}
          </span>
        </h1>

        <p className="mt-4 max-w-2xl text-lg text-slate-400 mb-12 leading-relaxed font-light">
          <Trans
            i18nKey="hero.subtitle"
            t={t}
            components={[
              <span className="text-cyan-400" key="subtitle-0" />,
              <span className="text-cyan-400" key="subtitle-1" />,
              <span className="text-cyan-400" key="subtitle-2" />
            ]}
          />
        </p>

        <div className="flex flex-row gap-4 mb-20">
          <button
            onClick={onRequestAccess}
            className="px-6 py-3 rounded-lg bg-[#5558ff] hover:bg-[#4548e6] text-white text-sm font-semibold transition-all flex items-center justify-center gap-2 shadow-[0_0_30px_rgba(85,88,255,0.35)] hover:shadow-[0_0_36px_rgba(85,88,255,0.55)] border border-white/10"
          >
            {t('hero.cta.startTrial')}
            <ArrowRight className="w-4 h-4" />
          </button>

          <button className="px-6 py-3 rounded-lg bg-slate-900/50 hover:bg-slate-800/50 text-white text-sm font-medium border border-violet-500/30 hover:border-violet-500/60 transition-all flex items-center justify-center gap-2 backdrop-blur-sm group">
            <Play className="w-4 h-4 fill-violet-400 text-violet-400 group-hover:scale-110 transition-transform" />
            {t('hero.cta.watchDemo')}
          </button>
        </div>

        <div className="relative w-full max-w-6xl mx-auto perspective-1000 group mb-8">
          <div
            ref={studioRef}
            className="relative rounded-xl bg-[#0b0f19] border border-slate-800 shadow-2xl overflow-hidden aspect-[2.2/1] transform transition-transform duration-500 hover:scale-[1.005] neon-border flex flex-col"
          >
            <div className="h-9 bg-[#151b2e] border-b border-slate-800 flex items-center px-4 justify-between shrink-0">
              <div className="flex items-center gap-2">
                <div className="flex gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-slate-600" />
                  <div className="w-2.5 h-2.5 rounded-full bg-slate-600" />
                  <div className="w-2.5 h-2.5 rounded-full bg-slate-600" />
                </div>
                <div className="ml-4 text-micro text-slate-400 font-mono flex items-center gap-2">
                  <span className="text-violet-400 font-bold">Datapillar Studio</span>
                  <span className="text-slate-600">/</span>
                  <span>{t('hero.studio.workspace')}</span>
                  <span className="text-slate-600">/</span>
                  <span>
                    {activeScenario === 0 ? t('hero.studio.paths.integration') : t('hero.studio.paths.analytics')}
                  </span>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <div className="flex -space-x-1">
                  <div className="w-5 h-5 rounded-full bg-slate-700 border border-[#151b2e]" />
                  <div className="w-5 h-5 rounded-full bg-slate-600 border border-[#151b2e]" />
                </div>
                <div className="h-3 w-px bg-slate-700" />
                <div className="text-micro text-slate-400">{t('hero.studio.autosave')}</div>
              </div>
            </div>

            {activeScenario === 0 ? (
              <PipelineView key={`pipeline-${scenarioKey}`} />
            ) : (
              <AnalyticsView key={`analytics-${scenarioKey}`} />
            )}
          </div>
        </div>

        <div className="flex flex-col items-center justify-center gap-4">
          <div className="flex items-center gap-3 bg-slate-900/50 p-2 rounded-full border border-white/5 backdrop-blur-sm">
            {scenarios.map((scenario, index) => (
              <button
                key={scenario.id}
                onClick={() => handleDotClick(index)}
                className="relative group focus:outline-none"
                aria-label={scenario.title}
              >
                <div
                  className={`transition-all duration-300 ease-out rounded-full ${
                    activeScenario === index
                      ? 'w-8 h-2.5 bg-gradient-to-r from-violet-500 to-cyan-500 shadow-[0_0_12px_rgba(139,92,246,0.6)]'
                      : 'w-2.5 h-2.5 bg-slate-700 group-hover:bg-slate-500'
                  }`}
                />
              </button>
            ))}
          </div>

          <div className="h-6 overflow-hidden relative w-64">
            {scenarios.map((scenario, index) => (
              <div
                key={scenario.id}
                className={`transition-all duration-300 absolute w-full text-center flex items-center justify-center gap-2 ${
                  activeScenario === index ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
                }`}
              >
                <span className="text-slate-400">{scenario.icon}</span>
                <span className="text-sm text-slate-300 font-medium font-mono tracking-wide">{scenario.title}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}

const PipelineView = () => {
  const { t } = useTranslation('home')
  const [step, setStep] = useState(0)
  const [animatedRadarProgress, setAnimatedRadarProgress] = useState(0)
  const stepRef = useRef(0)

  const prompt = t('hero.pipeline.prompt')
  const typingSpeed = 38
  const typingDuration = getTypingDuration(prompt, typingSpeed)
  const isPendingInput = step < 1
  const showUserMessage = step >= 1
  const showTyping = step >= 2 && step < 3
  const showAiMessage = step >= 3
  const canvasNodes = useMemo(
    () => [
      {
        id: 'order-source',
        title: t('hero.pipeline.nodes.orders'),
        subtitle: 'ODS',
        tone: 'blue',
        icon: <Database className="w-3 h-3 text-blue-300" />,
        position: { x: 50, y: 100 },
        appearAt: 4
      },
      {
        id: 'product-source',
        title: t('hero.pipeline.nodes.products'),
        subtitle: 'ODS',
        tone: 'cyan',
        icon: <Package className="w-3 h-3 text-cyan-300" />,
        position: { x: 50, y: 200 },
        appearAt: 5
      },
      {
        id: 'join',
        title: t('hero.pipeline.nodes.join'),
        subtitle: 'Spark SQL',
        tone: 'purple',
        icon: <Link className="w-3 h-3 text-violet-300" />,
        position: { x: 220, y: 150 },
        appearAt: 6
      },
      {
        id: 'wide-table',
        title: t('hero.pipeline.nodes.wide'),
        subtitle: 'DWD',
        tone: 'amber',
        icon: <Table className="w-3 h-3 text-amber-300" />,
        position: { x: 370, y: 150 },
        appearAt: 7
      },
      {
        id: 'aggregation',
        title: t('hero.pipeline.nodes.aggregation'),
        subtitle: 'DWS',
        tone: 'orange',
        icon: <BarChart3 className="w-3 h-3 text-orange-300" />,
        position: { x: 510, y: 150 },
        appearAt: 8
      }
    ],
    [t]
  )
  const edges = [
    { id: 'e1', source: 'order-source', target: 'join' },
    { id: 'e2', source: 'product-source', target: 'join' },
    { id: 'e3', source: 'join', target: 'wide-table' },
    { id: 'e4', source: 'wide-table', target: 'aggregation' }
  ]
  const toneStyles: Record<string, { border: string; text: string; shadow: string }> = {
    blue: {
      border: 'border-blue-500/40',
      text: 'text-blue-300',
      shadow: 'shadow-[0_0_10px_rgba(59,130,246,0.2)]'
    },
    cyan: {
      border: 'border-cyan-500/40',
      text: 'text-cyan-300',
      shadow: 'shadow-[0_0_10px_rgba(34,211,238,0.2)]'
    },
    purple: {
      border: 'border-violet-500/40',
      text: 'text-violet-300',
      shadow: 'shadow-[0_0_10px_rgba(139,92,246,0.2)]'
    },
    amber: {
      border: 'border-amber-500/40',
      text: 'text-amber-300',
      shadow: 'shadow-[0_0_10px_rgba(251,191,36,0.2)]'
    },
    orange: {
      border: 'border-orange-500/40',
      text: 'text-orange-300',
      shadow: 'shadow-[0_0_10px_rgba(249,115,22,0.2)]'
    }
  }
  const centerWidth = 680
  const centerHeight = 360
  const previewWidth = 660
  const previewHeight = 320
  const previewOffsetY = -12
  const nodeScaleRatio = previewWidth / 610
  const nodeWidth = Math.round(100 * nodeScaleRatio)
  const nodeHeight = Math.round(60 * nodeScaleRatio)
  const heroSizeStyle: CSSProperties = {
    '--hero-center-width': `${centerWidth}px`,
    '--hero-center-height': `${centerHeight}px`,
    '--hero-preview-width': `${previewWidth}px`,
    '--hero-preview-height': `${previewHeight}px`,
    '--hero-node-width': `${nodeWidth}px`,
    '--hero-node-height': `${nodeHeight}px`
  }
  const activeNodeIndex = Math.min(canvasNodes.length - 1, Math.max(-1, step - 4))
  const isNodePhase = step >= 4
  const radarFadeRatio =
    activeNodeIndex >= 0 ? Math.min(1, (activeNodeIndex + 1) / canvasNodes.length) : 0
  const radarOpacity = isNodePhase ? Math.max(0, 1 - radarFadeRatio) : 1
  const matrixOpacity = step >= 6 ? 0.45 : 0.9
  const radarProgress = step >= 4 ? 100 : animatedRadarProgress
  const radarProgressRatio = Math.min(radarProgress / 100, 1)
  const radarColor = (() => {
    const r = Math.floor(239 * (1 - radarProgressRatio) + 16 * radarProgressRatio)
    const g = Math.floor(68 * (1 - radarProgressRatio) + 185 * radarProgressRatio)
    const b = Math.floor(68 * (1 - radarProgressRatio) + 129 * radarProgressRatio)
    return `rgb(${r},${g},${b})`
  })()
  const matrixColumns = useMemo(() => {
    return Array.from({ length: 16 }, (_, colIdx) =>
      Array.from({ length: 20 }, (_, rowIdx) => ({
        value: String.fromCharCode(48 + Math.floor(Math.random() * 75)),
        delay: colIdx * 0.1 + rowIdx * 0.05
      }))
    )
  }, [])
  const radarConfig = useMemo(() => {
    const radarCenterX = centerWidth / 2
    const radarCenterY = centerHeight / 2
    const radarRadius = 160
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
    return {
      radarCenterX,
      radarCenterY,
      radarRadius,
      radarInboundPaths,
      radarBlips,
      wedgePath,
      pointerEnd
    }
  }, [centerWidth, centerHeight])
  const { radarCenterX, radarCenterY, radarRadius, radarInboundPaths, radarBlips, wedgePath, pointerEnd } = radarConfig
  const getEdgePath = (sourceNode: (typeof canvasNodes)[number], targetNode: (typeof canvasNodes)[number]) => {
    const scaledSourceX = sourceNode.position.x * nodeScaleRatio
    const scaledSourceY = sourceNode.position.y * nodeScaleRatio
    const scaledTargetX = targetNode.position.x * nodeScaleRatio
    const scaledTargetY = targetNode.position.y * nodeScaleRatio
    const scaledNodeWidth = nodeWidth
    const scaledNodeHeight = nodeHeight
    const containerOffsetX = (centerWidth - previewWidth) / 2
    const containerOffsetY = (centerHeight - previewHeight) / 2 + previewOffsetY
    const sourceX = scaledSourceX + scaledNodeWidth + containerOffsetX
    const sourceY = scaledSourceY + scaledNodeHeight / 2 + containerOffsetY
    const targetX = scaledTargetX + containerOffsetX
    const targetY = scaledTargetY + scaledNodeHeight / 2 + containerOffsetY
    const midX = (sourceX + targetX) / 2
    return `M ${sourceX} ${sourceY} Q ${midX} ${sourceY}, ${midX} ${(sourceY + targetY) / 2} T ${targetX} ${targetY}`
  }

  useEffect(() => {
    const timers: Array<number> = []

    const userMessageAt = typingDuration + 200
    const typingIndicatorAt = userMessageAt + 350
    const aiMessageAt = typingIndicatorAt + 850
    const pipelineStep4At = aiMessageAt + 450
    const pipelineStep5At = pipelineStep4At + 450
    const pipelineStep6At = pipelineStep5At + 450
    const pipelineStep7At = pipelineStep6At + 450
    const pipelineStep8At = pipelineStep7At + 450
    const pipelineStep9At = pipelineStep8At + 550

    timers.push(window.setTimeout(() => setStep(1), userMessageAt))
    timers.push(window.setTimeout(() => setStep(2), typingIndicatorAt))
    timers.push(window.setTimeout(() => setStep(3), aiMessageAt))
    timers.push(window.setTimeout(() => setStep(4), pipelineStep4At))
    timers.push(window.setTimeout(() => setStep(5), pipelineStep5At))
    timers.push(window.setTimeout(() => setStep(6), pipelineStep6At))
    timers.push(window.setTimeout(() => setStep(7), pipelineStep7At))
    timers.push(window.setTimeout(() => setStep(8), pipelineStep8At))
    timers.push(window.setTimeout(() => setStep(9), pipelineStep9At))
    return () => timers.forEach((timer) => window.clearTimeout(timer))
  }, [typingDuration])

  useEffect(() => {
    stepRef.current = step
  }, [step])

  useEffect(() => {
    let frameId = 0
    const start = performance.now()
    const duration = Math.max(2200, typingDuration + 1400)
    const tick = (now: number) => {
      const progress = Math.min(1, (now - start) / duration)
      setAnimatedRadarProgress(progress * 100)
      if (progress < 1 && stepRef.current < 4) {
        frameId = requestAnimationFrame(tick)
      }
    }
    frameId = requestAnimationFrame(tick)
    return () => {
      if (frameId) cancelAnimationFrame(frameId)
    }
  }, [typingDuration])

  return (
    <div className="flex flex-1 overflow-hidden">
      <div className="w-[30%] min-w-[15rem] max-w-[18.75rem] bg-[#0f1623] border-r border-slate-800 flex flex-col z-20">
        <div className="p-3 border-b border-slate-800/50 flex justify-between items-center bg-[#151b2e]/50 shrink-0">
          <div className="text-xs font-semibold text-slate-300 flex items-center gap-2">
            <Sparkles className="w-3.5 h-3.5 text-violet-400" />
            {t('hero.pipeline.title')}
          </div>
        </div>
        <div className="flex-1 p-4 space-y-4 overflow-y-auto custom-scrollbar">
          {showUserMessage && (
            <div className="flex flex-col gap-1 items-end animate-[slideUp_0.3s_ease-out]">
              <div className="flex items-start gap-2">
                <div className="w-6 h-6 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0 order-2">
                  <User className="w-3 h-3 text-slate-400" />
                </div>
              <div className="bg-slate-800 text-slate-200 px-3 py-2 rounded-2xl rounded-tr-sm max-w-[95%] border border-slate-700 text-micro leading-relaxed">
                {prompt}
              </div>
              </div>
            </div>
          )}
          {showTyping && (
            <div className="flex flex-col gap-1 items-start animate-[fadeIn_0.2s_ease-out]">
              <div className="bg-violet-900/10 border border-violet-500/20 px-3 py-2 rounded-2xl rounded-tl-sm">
                <div className="flex gap-1">
                  <div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce" />
                  <div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce delay-75" />
                  <div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce delay-150" />
                </div>
              </div>
            </div>
          )}
          {showAiMessage && (
            <div className="flex flex-col gap-1 items-start animate-[slideUp_0.3s_ease-out]">
              <div className="flex items-center gap-1.5 mb-0.5">
                <Bot className="w-3 h-3 text-violet-400" />
                <span className="text-violet-400 text-nano font-bold">{t('hero.pipeline.aiLabel')}</span>
              </div>
              <div className="bg-violet-900/10 border border-violet-500/20 text-slate-200 px-3 py-2 rounded-2xl rounded-tl-sm text-micro leading-relaxed">
                <p>{t('hero.pipeline.aiReply')}</p>
              </div>
            </div>
          )}
          {step >= 9 && (
            <div className="mt-2 bg-[#0b0f19] border border-green-900/30 rounded-lg p-3 animate-[slideUp_0.3s_ease-out_backwards]">
              <div className="flex items-center gap-2 mb-2">
                <div className="w-4 h-4 rounded-full bg-green-500/20 flex items-center justify-center">
                  <Check className="w-2.5 h-2.5 text-green-500" />
                </div>
                <span className="text-micro text-green-400 font-medium">{t('hero.pipeline.ready')}</span>
              </div>
              <button className="w-full bg-violet-600 text-white text-micro font-bold py-1.5 rounded">
                {t('hero.pipeline.deploy')}
              </button>
            </div>
          )}
        </div>

        <div className="p-3 border-t border-slate-800/50 bg-[#0f1623] shrink-0">
          <div className="flex items-center gap-2 bg-[#1e293b] border border-slate-700/50 rounded-lg px-3 py-2">
            <Paperclip className="w-3 h-3 text-slate-500 cursor-pointer hover:text-slate-300" />
            <div className={`flex-1 text-micro font-mono ${isPendingInput ? 'text-slate-200' : 'text-slate-500'}`}>
              {isPendingInput ? (
                <span className="flex items-center min-h-[1rem]">
                  <TypingText text={prompt} speed={typingSpeed} />
                  <span className="typing-caret bg-violet-400/90 shadow-[0_0_6px_rgba(139,92,246,0.75)]" aria-hidden="true" />
                </span>
              ) : (
                t('hero.pipeline.inputPlaceholder')
              )}
            </div>
            <div
              className={`p-1.5 rounded-full shadow-lg cursor-pointer transition-all ${
                isPendingInput ? 'bg-blue-500 shadow-blue-500/30' : 'bg-blue-600/50 shadow-blue-500/10'
              }`}
            >
              <Send className="w-3 h-3 text-white" />
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 relative bg-[#0b0f19] overflow-hidden">
        <div className="absolute inset-0 bg-[#0b0f19]" />
        <div className="absolute inset-0 z-0 flex justify-around items-center overflow-hidden transition-opacity duration-700" style={{ opacity: matrixOpacity }}>
          {matrixColumns.map((column, colIdx) => (
            <div key={colIdx} className="text-micro text-indigo-400 font-mono whitespace-pre flex flex-col gap-3">
              {column.map((cell, rowIdx) => (
                <div key={rowIdx} className="matrix-blink" style={{ animationDelay: `${cell.delay}s` }}>
                  {cell.value}
                </div>
              ))}
            </div>
          ))}
        </div>
        <div className="relative z-10 w-full h-full flex items-center justify-center" style={heroSizeStyle}>
          <div className="relative w-[var(--hero-center-width)] h-[var(--hero-center-height)]">
            <svg
              className="absolute inset-0 w-full h-full pointer-events-none z-10"
              viewBox={`0 0 ${centerWidth} ${centerHeight}`}
              preserveAspectRatio="none"
            >
              <defs>
                <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
                  <feGaussianBlur stdDeviation="3" result="blur" />
                  <feComposite in="SourceGraphic" in2="blur" operator="over" />
                </filter>
                <filter id="center-glow">
                  <feGaussianBlur stdDeviation="5" result="coloredBlur" />
                  <feMerge>
                    <feMergeNode in="coloredBlur" />
                    <feMergeNode in="SourceGraphic" />
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

              <g style={{ opacity: radarOpacity, transition: 'opacity 0.6s ease' }}>
                <circle cx={radarCenterX} cy={radarCenterY} r={radarRadius} fill="url(#radarGradient)" opacity="0.35" />

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

              <g>
                {edges.map((edge) => {
                  const sourceNode = canvasNodes.find((node) => node.id === edge.source)
                  const targetNode = canvasNodes.find((node) => node.id === edge.target)
                  if (!sourceNode || !targetNode) return null
                  const sourceIndex = canvasNodes.findIndex((node) => node.id === edge.source)
                  const targetIndex = canvasNodes.findIndex((node) => node.id === edge.target)
                  const isEdgeActive = isNodePhase && sourceIndex <= activeNodeIndex && targetIndex <= activeNodeIndex
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
                          <animateMotion dur="2s" repeatCount="indefinite" path={getEdgePath(sourceNode, targetNode)} />
                        </circle>
                      )}
                    </g>
                  )
                })}
              </g>
            </svg>

            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="relative w-[var(--hero-preview-width)] h-[var(--hero-preview-height)]">
                {canvasNodes.map((node, index) => {
                  if (!isNodePhase) return null
                  const tone = toneStyles[node.tone]
                  const isActive = index <= activeNodeIndex
                  const scaledX = node.position.x * nodeScaleRatio
                  const scaledY = node.position.y * nodeScaleRatio
                  return (
                    <div
                      key={node.id}
                      style={{
                        left: `${scaledX}px`,
                        top: `${scaledY + previewOffsetY}px`,
                        opacity: isActive ? 1 : 0,
                        transform: isActive ? 'scale(1)' : 'scale(0.5)',
                        filter: isActive ? 'blur(0px)' : 'blur(8px)'
                      }}
                      className={`absolute w-[var(--hero-node-width)] h-[var(--hero-node-height)] rounded-xl border bg-[#0f172a] flex flex-col items-center justify-center gap-1 transition-all duration-300 z-30 ${
                        isActive ? `${tone.border} ${tone.shadow}` : 'border-slate-700'
                      }`}
                    >
                      {node.icon}
                      <div className="text-tiny text-slate-200 font-bold text-center leading-tight">
                        {node.title}
                        <br />
                        <span className={`text-mini ${tone.text} font-normal`}>{node.subtitle}</span>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>

          </div>
        </div>
      </div>
    </div>
  )
}

const AnalyticsView = () => {
  const { t } = useTranslation('home')
  const [step, setStep] = useState(0)
  const isChartReady = step >= 3
  const isSqlReady = step >= 3

  const prompt = t('hero.analytics.prompt')
  const typingSpeed = 36
  const typingDuration = getTypingDuration(prompt, typingSpeed)
  const isPendingInput = step < 1
  const showUserMessage = step >= 1
  const showTyping = step >= 2 && step < 3
  const showAiMessage = step >= 3

  useEffect(() => {
    const timers: Array<number> = []

    const userMessageAt = typingDuration + 200
    const typingIndicatorAt = userMessageAt + 350
    const aiMessageAt = typingIndicatorAt + 850

    timers.push(window.setTimeout(() => setStep(1), userMessageAt))
    timers.push(window.setTimeout(() => setStep(2), typingIndicatorAt))
    timers.push(window.setTimeout(() => setStep(3), aiMessageAt))
    return () => timers.forEach((timer) => window.clearTimeout(timer))
  }, [typingDuration])

  return (
    <div className="flex flex-1 overflow-hidden">
      <div className="w-[30%] min-w-[15rem] max-w-[18.75rem] bg-[#0f1623] border-r border-slate-800 flex flex-col z-20">
        <div className="p-3 border-b border-slate-800/50 flex justify-between items-center bg-[#151b2e]/50 shrink-0">
          <div className="text-xs font-semibold text-slate-300 flex items-center gap-2">
            <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
            {t('hero.analytics.title')}
          </div>
        </div>
        <div className="flex-1 p-4 space-y-4 overflow-y-auto custom-scrollbar">
          {showUserMessage && (
            <div className="flex flex-col gap-1 items-end animate-[slideUp_0.3s_ease-out]">
              <div className="flex items-start gap-2">
                <div className="w-6 h-6 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0 order-2">
                  <User className="w-3 h-3 text-slate-400" />
                </div>
                <div className="bg-slate-800 text-slate-200 px-3 py-2 rounded-2xl rounded-tr-sm max-w-[95%] border border-slate-700 text-micro leading-relaxed">
                  {prompt}
                </div>
              </div>
            </div>
          )}
          {showTyping && (
            <div className="flex flex-col gap-1 items-start animate-[fadeIn_0.2s_ease-out]">
              <div className="bg-cyan-900/10 border border-cyan-500/20 px-3 py-2 rounded-2xl rounded-tl-sm">
                <div className="flex gap-1">
                  <div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce" />
                  <div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce delay-75" />
                  <div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce delay-150" />
                </div>
              </div>
            </div>
          )}
          {showAiMessage && (
            <div className="flex flex-col gap-1 items-start animate-[slideUp_0.3s_ease-out]">
              <div className="flex items-center gap-1.5 mb-0.5">
                <Bot className="w-3 h-3 text-cyan-400" />
                <span className="text-cyan-400 text-nano font-bold">{t('hero.analytics.aiLabel')}</span>
              </div>
              <div className="bg-cyan-900/10 border border-cyan-500/20 text-slate-200 px-3 py-2 rounded-2xl rounded-tl-sm text-micro leading-relaxed">
                <p>{t('hero.analytics.aiReply')}</p>
              </div>
            </div>
          )}
        </div>

        <div className="p-3 border-t border-slate-800/50 bg-[#0f1623] shrink-0">
          <div className="flex items-center gap-2 bg-[#1e293b] border border-slate-700/50 rounded-lg px-3 py-2">
            <Paperclip className="w-3 h-3 text-slate-500 cursor-pointer hover:text-slate-300" />
            <div className={`flex-1 text-micro font-mono ${isPendingInput ? 'text-slate-200' : 'text-slate-500'}`}>
              {isPendingInput ? (
                <span className="flex items-center min-h-[1rem]">
                  <TypingText text={prompt} speed={typingSpeed} />
                  <span className="typing-caret bg-cyan-400/90 shadow-[0_0_6px_rgba(34,211,238,0.75)]" aria-hidden="true" />
                </span>
              ) : (
                t('hero.analytics.inputPlaceholder')
              )}
            </div>
            <div
              className={`p-1.5 rounded-full shadow-lg cursor-pointer transition-all ${
                isPendingInput ? 'bg-blue-500 shadow-blue-500/30' : 'bg-blue-600/50 shadow-blue-500/10'
              }`}
            >
              <Send className="w-3 h-3 text-white" />
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 relative bg-[#0b0f19] flex flex-col p-6 overflow-hidden">
        <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:20px_20px]" />

        <div className="relative z-10 w-full h-full flex flex-col gap-4">
          <div
            className={`w-full bg-[#020410] border border-slate-700 rounded-lg p-3 font-mono text-nano transition-all duration-300 ${
              isSqlReady ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
            }`}
          >
            <div className="flex items-center justify-between mb-2 pb-2 border-b border-slate-800">
              <div className="text-cyan-400 flex items-center gap-1">
                <Terminal className="w-3 h-3" /> {t('hero.analytics.sqlTitle')}
              </div>
              <div className="text-slate-500">{t('hero.analytics.sqlExecution', { ms: '24ms' })}</div>
            </div>
            <div className="text-slate-300 space-y-0.5">
              <div>
                <span className="text-violet-400">SELECT</span> DATE(order_date) <span className="text-violet-400">AS</span> day,
                <span className="text-yellow-400"> SUM</span>(amount)
              </div>
              <div>
                <span className="text-violet-400">FROM</span> orders <span className="text-violet-400">WHERE</span> region =
                <span className="text-green-400"> 'East_China'</span>
              </div>
              <div>
                <span className="text-violet-400">GROUP BY</span> 1 <span className="text-violet-400">ORDER BY</span> 1;
              </div>
            </div>
          </div>

          <SmartVisualizationChart isActive={isChartReady} className="flex-1 min-h-64 delay-100" />
        </div>
      </div>
    </div>
  )
}
