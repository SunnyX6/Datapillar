import { useState, useEffect, useCallback } from 'react'
import { Target, ArrowLeft, Plus, List, Grid, Box, Trash2, Loader2, Pencil } from 'lucide-react'
import { Badge } from '../components'
import type { Metric, ViewMode } from '../types'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { useSearchStore, useSemanticStatsStore } from '@/stores'
import { useInfiniteScroll } from '@/hooks'
import { fetchMetrics, deleteMetric, registerMetric, alterMetricVersion } from '@/services/oneMetaSemanticService'
import { MetricFormModal, type MetricFormData } from './form/MetricForm'
import { buildDataTypeString, type DataTypeValue } from '@/components/ui'
import { ComponentLibrarySidebar } from './ComponentLibrarySidebar'
import { formatTime } from '@/lib/utils'

/** 每页加载数量 */
const PAGE_SIZE = 20

/** 指标类型颜色映射 */
const TYPE_VARIANTS: Record<string, 'blue' | 'purple' | 'amber'> = {
  ATOMIC: 'blue',
  DERIVED: 'purple',
  COMPOSITE: 'amber'
}

function MetricCard({
  metric,
  onClick,
  onDelete,
  onEdit
}: {
  metric: Metric
  onClick: () => void
  onDelete: (code: string) => void
  onEdit: (metric: Metric) => void
}) {
  const [deleting, setDeleting] = useState(false)

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (deleting) return
    setDeleting(true)
    try {
      await deleteMetric(metric.code)
      onDelete(metric.code)
    } catch {
      setDeleting(false)
    }
  }

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    onEdit(metric)
  }

  const typeVariant = TYPE_VARIANTS[metric.type?.toUpperCase()] || 'blue'

  return (
    <div
      onClick={onClick}
      className="group bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-4 hover:shadow-md hover:border-blue-300 dark:hover:border-blue-600 transition-all cursor-pointer relative overflow-hidden"
    >
      <div className="flex justify-between items-start mb-2">
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-slate-800 dark:text-slate-100 text-body-sm truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
            {metric.name}
          </h3>
          <div className="text-micro font-mono text-slate-400 dark:text-slate-500 mt-0.5 uppercase tracking-tight truncate">
            {metric.code}
          </div>
        </div>
        <Badge variant={typeVariant}>{metric.type?.toUpperCase()}</Badge>
      </div>

      <p className="text-caption text-slate-500 dark:text-slate-400 line-clamp-2 mb-3 leading-relaxed">
        {metric.comment || '暂无描述...'}
      </p>

      <div className="flex items-center justify-between pt-3 border-t border-slate-100 dark:border-slate-800">
        <div className="flex items-center gap-2">
          <div className="w-5 h-5 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-micro font-semibold text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
            {metric.audit?.creator?.[0] || 'U'}
          </div>
          <span className="text-micro font-medium text-slate-600 dark:text-slate-400">{metric.audit?.creator || '-'}</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleEdit}
            className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
            title="编辑"
          >
            <Pencil size={iconSizeToken.small} />
          </button>
          <button
            onClick={handleDelete}
            disabled={deleting}
            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-50"
            title="删除"
          >
            {deleting ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Trash2 size={iconSizeToken.small} />}
          </button>
        </div>
      </div>
    </div>
  )
}

