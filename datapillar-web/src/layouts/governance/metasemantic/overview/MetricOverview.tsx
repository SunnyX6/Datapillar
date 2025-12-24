import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Target, X, Info, Code, GitBranch, Layers, Share2, History, Database, Hash, Loader2, Check } from 'lucide-react'
import { Badge } from '../components'
import type { Metric } from '../types'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { fetchMetricVersion, fetchMetricVersions } from '@/services/oneMetaService'

/** 指标类型标签映射 */
const TYPE_LABELS: Record<string, { label: string; variant: 'blue' | 'purple' | 'amber' }> = {
  ATOMIC: { label: '原子指标', variant: 'blue' },
  DERIVED: { label: '派生指标', variant: 'purple' },
  COMPOSITE: { label: '复合指标', variant: 'amber' }
}

interface MetricVersionDetail {
  calculationFormula?: string
  unit?: string
  aggregationLogic?: string
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  refColumnName?: string
}

interface MetricOverviewProps {
  metric: Metric
  onClose: () => void
}

export function MetricOverview({ metric, onClose }: MetricOverviewProps) {
  const [versionDetail, setVersionDetail] = useState<MetricVersionDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [versions, setVersions] = useState<number[]>([])
  const [selectedVersion, setSelectedVersion] = useState(metric.currentVersion)
  const [showVersionPanel, setShowVersionPanel] = useState(false)
  const [loadingVersions, setLoadingVersions] = useState(false)

  // 加载版本详情
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchMetricVersion(metric.code, selectedVersion)
      .then((data) => {
        if (!cancelled) {
          setVersionDetail({
            calculationFormula: data.calculationFormula,
            unit: data.unit,
            aggregationLogic: data.aggregationLogic,
            refCatalogName: data.refCatalogName,
            refSchemaName: data.refSchemaName,
            refTableName: data.refTableName,
            refColumnName: data.refColumnName
          })
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
      const data = await fetchMetricVersions(metric.code)
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

  const typeInfo = TYPE_LABELS[metric.type] || { label: metric.type, variant: 'blue' as const }

  // 构建资产引用路径
  const assetPath = versionDetail?.refTableName
    ? [versionDetail.refCatalogName, versionDetail.refSchemaName, versionDetail.refTableName, versionDetail.refColumnName]
        .filter(Boolean)
        .join(' / ')
    : null

  return createPortal(
    <div className="fixed inset-0 z-[100] flex justify-end">
      <div
        className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-300"
        onClick={onClose}
      />
      <div className="relative w-drawer-responsive h-full bg-white dark:bg-slate-900 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500">
        {/* 头部 */}
        <div className="p-5 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between flex-shrink-0 bg-slate-50/50 dark:bg-slate-800/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-purple-600 text-white rounded-xl shadow-md">
              <Target size={iconSizeToken.large} />
            </div>
            <div>
              <h2 className="font-semibold text-slate-900 dark:text-slate-100 text-body">指标详情</h2>
              <div className="text-micro text-slate-400 dark:text-slate-500 font-semibold uppercase tracking-wider">Metric Overview</div>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-full transition-colors">
            <X size={iconSizeToken.large} className="text-slate-400" />
          </button>
        </div>

        {/* 内容区域 */}
        <div className="flex-1 min-h-0 overflow-auto p-6 custom-scrollbar">
          {/* 指标名称和类型 */}
          <div className="mb-6">
            <div className="flex items-center gap-2 mb-2">
              <h1 className="text-title font-bold text-slate-900 dark:text-slate-100 tracking-tight">{metric.name}</h1>
              <Badge variant={typeInfo.variant}>{typeInfo.label}</Badge>
            </div>
            <div className="flex items-center gap-3 mb-3">
              <span className="text-body-sm font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight">
                {metric.code}
              </span>
              <span className="text-micro font-mono text-blue-600 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded">
                v{selectedVersion}
                {selectedVersion !== metric.currentVersion && (
                  <span className="text-amber-500 ml-1">(非当前)</span>
                )}
              </span>
            </div>
            <p className="text-slate-500 dark:text-slate-400 text-body-sm leading-relaxed">
              {metric.comment || '暂无业务描述...'}
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
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">{typeInfo.label}</div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">数据类型</div>
                  <div className="text-body-sm font-mono font-semibold text-cyan-600 dark:text-cyan-400">{metric.dataType || '-'}</div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">当前版本</div>
                  <div className="text-body-sm font-mono font-semibold text-slate-700 dark:text-slate-300">v{metric.currentVersion}</div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">单位</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {loading ? <Loader2 size={14} className="animate-spin" /> : versionDetail?.unit || '-'}
                  </div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建人</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">{metric.audit?.creator || '-'}</div>
                </div>
                <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
                  <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">创建时间</div>
                  <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
                    {metric.audit?.createTime ? new Date(metric.audit.createTime).toLocaleDateString() : '-'}
                  </div>
                </div>
              </div>
            </section>

            {/* 资产引用（原子指标） */}
            {metric.type === 'ATOMIC' && (
              <section>
                <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                  <Database size={iconSizeToken.small} className="text-emerald-500" /> 物理资产引用
                </div>
                {loading ? (
                  <div className="flex items-center justify-center py-4">
                    <Loader2 size={20} className="animate-spin text-slate-400" />
                  </div>
                ) : assetPath ? (
                  <div className="p-4 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-100 dark:border-emerald-800">
                    <div className="flex items-center gap-2 text-emerald-700 dark:text-emerald-400">
                      <Hash size={14} />
                      <span className="font-mono text-body-sm">{assetPath}</span>
                    </div>
                  </div>
                ) : (
                  <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-center text-slate-400 text-caption">
                    暂无物理资产引用
                  </div>
                )}
              </section>
            )}

            {/* 公式表达式 */}
            <section>
              <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <Code size={iconSizeToken.small} className="text-blue-500" /> 计算公式
              </div>
              {loading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 size={20} className="animate-spin text-slate-400" />
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
      </div>
    </div>,
    document.body
  )
}
