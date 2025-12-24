import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Plus, BookA, Pencil, Trash2, Check, X, Loader2 } from 'lucide-react'
import { Badge } from '../components'
import type { WordRoot } from '../types'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { useSearchStore } from '@/stores'
import { useIsZhCN } from '@/stores/i18nStore'
import { fetchWordRoots, createWordRoot, deleteWordRoot, updateWordRoot, type CreateWordRootRequest, type UpdateWordRootRequest } from '@/services/oneMetaService'

interface WordRootExplorerProps {
  onBack: () => void
  onOpenDrawer: (root: WordRoot) => void
}

/** 新增行的表单状态 */
interface NewRowForm {
  code: string
  nameCn: string
  nameEn: string
  dataType: string
  comment: string
}

const emptyForm: NewRowForm = {
  code: '',
  nameCn: '',
  nameEn: '',
  dataType: 'STRING',
  comment: ''
}

/** 数据类型选项 */
const DATA_TYPES = ['STRING', 'NUMBER', 'DECIMAL', 'DATE', 'DATETIME', 'BOOLEAN']

/** 词根行组件 */
function WordRootRow({
  root,
  onClick,
  onDelete,
  onUpdate,
  isZhCN
}: {
  root: WordRoot
  onClick: () => void
  onDelete: (code: string) => void
  onUpdate: (code: string, updated: WordRoot) => void
  isZhCN: boolean
}) {
  const [deleting, setDeleting] = useState(false)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editForm, setEditForm] = useState({
    nameCn: root.nameCn,
    nameEn: root.nameEn,
    dataType: root.dataType || 'STRING',
    comment: root.comment || ''
  })

  // 当前语言下的编辑值
  const currentEditName = isZhCN ? editForm.nameCn : editForm.nameEn

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
    setEditing(true)
  }

  const handleCancel = (e: React.MouseEvent) => {
    e.stopPropagation()
    setEditing(false)
    setEditForm({
      nameCn: root.nameCn,
      nameEn: root.nameEn,
      dataType: root.dataType || 'STRING',
      comment: root.comment || ''
    })
  }

  const handleNameChange = (value: string) => {
    if (isZhCN) {
      setEditForm({ ...editForm, nameCn: value })
    } else {
      setEditForm({ ...editForm, nameEn: value })
    }
  }

  const handleSave = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (saving || !currentEditName.trim()) return
    setSaving(true)
    try {
      const request: UpdateWordRootRequest = {
        nameCn: editForm.nameCn.trim() || root.nameCn,
        nameEn: editForm.nameEn.trim() || root.nameEn,
        dataType: editForm.dataType,
        comment: editForm.comment.trim() || undefined
      }
      const updated = await updateWordRoot(root.code, request)
      onUpdate(root.code, updated)
      setEditing(false)
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  const displayName = isZhCN ? root.nameCn : root.nameEn

  if (editing) {
    return (
      <tr className="bg-blue-50/50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800">
        <td className="px-4 py-2">
          <div className="space-y-1">
            <input
              type="text"
              value={currentEditName}
              onChange={(e) => handleNameChange(e.target.value)}
              onClick={(e) => e.stopPropagation()}
              placeholder={isZhCN ? '中文名' : 'English Name'}
              className="w-full px-2 py-1 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="text-micro font-mono text-slate-400 uppercase px-1">{root.code}</div>
          </div>
        </td>
        <td className="px-4 py-2 text-center">
          <select
            value={editForm.dataType}
            onChange={(e) => setEditForm({ ...editForm, dataType: e.target.value })}
            onClick={(e) => e.stopPropagation()}
            className="px-2 py-1.5 text-body-sm font-mono border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {DATA_TYPES.map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
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
          <div className="flex items-center justify-center gap-1">
            <button
              onClick={handleSave}
              disabled={saving || !currentEditName.trim()}
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
              {displayName}
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
  saving,
  isZhCN
}: {
  form: NewRowForm
  onChange: (form: NewRowForm) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
  isZhCN: boolean
}) {
  // 当前语言下的名称值
  const currentName = isZhCN ? form.nameCn : form.nameEn

  // 验证：编码必填，当前语言名称必填
  const isValid = form.code.trim() && currentName.trim()

  const handleNameChange = (value: string) => {
    if (isZhCN) {
      onChange({ ...form, nameCn: value })
    } else {
      onChange({ ...form, nameEn: value })
    }
  }

  return (
    <tr className="bg-blue-50/50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800">
      <td className="px-4 py-2">
        <div className="space-y-1">
          <input
            type="text"
            value={currentName}
            onChange={(e) => handleNameChange(e.target.value)}
            placeholder={isZhCN ? '中文名' : 'English Name'}
            className="w-full px-2 py-1 text-body-sm border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <input
            type="text"
            value={form.code}
            onChange={(e) => onChange({ ...form, code: e.target.value })}
            placeholder="编码 (CODE)"
            className="w-full px-2 py-1 text-micro font-mono uppercase border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </td>
      <td className="px-4 py-2 text-center">
        <select
          value={form.dataType}
          onChange={(e) => onChange({ ...form, dataType: e.target.value })}
          className="px-2 py-1.5 text-body-sm font-mono border border-slate-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {DATA_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
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
  const isZhCN = useIsZhCN()

  // 数据状态
  const [wordRoots, setWordRoots] = useState<WordRoot[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)

  // 新增行状态
  const [showNewRow, setShowNewRow] = useState(false)
  const [newRowForm, setNewRowForm] = useState<NewRowForm>(emptyForm)
  const [saving, setSaving] = useState(false)

  // 加载数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchWordRoots(0, 100)
      setWordRoots(result.items)
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

  // 过滤词根
  const filteredRoots = wordRoots.filter(
    (r) =>
      r.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
      r.nameCn.toLowerCase().includes(searchTerm.toLowerCase()) ||
      r.nameEn.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (r.comment && r.comment.toLowerCase().includes(searchTerm.toLowerCase()))
  )

  // 保存新词根
  const handleSaveNewRow = async () => {
    if (saving) return
    setSaving(true)
    try {
      const request: CreateWordRootRequest = {
        code: newRowForm.code.trim().toUpperCase(),
        nameCn: newRowForm.nameCn.trim(),
        nameEn: newRowForm.nameEn.trim(),
        dataType: newRowForm.dataType,
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
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-x-auto">
          <table className="w-full text-left border-collapse table-fixed min-w-table-wide">
            <thead className="bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-400 font-semibold text-micro uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3 w-56">词根名称 / 编码</th>
                <th className="px-4 py-3 w-28 text-center">数据类型</th>
                <th className="px-4 py-3">描述</th>
                <th className="px-4 py-3 w-24">创建人</th>
                <th className="px-4 py-3 w-24 text-center">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center">
                    <Loader2 className="w-6 h-6 animate-spin text-blue-500 mx-auto" />
                    <div className="text-slate-400 text-caption mt-2">加载中...</div>
                  </td>
                </tr>
              ) : (
                <>
                  {filteredRoots.map((root) => (
                    <WordRootRow key={root.code} root={root} onClick={() => onOpenDrawer(root)} onDelete={handleDelete} onUpdate={handleUpdate} isZhCN={isZhCN} />
                  ))}
                  {showNewRow && (
                    <NewWordRootRow
                      form={newRowForm}
                      onChange={setNewRowForm}
                      onSave={handleSaveNewRow}
                      onCancel={handleCancelNewRow}
                      saving={saving}
                      isZhCN={isZhCN}
                    />
                  )}
                  {filteredRoots.length === 0 && !showNewRow && (
                    <tr>
                      <td colSpan={5} className="px-4 py-12 @md:py-16 text-center text-slate-400 text-caption @md:text-body-sm">
                        未找到匹配的词根
                      </td>
                    </tr>
                  )}
                </>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