function MetricRow({
  metric,
  onClick,
  onDelete,
  onEdit
}: {
  metric: Metric
  onClick: () => void
  onDelete: (code: string) => void
  onEdit: (metric: Metric) => void
}) {
  const [deleting, setDeleting] = useState(false)

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (deleting) return
    setDeleting(true)
    try {
      await deleteMetric(metric.code)
      onDelete(metric.code)
    } catch {
      setDeleting(false)
    }
  }

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    onEdit(metric)
  }

  const typeVariant = TYPE_VARIANTS[metric.type?.toUpperCase()] || 'blue'

  return (
    <tr
      onClick={onClick}
      className="group hover:bg-blue-50/40 dark:hover:bg-blue-900/20 transition-colors cursor-pointer border-b border-slate-100 dark:border-slate-800 last:border-0"
    >
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400">
            <Target size={iconSizeToken.small} />
          </div>
          <div className="min-w-0">
            <div className="font-medium text-slate-800 dark:text-slate-100 text-body-sm group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors truncate">
              {metric.name}
            </div>
            <div className="text-micro font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight truncate">{metric.code}</div>
          </div>
        </div>
      </td>
      <td className="px-3 py-3 text-center">
        <Badge variant={typeVariant}>{metric.type?.toUpperCase()}</Badge>
      </td>
      <td className="px-3 py-3 text-center">
        <span className="font-mono text-micro text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-900/30 px-2 py-0.5 rounded border border-cyan-100 dark:border-cyan-800">
          {metric.dataType || '-'}
        </span>
      </td>
      <td className="px-3 py-3 text-center">
        <span className="text-caption text-slate-600 dark:text-slate-400">
          {metric.unitName || '-'}
        </span>
      </td>
      <td className="px-3 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400 line-clamp-1">{metric.comment || '-'}</div>
      </td>
      <td className="px-3 py-3 text-center">
        <span className="text-micro font-mono text-slate-600 dark:text-slate-400">v{metric.currentVersion}</span>
      </td>
      <td className="px-3 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400 truncate">{metric.audit?.creator || '-'}</div>
      </td>
      <td className="px-3 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400">{formatTime(metric.audit?.createTime)}</div>
      </td>
      <td className="px-3 py-3">
        <div className="flex items-center justify-center gap-1">
          <button
            onClick={handleEdit}
            className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
            title="编辑"
          >
            <Pencil size={iconSizeToken.small} />
          </button>
          <button
            onClick={handleDelete}
            disabled={deleting}
            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-50"
            title="删除"
          >
            {deleting ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Trash2 size={iconSizeToken.small} />}
          </button>
        </div>
      </td>
    </tr>
  )
}

interface MetricExplorerProps {
  onBack: () => void
  onOpenDrawer: (metric: Metric) => void
  updatedMetric?: Metric | null
}

