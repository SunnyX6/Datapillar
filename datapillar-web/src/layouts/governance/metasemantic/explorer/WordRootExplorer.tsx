import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Plus, BookA, Pencil, Trash2, Check, X, Loader2 } from 'lucide-react'
import { Badge } from '../components'
import type { WordRoot } from '../types'
import { iconSizeToken, tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { useSearchStore, useSemanticStatsStore } from '@/stores'
import { useInfiniteScroll } from '@/hooks'
import { fetchWordRoots, createWordRoot, deleteWordRoot, updateWordRoot, type CreateWordRootRequest, type UpdateWordRootRequest } from '@/services/oneMetaSemanticService'
import {
  DataTypeSelector,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  parseDataTypeString,
  buildDataTypeString,
  type DataTypeValue
} from '@/components/ui'
import { formatTime } from '@/lib/utils'

/** 每页加载数量 */
const PAGE_SIZE = 20

interface WordRootExplorerProps {
  onBack: () => void
  onOpenDrawer: (root: WordRoot) => void
}

/** 新增行的表单状态 */
interface NewRowForm {
  code: string
  name: string
  dataTypeValue: DataTypeValue
  comment: string
}

const emptyForm: NewRowForm = {
  code: '',
  name: '',
  dataTypeValue: { type: 'STRING' },
  comment: ''
}

/** 词根行组件 */
function WordRootRow({
  root,
  isEditing,
  onStartEdit,
  onEndEdit,
  onClick,
  onDelete,
  onUpdate
}: {
  root: WordRoot
  isEditing: boolean
  onStartEdit: () => void
  onEndEdit: () => void
  onClick: () => void
  onDelete: (code: string) => void
  onUpdate: (code: string, updated: WordRoot) => void
}) {
  const [deleting, setDeleting] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editForm, setEditForm] = useState(() => ({
    name: root.name,
    dataTypeValue: parseDataTypeString(root.dataType),
    comment: root.comment || ''
  }))

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (deleting) return
    setDeleting(true)
    try {
      await deleteWordRoot(root.code)
      onDelete(root.code)
    } catch {
      setDeleting(false)
    }
  }

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    onStartEdit()
  }

  const handleCancel = (e: React.MouseEvent) => {
    e.stopPropagation()
    onEndEdit()
    setEditForm({
      name: root.name,
      dataTypeValue: parseDataTypeString(root.dataType),
      comment: root.comment || ''
    })
  }

  const handleSave = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (saving || !editForm.name.trim()) return
    setSaving(true)
    try {
      const request: UpdateWordRootRequest = {
        name: editForm.name.trim(),
        dataType: buildDataTypeString(editForm.dataTypeValue),
        comment: editForm.comment.trim() || undefined
      }
      const updated = await updateWordRoot(root.code, request)
      onUpdate(root.code, updated)
      onEndEdit()
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  if (isEditing) {
    return (
      <tr className="bg-blue-50/50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800">
        <td className="px-4 py-2">
          <div className="space-y-1">
            <input
              type="text"
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
              onClick={(e) => e.stopPropagation()}
              placeholder="词根名称"
              className="w-full px-2 py-1 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="text-micro font-mono text-slate-400 uppercase px-1">{root.code}</div>
          </div>
        </td>
        <td className="px-4 py-2">
          <div className="flex justify-center" onClick={(e) => e.stopPropagation()}>
            <DataTypeSelector
              value={editForm.dataTypeValue}
              onChange={(value) => setEditForm({ ...editForm, dataTypeValue: value })}
            />
          </div>
        </td>
        <td className="px-4 py-2">
          <input
            type="text"
            value={editForm.comment}
            onChange={(e) => setEditForm({ ...editForm, comment: e.target.value })}
            onClick={(e) => e.stopPropagation()}
            placeholder="描述"
            className="w-full px-2 py-1.5 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </td>
        <td className="px-4 py-2">
          <span className="text-caption text-slate-400">{root.audit?.creator || '-'}</span>
        </td>
        <td className="px-4 py-2">
          <span className="text-caption text-slate-400">{formatTime(root.audit?.createTime)}</span>
        </td>
        <td className="px-4 py-2">
          <div className="flex items-center justify-center gap-1">
            <button
              onClick={handleSave}
              disabled={saving || !editForm.name.trim()}
              className="p-1.5 text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 rounded-lg transition-colors disabled:opacity-50"
              title="保存"
            >
              {saving ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Check size={iconSizeToken.small} />}
            </button>
            <button
              onClick={handleCancel}
              disabled={saving}
              className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-50"
              title="取消"
            >
              <X size={iconSizeToken.small} />
            </button>
          </div>
        </td>
      </tr>
    )
  }

  return (
    <tr
      onClick={onClick}
      className="group hover:bg-blue-50/40 dark:hover:bg-blue-900/20 transition-colors cursor-pointer border-b border-slate-100 dark:border-slate-800 last:border-0"
    >
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400">
            <BookA size={iconSizeToken.small} />
          </div>
          <div>
            <div className="font-medium text-slate-800 dark:text-slate-100 text-body-sm group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
              {root.name}
            </div>
            <div className="text-micro font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight">{root.code}</div>
          </div>
        </div>
      </td>
      <td className="px-4 py-3 text-center">
        <span className="font-mono text-micro text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-900/30 px-2 py-0.5 rounded border border-cyan-100 dark:border-cyan-800">
          {root.dataType || '-'}
        </span>
      </td>
      <td className="px-4 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400 line-clamp-1">{root.comment || '-'}</div>
      </td>
      <td className="px-4 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400">{root.audit?.creator || '-'}</div>
      </td>
      <td className="px-4 py-3">
        <div className="text-caption text-slate-500 dark:text-slate-400">{formatTime(root.audit?.createTime)}</div>
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

/** 新增行组件 */
function NewWordRootRow({
  form,
  onChange,
  onSave,
  onCancel,
  saving
}: {
  form: NewRowForm
  onChange: (form: NewRowForm) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
}) {
  const isValid = form.code.trim() && form.name.trim()

  return (
    <tr className="bg-blue-50/50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800">
      <td className="px-4 py-2">
        <div className="space-y-1">
          <input
            type="text"
            value={form.name}
            onChange={(e) => onChange({ ...form, name: e.target.value })}
            placeholder="词根名称"
            className="w-full px-2 py-1 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <input
            type="text"
            value={form.code}
            onChange={(e) => onChange({ ...form, code: e.target.value.toUpperCase() })}
            placeholder="编码 (CODE)"
            className="w-full px-2 py-1 text-micro font-mono uppercase border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </td>
      <td className="px-4 py-2 text-center">
        <div className="flex justify-center">
          <DataTypeSelector
            value={form.dataTypeValue}
            onChange={(value) => onChange({ ...form, dataTypeValue: value })}
          />
        </div>
      </td>
      <td className="px-4 py-2">
        <input
          type="text"
          value={form.comment}
          onChange={(e) => onChange({ ...form, comment: e.target.value })}
          placeholder="描述（可选）"
          className="w-full px-2 py-1.5 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </td>
      <td className="px-4 py-2">
        <span className="text-caption text-slate-400">-</span>
      </td>
      <td className="px-4 py-2">
        <span className="text-caption text-slate-400">-</span>
      </td>
      <td className="px-4 py-2">
        <div className="flex items-center justify-center gap-1">
          <button
            onClick={onSave}
            disabled={!isValid || saving}
            className="p-1.5 text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            title="保存"
          >
            {saving ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Check size={iconSizeToken.small} />}
          </button>
          <button
            onClick={onCancel}
            disabled={saving}
            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-50"
            title="取消"
          >
            <X size={iconSizeToken.small} />
          </button>
        </div>
      </td>
    </tr>
  )
}

export function WordRootExplorer({ onBack, onOpenDrawer }: WordRootExplorerProps) {
  const searchTerm = useSearchStore((state) => state.searchTerm)
  const setWordRootsTotal = useSemanticStatsStore((state) => state.setWordRootsTotal)

  // 数据状态
  const [wordRoots, setWordRoots] = useState<WordRoot[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  // 新增行状态
  const [showNewRow, setShowNewRow] = useState(false)
  const [newRowForm, setNewRowForm] = useState<NewRowForm>(emptyForm)
  const [saving, setSaving] = useState(false)

  // 当前编辑行的 code（同时只能编辑一行）
  const [editingCode, setEditingCode] = useState<string | null>(null)

  // 是否还有更多数据
  const hasMore = wordRoots.length < total

  // 加载首页数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchWordRoots(0, PAGE_SIZE)
      setWordRoots(result.items)
      setTotal(result.total)
      setWordRootsTotal(result.total)
    } catch {
      // 加载失败时保持空列表
    } finally {
      setLoading(false)
    }
  }, [setWordRootsTotal])

  // 加载更多数据
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const result = await fetchWordRoots(wordRoots.length, PAGE_SIZE)
      setWordRoots((prev) => [...prev, ...result.items])
      setTotal(result.total)
    } catch {
      // 加载失败
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, wordRoots.length])

  // 无限滚动
  const { sentinelRef } = useInfiniteScroll({
    hasMore,
    loading: loadingMore,
    onLoadMore: loadMore
  })

  useEffect(() => {
    loadData()
  }, [loadData])

  // 过滤词根
  const filteredRoots = wordRoots.filter(
    (r) =>
      r.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      r.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (r.comment && r.comment.toLowerCase().includes(searchTerm.toLowerCase()))
  )

  // 保存新词根
  const handleSaveNewRow = async () => {
    if (saving) return
    setSaving(true)
    try {
      const request: CreateWordRootRequest = {
        code: newRowForm.code.trim().toUpperCase(),
        name: newRowForm.name.trim(),
        dataType: buildDataTypeString(newRowForm.dataTypeValue),
        comment: newRowForm.comment.trim() || undefined
      }
      const newRoot = await createWordRoot(request)
      setWordRoots((prev) => [...prev, newRoot])
      setTotal((prev) => prev + 1)
      setShowNewRow(false)
      setNewRowForm(emptyForm)
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  // 取消新增
  const handleCancelNewRow = () => {
    setShowNewRow(false)
    setNewRowForm(emptyForm)
  }

  // 删除词根
  const handleDelete = (code: string) => {
    setWordRoots((prev) => prev.filter((r) => r.code !== code))
    setTotal((prev) => prev - 1)
  }

  // 更新词根
  const handleUpdate = (code: string, updated: WordRoot) => {
    setWordRoots((prev) => prev.map((r) => (r.code === code ? updated : r)))
  }

  // 显示新增行
  const handleShowNewRow = () => {
    setEditingCode(null) // 取消当前编辑行
    setShowNewRow(true)
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50/30 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      <div className="h-12 @md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 @md:px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
        <div className="flex items-center gap-2 @md:gap-3">
          <button onClick={onBack} className="p-1 @md:p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all">
            <ArrowLeft size={iconSizeToken.large} />
          </button>
          <div className="flex items-center gap-2">
            <h2 className="text-body-sm @md:text-subtitle font-semibold text-slate-800 dark:text-slate-100">规范词根</h2>
            <Badge variant="blue">
              {filteredRoots.length} / {total}
            </Badge>
          </div>
        </div>
        <button
          onClick={handleShowNewRow}
          disabled={showNewRow}
          className="bg-slate-900 dark:bg-blue-600 text-white px-3 @md:px-4 py-1 @md:py-1.5 rounded-lg text-caption @md:text-body-sm font-medium flex items-center gap-1 @md:gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">新增词根</span>
        </button>
      </div>

      <div className="flex-1 overflow-auto p-4 @md:p-6 custom-scrollbar">
        <Table
          footer={
            <>
              {/* 哨兵元素 + 加载更多 */}
              <div ref={sentinelRef} className="h-1" />
              {loadingMore && (
                <div className="flex justify-center py-4 border-t border-slate-100 dark:border-slate-800">
                  <Loader2 className="w-5 h-5 animate-spin text-blue-500" />
                </div>
              )}
            </>
          }
        >
          <TableHeader>
            <TableRow>
              <TableHead className={tableColumnWidthClassMap['5xl']}>词根名称 / 编码</TableHead>
              <TableHead className={`${tableColumnWidthClassMap['4xl']} text-center`}>数据类型</TableHead>
              <TableHead>描述</TableHead>
              <TableHead className={tableColumnWidthClassMap.lg}>创建人</TableHead>
              <TableHead className={tableColumnWidthClassMap['2xl']}>创建时间</TableHead>
              <TableHead className={`${tableColumnWidthClassMap.lg} text-center`}>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={6} className="py-12 text-center">
                  <Loader2 className="w-6 h-6 animate-spin text-blue-500 mx-auto" />
                  <div className="text-slate-400 text-caption mt-2">加载中...</div>
                </TableCell>
              </TableRow>
            ) : (
              <>
                {filteredRoots.map((root) => (
                  <WordRootRow
                    key={root.code}
                    root={root}
                    isEditing={editingCode === root.code}
                    onStartEdit={() => setEditingCode(root.code)}
                    onEndEdit={() => setEditingCode(null)}
                    onClick={() => onOpenDrawer(root)}
                    onDelete={handleDelete}
                    onUpdate={handleUpdate}
                  />
                ))}
                {showNewRow && (
                  <NewWordRootRow
                    form={newRowForm}
                    onChange={setNewRowForm}
                    onSave={handleSaveNewRow}
                    onCancel={handleCancelNewRow}
                    saving={saving}
                  />
                )}
                {filteredRoots.length === 0 && !showNewRow && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-12 @md:py-16 text-center text-slate-400 text-caption @md:text-body-sm">
                      未找到匹配的词根
                    </TableCell>
                  </TableRow>
                )}
              </>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
