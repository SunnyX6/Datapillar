import { useState, useEffect, useCallback } from 'react'
import { Target, ArrowLeft, Plus, List, Grid, Box, Trash2, Loader2, Pencil } from 'lucide-react'
import { Badge } from '../components'
import type { Metric, ViewMode } from '../types'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { useSearchStore } from '@/stores'
import { fetchMetrics, deleteMetric, registerMetric, updateMetric, alterMetricVersion } from '@/services/oneMetaService'
import { MetricFormModal, type MetricFormData } from './form/MetricForm'
import { ComponentLibrarySidebar } from './ComponentLibrarySidebar'

/** 指标类型标签映射 */
const TYPE_LABELS: Record<string, { label: string; variant: 'blue' | 'purple' | 'amber' }> = {
  ATOMIC: { label: '原子', variant: 'blue' },
  DERIVED: { label: '派生', variant: 'purple' },
  COMPOSITE: { label: '复合', variant: 'amber' }
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

  const typeInfo = TYPE_LABELS[metric.type] || { label: metric.type, variant: 'blue' as const }

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
        <Badge variant={typeInfo.variant}>{typeInfo.label}</Badge>
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

  const typeInfo = TYPE_LABELS[metric.type] || { label: metric.type, variant: 'blue' as const }

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
          <div>
            <div className="font-medium text-slate-800 dark:text-slate-100 text-body-sm group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
              {metric.name}
            </div>
            <div className="text-micro font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight">{metric.code}</div>
          </div>
        </div>
      </td>
      <td className="px-4 py-3 text-center">
        <Badge variant={typeInfo.variant}>{typeInfo.label}</Badge>
      </td>
      <td className="px-4 py-3 text-center">
        <span className="font-mono text-micro text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-900/30 px-2 py-0.5 rounded border border-cyan-100 dark:border-cyan-800">
          {metric.dataType || '-'}
        </span>
      </td>
      <td className="px-4 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400 line-clamp-1">{metric.comment || '-'}</div>
      </td>
      <td className="px-4 py-3 text-center">
        <span className="text-micro font-mono text-slate-600 dark:text-slate-400">v{metric.currentVersion}</span>
      </td>
      <td className="px-4 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400">{metric.audit?.creator || '-'}</div>
      </td>
      <td className="px-4 py-3">
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
}

export function MetricExplorer({ onBack, onOpenDrawer }: MetricExplorerProps) {
  const [viewMode, setViewMode] = useState<ViewMode>('LIST')
  const searchTerm = useSearchStore((state) => state.searchTerm)

  // 数据状态
  const [metrics, setMetrics] = useState<Metric[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)

  // 新建弹窗状态
  const [showNewModal, setShowNewModal] = useState(false)
  const [saving, setSaving] = useState(false)

  // 编辑弹窗状态
  const [editingMetric, setEditingMetric] = useState<Metric | null>(null)

  // 加载数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchMetrics(0, 100)
      setMetrics(result.items)
      setTotal(result.total)
    } catch {
      // 加载失败时保持空列表
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

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

  // 更新指标（编辑后刷新列表中的数据）
  const handleUpdate = (code: string, updated: Metric) => {
    setMetrics((prev) => prev.map((m) => (m.code === code ? updated : m)))
  }

  // 保存新指标
  const handleSaveNewMetric = async (form: MetricFormData) => {
    if (saving) return
    setSaving(true)
    try {
      const newMetric = await registerMetric({
        name: form.name.trim(),
        code: form.code.trim().toUpperCase(),
        type: form.type,
        dataType: form.dataType,
        comment: form.comment.trim() || undefined,
        unit: form.unit.trim() || undefined,
        // 公式字段（包含聚合逻辑和计算表达式）
        calculationFormula: form.formula.trim() || undefined,
        // 原子指标数据源配置
        refCatalogName: form.type === 'ATOMIC' ? form.refCatalogName || undefined : undefined,
        refSchemaName: form.type === 'ATOMIC' ? form.refSchemaName || undefined : undefined,
        refTableName: form.type === 'ATOMIC' ? form.refTableName || undefined : undefined,
        refColumnName: form.type === 'ATOMIC' ? form.refColumnName || undefined : undefined
      })
      setMetrics((prev) => [...prev, newMetric])
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
      // 更新指标基本信息（name, comment）
      const updated = await updateMetric(editingMetric.code, {
        name: form.name.trim(),
        comment: form.comment.trim() || undefined
      })
      // 更新指标版本（公式、单位等）
      await alterMetricVersion(editingMetric.code, editingMetric.currentVersion, {
        comment: form.comment.trim() || undefined,
        unit: form.unit.trim() || undefined,
        calculationFormula: form.formula.trim() || undefined
      })
      handleUpdate(editingMetric.code, updated)
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

        <div className="flex-1 overflow-auto p-4 @md:p-6 custom-scrollbar">
          <div>
            {loading ? (
              <div className="flex flex-col items-center justify-center py-16">
                <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
                <div className="text-slate-400 text-caption mt-3">加载中...</div>
              </div>
            ) : viewMode === 'CARD' ? (
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
            ) : (
              <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-x-auto">
                <table className="w-full text-left border-collapse table-fixed min-w-table-wide">
                  <thead className="bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-400 font-semibold text-micro uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3 w-56">指标名称 / 编码</th>
                      <th className="px-4 py-3 w-20 text-center">类型</th>
                      <th className="px-4 py-3 w-24 text-center">数据类型</th>
                      <th className="px-4 py-3">描述</th>
                      <th className="px-4 py-3 w-16 text-center">版本</th>
                      <th className="px-4 py-3 w-24">创建人</th>
                      <th className="px-4 py-3 w-16 text-center">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredMetrics.map((m) => (
                      <MetricRow key={m.code} metric={m} onClick={() => onOpenDrawer(m)} onDelete={handleDelete} onEdit={setEditingMetric} />
                    ))}
                    {filteredMetrics.length === 0 && (
                      <tr>
                        <td colSpan={7} className="px-4 py-12 @md:py-16 text-center text-slate-400 text-caption @md:text-body-sm">
                          未找到匹配的指标
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </div>
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
