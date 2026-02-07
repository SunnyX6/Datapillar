import { useState, useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Target, X, Info, Code, GitBranch, Layers, Share2, History, Loader2, Check, RotateCcw } from 'lucide-react'
import { Badge } from '../components'
import type { Metric } from '../types'
import { drawerWidthClassMap, iconSizeToken } from '@/design-tokens/dimensions'
import { fetchMetricVersion, fetchMetricVersionNumbers, switchMetricVersion as apiSwitchMetricVersion } from '@/services/oneMetaSemanticService'
import { formatTime } from '@/lib/utils'

/** 指标类型标签映射 */
const TYPE_LABELS: Record<string, { label: string; variant: 'blue' | 'purple' | 'amber' }> = {
  ATOMIC: { label: '原子指标', variant: 'blue' },
  DERIVED: { label: '派生指标', variant: 'purple' },
  COMPOSITE: { label: '复合指标', variant: 'amber' }
}

const skeletonBaseClassName = 'inline-block animate-pulse rounded bg-slate-200/70 dark:bg-slate-700/40'

function SkeletonBlock({ className }: { className: string }) {
  return <span aria-hidden="true" className={`${skeletonBaseClassName} ${className}`} />
}

interface MetricVersionDetail {
  name?: string
  code?: string
  type?: string
  dataType?: string
  comment?: string
  calculationFormula?: string
  unit?: string
  unitName?: string
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  refColumnName?: string
  audit?: {
    creator?: string
    createTime?: string
  }
}

interface MetricOverviewProps {
  metric: Metric
  onClose: () => void
  onVersionSwitch?: (updatedMetric: Metric) => void
}