export function MetricExplorer({ onBack, onOpenDrawer, updatedMetric }: MetricExplorerProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('LIST')
  const searchTerm = useSearchStore((state) => state.searchTerm)
  const setMetricsTotal = useSemanticStatsStore((state) => state.setMetricsTotal)

  // 数据状态
  const [metrics, setMetrics] = useState<Metric[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  // 新建弹窗状态
  const [showNewModal, setShowNewModal] = useState(false)
  const [saving, setSaving] = useState(false)

  // 编辑弹窗状态
  const [editingMetric, setEditingMetric] = useState<Metric | null>(null)

  // 是否还有更多数据
  const hasMore = metrics.length < total

  // 加载首页数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchMetrics(0, PAGE_SIZE)
      setMetrics(result.items)
      setTotal(result.total)
      setMetricsTotal(result.total)
    } catch {
      // 加载失败时保持空列表
    } finally {
      setLoading(false)
    }
  }, [setMetricsTotal])

  // 加载更多数据
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const result = await fetchMetrics(metrics.length, PAGE_SIZE)
      setMetrics((prev) => [...prev, ...result.items])
      setTotal(result.total)
    } catch {
      // 加载失败
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, metrics.length])

  // 无限滚动
  const { sentinelRef } = useInfiniteScroll({
    hasMore,
    loading: loadingMore,
    onLoadMore: loadMore
  })

  useEffect(() => {
    loadData()
  }, [loadData])

  // 当外部传入更新的指标时，更新列表中对应项
  useEffect(() => {
    if (updatedMetric) {
      setMetrics((prev) =>
        prev.map((m) => (m.code === updatedMetric.code ? updatedMetric : m))
      )
    }
  }, [updatedMetric])

  // 过滤指标
  const filteredMetrics = metrics.filter(
    (m) =>
      m.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      m.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (m.comment && m.comment.toLowerCase().includes(searchTerm.toLowerCase()))
  )

  // 删除指标
  const handleDelete = (code: string) => {
    setMetrics((prev) => prev.filter((m) => m.code !== code))
    setTotal((prev) => prev - 1)
  }

  // 保存新指标
  const handleSaveNewMetric = async (form: MetricFormData) => {
    if (saving) return
    setSaving(true)
    try {
      // 构建完整的 dataType 字符串（包含精度）
      const dataTypeValue: DataTypeValue = {
        type: form.dataType,
        precision: form.precision,
        scale: form.scale
      }
      // 构建 parentMetricCodes：派生指标取 baseCode，复合指标取 compositeMetrics
      let parentMetricCodes: string[] | undefined
      if (form.type === 'DERIVED' && form.baseCode) {
        parentMetricCodes = [form.baseCode]
      } else if (form.type === 'COMPOSITE' && form.compositeMetrics && form.compositeMetrics.length > 0) {
        parentMetricCodes = form.compositeMetrics.map((m) => m.code)
      }

      await registerMetric({
        name: form.name.trim(),
        code: form.code.trim().toUpperCase(),
        type: form.type,
        dataType: form.dataType ? buildDataTypeString(dataTypeValue) : undefined,
        comment: form.comment.trim() || undefined,
        unit: form.unit.trim() || undefined,
        calculationFormula: form.formula.trim() || undefined,
        parentMetricCodes,
        refTableId: (form.type === 'ATOMIC' || form.type === 'DERIVED') ? form.refTableId : undefined,
        measureColumnIds: form.type === 'ATOMIC' && form.measureColumns.length > 0
          ? JSON.stringify(form.measureColumns.map(c => c.id).filter(Boolean))
          : undefined,
        filterColumnIds: (form.type === 'ATOMIC' || form.type === 'DERIVED') && form.filterColumns.length > 0
          ? JSON.stringify(form.filterColumns.map(c => c.id).filter(Boolean))
          : undefined
      })
      // 构造新指标对象，直接添加到列表
      const newMetric: Metric = {
        name: form.name.trim(),
        code: form.code.trim().toUpperCase(),
        type: form.type,
        dataType: form.dataType ? buildDataTypeString(dataTypeValue) : undefined,
        unit: form.unit.trim() || undefined,
        unitName: form.unitName,
        comment: form.comment.trim() || undefined,
        currentVersion: 1,
        lastVersion: 1,
        audit: {
          creator: 'anonymous',
          createTime: new Date().toISOString()
        }
      }
      setMetrics((prev) => [newMetric, ...prev])
      setTotal((prev) => prev + 1)
      setShowNewModal(false)
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  // 保存编辑的指标
  const handleSaveEditMetric = async (form: MetricFormData) => {
    if (saving || !editingMetric) return
    setSaving(true)
    try {
      // 构建完整的 dataType 字符串（包含精度）
      const dataTypeValue: DataTypeValue = {
        type: form.dataType,
        precision: form.precision,
        scale: form.scale
      }
      const fullDataType = form.dataType ? buildDataTypeString(dataTypeValue) : undefined

      // 构建 parentMetricCodes：派生指标取 baseCode，复合指标取 compositeMetrics
      let parentMetricCodes: string[] | undefined
      if (form.type === 'DERIVED' && form.baseCode) {
        parentMetricCodes = [form.baseCode]
      } else if (form.type === 'COMPOSITE' && form.compositeMetrics && form.compositeMetrics.length > 0) {
        parentMetricCodes = form.compositeMetrics.map((m) => m.code)
      }

      // 直接调用 alterMetricVersion，会自动创建新版本
      const versionData = await alterMetricVersion(editingMetric.code, editingMetric.currentVersion, {
        metricName: form.name.trim(),
        metricCode: form.code.trim(),
        metricType: form.type,
        dataType: fullDataType,
        comment: form.comment.trim() || undefined,
        unit: form.unit.trim() || undefined,
        unitName: form.unitName || undefined,
        parentMetricCodes,
        calculationFormula: form.formula.trim() || undefined,
        refTableId: form.refTableId,
        measureColumnIds: form.measureColumns.length > 0 ? JSON.stringify(form.measureColumns.map(c => c.id).filter(Boolean)) : undefined,
        filterColumnIds: form.filterColumns.length > 0 ? JSON.stringify(form.filterColumns.map(c => c.id).filter(Boolean)) : undefined
      })

      // 用后端返回的版本数据更新列表
      const updatedMetric: Metric = {
        ...editingMetric,
        name: versionData.name,
        code: versionData.code,
        type: versionData.type,
        dataType: versionData.dataType,
        unit: versionData.unit,
        unitName: versionData.unitName,
        comment: versionData.comment,
        currentVersion: versionData.version
      }
      setMetrics((prev) =>
        prev.map((m) => (m.code === editingMetric.code ? updatedMetric : m))
      )
      setEditingMetric(null)
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex-1 flex overflow-hidden bg-slate-50/40 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      {/* 主内容区域 */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <div className="h-12 @md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 @md:px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
          <div className="flex items-center gap-2 @md:gap-3">
            <button onClick={onBack} className="p-1 @md:p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all">
              <ArrowLeft size={iconSizeToken.large} />
            </button>
            <div className="flex items-center gap-2">
              <h2 className="text-body-sm @md:text-subtitle font-semibold text-slate-800 dark:text-slate-100">指标中心</h2>
              <Badge variant="blue">
                {filteredMetrics.length} / {total}
              </Badge>
            </div>
          </div>

          <div className="flex items-center gap-2 @md:gap-3">
            <div className="flex bg-slate-100 dark:bg-slate-800 p-0.5 rounded-lg border border-slate-200 dark:border-slate-700">
              <button
                onClick={() => setViewMode('LIST')}
                className={`p-1 @md:p-1.5 rounded-md transition-all ${viewMode === 'LIST' ? 'bg-white dark:bg-slate-700 text-blue-600 shadow-sm' : 'text-slate-400 hover:text-slate-600'}`}
              >
                <List size={iconSizeToken.medium} />
              </button>
              <button
                onClick={() => setViewMode('CARD')}
                className={`p-1 @md:p-1.5 rounded-md transition-all ${viewMode === 'CARD' ? 'bg-white dark:bg-slate-700 text-blue-600 shadow-sm' : 'text-slate-400 hover:text-slate-600'}`}
              >
                <Grid size={iconSizeToken.medium} />
              </button>
            </div>

            <button
              onClick={() => setShowNewModal(true)}
              className="bg-slate-900 dark:bg-blue-600 text-white px-3 @md:px-4 py-1 @md:py-1.5 rounded-lg text-caption @md:text-body-sm font-medium flex items-center gap-1 @md:gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all"
            >
              <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">新建指标</span>
            </button>
          </div>
        </div>

        {/* 内容区域 - 高度自适应，最大不超过容器 */}
        <div className="flex-1 min-h-0 p-4 @md:p-6 pb-6 @md:pb-8 overflow-auto custom-scrollbar">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-16">
              <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
              <div className="text-slate-400 text-caption mt-3">加载中...</div>
            </div>
          ) : viewMode === 'CARD' ? (
            <>
              <div className="grid grid-cols-1 @md:grid-cols-2 @lg:grid-cols-3 gap-3 @md:gap-4">
                {filteredMetrics.map((m) => (
                  <MetricCard key={m.code} metric={m} onClick={() => onOpenDrawer(m)} onDelete={handleDelete} onEdit={setEditingMetric} />
                ))}
                {filteredMetrics.length === 0 && (
                  <div className="col-span-full flex flex-col items-center justify-center py-12 @md:py-16 text-slate-400 bg-white dark:bg-slate-900 border border-dashed border-slate-200 dark:border-slate-700 rounded-xl @md:rounded-2xl">
                    <Box size={iconSizeToken.huge} className="opacity-20 mb-3" />
                    <p className="text-caption @md:text-body-sm font-medium">未找到匹配的指标</p>
                  </div>
                )}
              </div>
              {/* 哨兵元素 + 加载更多 */}
              <div ref={sentinelRef} className="h-4" />
              {loadingMore && (
                <div className="flex justify-center py-4">
                  <Loader2 className="w-5 h-5 animate-spin text-blue-500" />
                </div>
              )}
            </>
          ) : (
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-hidden">
              <table className="w-full text-left border-collapse table-fixed min-w-table-wide">
                <thead className="bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-400 font-semibold text-micro uppercase tracking-wider">
                  <tr>
                    <th className="px-4 py-3 w-52">指标名称 / 编码</th>
                    <th className="px-3 py-3 w-16 text-center">类型</th>
                    <th className="px-3 py-3 w-32 text-center">数据类型</th>
                    <th className="px-3 py-3 w-16 text-center">单位</th>
                    <th className="px-3 py-3">描述</th>
                    <th className="px-3 py-3 w-14 text-center">版本</th>
                    <th className="px-3 py-3 w-20">创建人</th>
                    <th className="px-3 py-3 w-40">创建时间</th>
                    <th className="px-3 py-3 w-16 text-center">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredMetrics.map((m) => (
                    <MetricRow key={m.code} metric={m} onClick={() => onOpenDrawer(m)} onDelete={handleDelete} onEdit={setEditingMetric} />
                  ))}
                  {filteredMetrics.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-4 py-12 @md:py-16 text-center text-slate-400 text-caption @md:text-body-sm">
                        未找到匹配的指标
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
              {/* 哨兵元素 + 加载更多 */}
              <div ref={sentinelRef} className="h-1" />
              {loadingMore && (
                <div className="flex justify-center py-4 border-t border-slate-100 dark:border-slate-800">
                  <Loader2 className="w-5 h-5 animate-spin text-blue-500" />
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 右侧语义组件库 */}
      <ComponentLibrarySidebar />

      {/* 新建指标弹窗 */}
      <MetricFormModal
        isOpen={showNewModal}
        onClose={() => setShowNewModal(false)}
        onSave={handleSaveNewMetric}
        saving={saving}
      />

      {/* 编辑指标弹窗 */}
      <MetricFormModal
        isOpen={!!editingMetric}
        onClose={() => setEditingMetric(null)}
        onSave={handleSaveEditMetric}
        saving={saving}
        editMetric={editingMetric}
      />
    </div>
  )
}