export function MetricOverview({ metric, onClose, onVersionSwitch }: MetricOverviewProps) {
  const [versionDetail, setVersionDetail] = useState<MetricVersionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [versions, setVersions] = useState<number[]>([])
  const [selectedVersion, setSelectedVersion] = useState(metric.currentVersion)
  const [showVersionPanel, setShowVersionPanel] = useState(false)
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [switching, setSwitching] = useState(false)

  // 当 metric 变化时，重置版本状态
  useEffect(() => {
    setSelectedVersion(metric.currentVersion)
    setVersions([])
    setShowVersionPanel(false)
  }, [metric.code, metric.currentVersion])

  // 使用 ref 跟踪是否有数据，避免将 versionDetail 加入依赖数组
  const hasDataRef = useRef(!!versionDetail)

  // 加载版本详情
  useEffect(() => {
    let cancelled = false
    // 只在首次加载时显示 loading，切换版本时保持旧数据避免抖动
    if (!hasDataRef.current) {
      setLoading(true)
    }
    fetchMetricVersion(metric.code, selectedVersion)
      .then((data) => {
        if (!cancelled) {
          setVersionDetail({
            name: data.name,
            code: data.code,
            type: data.type,
            dataType: data.dataType,
            comment: data.comment,
            calculationFormula: data.calculationFormula,
            unit: data.unit,
            unitName: data.unitName,
            refCatalogName: data.refCatalogName,
            refSchemaName: data.refSchemaName,
            refTableName: data.refTableName,
            refColumnName: data.refColumnName,
            audit: data.audit
          })
          hasDataRef.current = true
        }
      })
      .catch(() => {
        if (!cancelled) setVersionDetail(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
  }, [metric.code, selectedVersion])

  // 加载版本列表
  const loadVersions = async () => {
    if (versions.length > 0) {
      setShowVersionPanel(true)
      return
    }
    setLoadingVersions(true)
    try {
      const data = await fetchMetricVersionNumbers(metric.code)
      setVersions(data.sort((a, b) => b - a)) // 降序排列
      setShowVersionPanel(true)
    } catch {
      setVersions([])
    } finally {
      setLoadingVersions(false)
    }
  }

  const handleVersionSelect = (version: number) => {
    setSelectedVersion(version)
    setShowVersionPanel(false)
  }

  // 切换当前版本
  const handleSwitchVersion = async () => {
    if (selectedVersion === metric.currentVersion) return
    setSwitching(true)
    try {
      const versionData = await apiSwitchMetricVersion(metric.code, selectedVersion)
      // 用返回的版本数据构造更新后的 Metric
      const updatedMetric: Metric = {
        ...metric,
        name: versionData.name,
        code: versionData.code,
        type: versionData.type,
        dataType: versionData.dataType,
        unit: versionData.unit,
        unitName: versionData.unitName,
        comment: versionData.comment,
        currentVersion: selectedVersion
      }
      onVersionSwitch?.(updatedMetric)
      onClose()
    } catch {
      // 错误已由统一客户端通过 toast 显示
    } finally {
      setSwitching(false)
    }
  }

  const typeKey = (versionDetail?.type || '').toUpperCase()
  const typeInfo = TYPE_LABELS[typeKey] || { label: typeKey || '-', variant: 'blue' as const }

  return createPortal(
    <aside className={`fixed right-0 top-14 bottom-0 z-30 ${drawerWidthClassMap.responsive} bg-white dark:bg-slate-900 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500`}>
      {/* 头部：高度与「指标中心」列表页头部对齐 */}
      <div className="h-12 md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 md:px-6 flex items-center justify-between flex-shrink-0 shadow-sm">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-purple-600 text-white rounded-lg shadow-sm">
            <Target size={iconSizeToken.medium} />
          </div>
          <h2 className="text-body-sm font-semibold text-slate-800 dark:text-slate-100">指标详情</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
          aria-label="关闭指标详情"
        >
          <X size={iconSizeToken.large} className="text-slate-400" />
        </button>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 min-h-0 overflow-auto p-6 custom-scrollbar">
        {/* 指标名称和类型 */}
        <div className="mb-6">
          <div className="flex items-center gap-2 mb-2">
            <h1 className="text-heading font-semibold text-slate-900 dark:text-slate-100 tracking-tight">
              {loading ? <SkeletonBlock className="h-6 w-56" /> : versionDetail?.name || '-'}
            </h1>
            {loading ? <SkeletonBlock className="h-5 w-16 rounded-full" /> : <Badge variant={typeInfo.variant}>{typeKey || '-'}</Badge>}
          </div>
          <div className="flex items-center gap-3 mb-3">
            <span className="text-body-xs font-mono font-semibold text-purple-600 dark:text-purple-400 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-200 dark:border-purple-700 px-2.5 py-0.5 rounded-lg tracking-wide">
              {versionDetail?.code || metric.code}
            </span>
            <span className="text-micro font-mono text-blue-600 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded">
              v{selectedVersion}
              {selectedVersion !== metric.currentVersion && (
                <span className="text-amber-500 ml-1">(非当前)</span>
              )}
            </span>
            {selectedVersion !== metric.currentVersion && (
              <button
                onClick={handleSwitchVersion}
                disabled={switching}
                className="text-micro font-medium text-white bg-emerald-500 hover:bg-emerald-600 px-2 py-0.5 rounded transition-colors disabled:opacity-50 flex items-center gap-1"
              >
                {switching ? (
                  <Loader2 size={12} className="animate-spin" />
                ) : (
                  <RotateCcw size={12} />
                )}
                设为当前版本
              </button>
            )}
          </div>
          <p className="text-slate-500 dark:text-slate-400 text-body-sm leading-relaxed min-h-[20px]">
            {loading ? <SkeletonBlock className="h-5 w-full max-w-[26rem]" /> : versionDetail?.comment || '暂无业务描述...'}
          </p>
        </div>

        <div className="space-y-6">
            {/* 技术属性 */}
            <section>
              <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Info size={iconSizeToken.small} className="text-blue-500" /> 技术属性
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">指标类型</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {loading ? <SkeletonBlock className="h-5 w-16" /> : typeKey || '-'}
                  </div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">数据类型</div>
                  <div className="text-body-sm font-mono font-semibold text-cyan-600 dark:text-cyan-400">
                    {loading ? <SkeletonBlock className="h-5 w-24" /> : versionDetail?.dataType || '-'}
                  </div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">当前版本</div>
                  <div className="text-body-sm font-mono font-semibold text-slate-700 dark:text-slate-300">v{metric.currentVersion}</div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">单位</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {loading ? <SkeletonBlock className="h-5 w-20" /> : (
                      versionDetail?.unitName ? (
                        <>
                          {versionDetail.unitSymbol && <span className="text-amber-500 mr-1">{versionDetail.unitSymbol}</span>}
                          {versionDetail.unitName}
                        </>
                      ) : '-'
                    )}
                  </div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建人</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {loading ? <SkeletonBlock className="h-5 w-20" /> : versionDetail?.audit?.creator || '-'}
                  </div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建时间</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {loading ? <SkeletonBlock className="h-5 w-28" /> : formatTime(versionDetail?.audit?.createTime)}
                  </div>
                </div>
              </div>
            </section>

            {/* 公式表达式 */}
            <section>
              <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Code size={iconSizeToken.small} className="text-blue-500" /> 计算公式
              </div>
              {loading ? (
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-700">
                  <SkeletonBlock className="h-4 w-11/12" />
                  <SkeletonBlock className="h-4 w-9/12 mt-2" />
                  <SkeletonBlock className="h-4 w-10/12 mt-2" />
                </div>
              ) : versionDetail?.calculationFormula ? (
                <div className="p-4 rounded-xl bg-slate-900 dark:bg-slate-950 border border-slate-800 shadow-inner">
                  <code className="text-caption text-emerald-400 font-mono leading-relaxed whitespace-pre-wrap break-all">
                    {versionDetail.calculationFormula}
                  </code>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-center text-slate-400 text-caption">
                  暂无计算公式
                </div>
              )}
            </section>

            {/* 血缘预览 */}
            <section>
              <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <GitBranch size={iconSizeToken.small} className="text-blue-500" /> 上下游血缘预览
              </div>
              <div className="h-32 rounded-xl border-2 border-dashed border-slate-100 dark:border-slate-800 flex items-center justify-center bg-slate-50/50 dark:bg-slate-800/50">
                <div className="flex flex-col items-center gap-1.5 text-slate-300 dark:text-slate-600">
                  <Layers size={iconSizeToken.huge} />
                  <span className="text-caption font-medium">血缘图谱加载中...</span>
                </div>
              </div>
            </section>
          </div>
        </div>

        {/* 底部操作 */}
        <div className="p-5 border-t border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 flex gap-3 flex-shrink-0 relative">
          <button className="flex-1 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 py-2.5 rounded-xl font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-all flex items-center justify-center gap-1.5 shadow-sm text-body-sm">
            <Share2 size={iconSizeToken.medium} /> 资产分享
          </button>
          <div className="flex-1 relative">
            <button
              onClick={loadVersions}
              disabled={loadingVersions}
              className="w-full bg-blue-600 text-white py-2.5 rounded-xl font-medium hover:bg-blue-700 shadow-lg transition-all flex items-center justify-center gap-1.5 text-body-sm disabled:opacity-50"
            >
              {loadingVersions ? (
                <Loader2 size={iconSizeToken.medium} className="animate-spin" />
              ) : (
                <History size={iconSizeToken.medium} />
              )}
              查看版本历史
            </button>

            {/* 版本选择弹出层 */}
            {showVersionPanel && (
              <div className="absolute bottom-full mb-2 left-0 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg overflow-hidden z-10">
                <div className="p-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
                  <span className="text-micro font-semibold text-slate-500">版本历史</span>
                  <button
                    onClick={() => setShowVersionPanel(false)}
                    className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded text-slate-400"
                  >
                    <X size={12} />
                  </button>
                </div>
                <div className="max-h-48 overflow-y-auto custom-scrollbar">
                  {versions.length === 0 ? (
                    <div className="p-4 text-center text-slate-400 text-caption">暂无历史版本</div>
                  ) : (
                    versions.map((v) => (
                      <button
                        key={v}
                        onClick={() => handleVersionSelect(v)}
                        className={`w-full flex items-center justify-between px-3 py-2 text-caption transition-colors ${
                          v === selectedVersion
                            ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600'
                            : 'hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300'
                        }`}
                      >
                        <span className="font-mono">v{v}</span>
                        <span className="flex items-center gap-1">
                          {v === metric.currentVersion && (
                            <span className="text-micro text-emerald-500 font-medium">当前</span>
                          )}
                          {v === selectedVersion && <Check size={14} className="text-blue-600" />}
                        </span>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </aside>,
      document.body
    )
}
