import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Plus, Users, Trash2, Loader2, Pencil, Clock } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { Badge } from '../components'
import { useSearchStore } from '@/stores'
import { useInfiniteScroll } from '@/hooks'
import { Modal, ModalCancelButton, ModalPrimaryButton, Select } from '@/components/ui'
import {
  fetchValueDomains,
  createValueDomain,
  deleteValueDomain,
  updateValueDomain,
  type ValueDomainDTO,
  type ValueDomainType,
  type CreateValueDomainRequest
} from '@/services/oneMetaSemanticService'
import { ValueDomainFormModal, type ValueDomainFormData } from './form/ValueDomainForm'
import { formatTime } from '@/lib/utils'
import { DataTypeSelector, parseDataTypeString, buildDataTypeString, type DataTypeValue } from '@/components/ui'

/** 每页加载数量 */
const PAGE_SIZE = 20

/** 类型标签配置 */
const TYPE_CONFIG: Record<ValueDomainType, { label: string; color: string }> = {
  ENUM: { label: 'ENUM', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 border-blue-200 dark:border-blue-800' },
  RANGE: { label: 'RANGE', color: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' },
  REGEX: { label: 'REGEX', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 border-purple-200 dark:border-purple-800' }
}

/** 级别标签配置 */
const LEVEL_CONFIG: Record<string, { label: string; color: string }> = {
  BUILTIN: { label: '内置', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 border-amber-200 dark:border-amber-800' },
  BUSINESS: { label: '业务', color: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400 border-slate-200 dark:border-slate-700' }
}

interface ValueDomainExplorerProps {
  onBack: () => void
}

/** 值域卡片组件 */
function ValueDomainCard({
  domain,
  onDelete,
  onEdit
}: {
  domain: ValueDomainDTO
  onDelete: (domainCode: string) => void
  onEdit: (domain: ValueDomainDTO) => void
}) {
  const normalizedType = (domain.domainType?.toUpperCase() || 'ENUM') as ValueDomainType
  const normalizedLevel = domain.domainLevel?.toUpperCase() || 'BUSINESS'
  const typeConfig = TYPE_CONFIG[normalizedType] || TYPE_CONFIG.ENUM
  const levelConfig = LEVEL_CONFIG[normalizedLevel] || LEVEL_CONFIG.BUSINESS
  const isBuiltin = normalizedLevel === 'BUILTIN'
  const [deleting, setDeleting] = useState(false)

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (deleting || isBuiltin) return
    setDeleting(true)
    try {
      // 直接删除整个值域
      await deleteValueDomain(domain.domainCode)
      onDelete(domain.domainCode)
    } catch {
      setDeleting(false)
    }
  }

  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    onEdit(domain)
  }

  return (
    <div className="group bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-4 hover:shadow-md hover:border-slate-300 dark:hover:border-slate-700 transition-all duration-200">
      {/* 头部：类型标签 + 数据类型标签 + 级别标签 + 操作按钮 */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-1.5">
          <span className={`px-2 py-0.5 text-micro font-semibold uppercase tracking-wider rounded-md border ${typeConfig.color}`}>
            {normalizedType}
          </span>
          {domain.dataType && (
            <span className="px-2 py-0.5 text-micro font-mono text-cyan-600 dark:text-cyan-400 bg-cyan-50 dark:bg-cyan-900/30 rounded-md border border-cyan-200 dark:border-cyan-800">
              {domain.dataType}
            </span>
          )}
          <span className={`px-2 py-0.5 text-micro font-medium rounded-md border ${levelConfig.color}`}>
            {levelConfig.label}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleEdit}
            className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
            title="编辑"
          >
            <Pencil size={iconSizeToken.small} />
          </button>
          {!isBuiltin && (
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-50"
              title="删除"
            >
              {deleting ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Trash2 size={iconSizeToken.small} />}
            </button>
          )}
        </div>
      </div>

      {/* 名称和编码 */}
      <h3 className="font-semibold text-slate-800 dark:text-slate-100 text-body-sm mb-0.5">{domain.domainName}</h3>
      <p className="text-micro font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight mb-3">{domain.domainCode}</p>

      {/* 枚举值预览区 */}
      <div className="bg-slate-900 dark:bg-slate-800 rounded-lg px-3 py-2.5 mb-3 max-h-32 overflow-y-auto custom-scrollbar">
        {domain.items?.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {domain.items.map((item) => (
              <span
                key={item.value}
                className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-800 dark:bg-slate-700 rounded text-micro"
                title={item.label || item.value}
              >
                <code className="text-emerald-400 font-mono">{item.value}</code>
                {item.label && <span className="text-slate-500">({item.label})</span>}
              </span>
            ))}
          </div>
        ) : (
          <span className="text-slate-500 text-micro">无枚举值</span>
        )}
      </div>

      {/* 底部：创建人 + 创建时间 + 枚举数量 */}
      <div className="flex items-center justify-between pt-3 border-t border-slate-100 dark:border-slate-800">
        <div className="flex items-center gap-3 text-micro text-slate-500 dark:text-slate-400">
          <div className="flex items-center gap-1.5">
            <Users size={iconSizeToken.small} />
            <span>{domain.audit?.creator || '-'}</span>
          </div>
          <span className="text-slate-300 dark:text-slate-600">|</span>
          <div className="flex items-center gap-1">
            <Clock size={iconSizeToken.small} />
            <span>{formatTime(domain.audit?.createTime)}</span>
          </div>
        </div>
        <span className="text-micro text-slate-400">{domain.items?.length || 0} 个枚举值</span>
      </div>
    </div>
  )
}

export function ValueDomainExplorer({ onBack }: ValueDomainExplorerProps) {
  const searchTerm = useSearchStore((state) => state.searchTerm)

  // 数据状态
  const [valueDomains, setValueDomains] = useState<ValueDomainDTO[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  // 模态框状态
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingDomain, setEditingDomain] = useState<ValueDomainDTO | null>(null)
  const [saving, setSaving] = useState(false)

  // 是否还有更多数据
  const hasMore = valueDomains.length < total

  // 加载首页数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchValueDomains(0, PAGE_SIZE)
      setValueDomains(result.items)
      setTotal(result.total)
    } catch {
      // 加载失败时保持空列表
    } finally {
      setLoading(false)
    }
  }, [])

  // 加载更多数据
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const result = await fetchValueDomains(valueDomains.length, PAGE_SIZE)
      setValueDomains((prev) => [...prev, ...result.items])
      setTotal(result.total)
    } catch {
      // 加载失败
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, valueDomains.length])

  // 无限滚动
  const { sentinelRef } = useInfiniteScroll({
    hasMore,
    loading: loadingMore,
    onLoadMore: loadMore
  })

  useEffect(() => {
    loadData()
  }, [loadData])

  // 过滤并排序值域：BUILTIN 在前，然后按名称排序
  const filteredDomains = valueDomains
    .filter(
      (d) =>
        d.domainCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
        d.domainName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        d.items?.some((item) => item.value.toLowerCase().includes(searchTerm.toLowerCase()))
    )
    .sort((a, b) => {
      const levelA = a.domainLevel?.toUpperCase() || 'BUSINESS'
      const levelB = b.domainLevel?.toUpperCase() || 'BUSINESS'
      // BUILTIN 排在前面
      if (levelA === 'BUILTIN' && levelB !== 'BUILTIN') return -1
      if (levelA !== 'BUILTIN' && levelB === 'BUILTIN') return 1
      // 同级别按名称排序
      return a.domainName.localeCompare(b.domainName, 'zh-CN')
    })

  // 保存新值域
  const handleSave = async (formData: ValueDomainFormData) => {
    if (saving) return
    setSaving(true)
    try {
      let items: { value: string; label?: string }[] = []

      if (formData.domainType === 'ENUM') {
        // ENUM: 批量创建多个枚举值
        items = formData.enumItems
          .filter((item) => item.key.trim())
          .map((item) => ({
            value: item.key.trim(),
            label: item.value.trim() || undefined
          }))
      } else {
        // RANGE/REGEX: 单条创建
        items = [{ value: formData.itemValue.trim() }]
      }

      const request: CreateValueDomainRequest = {
        domainCode: formData.domainCode.trim(),
        domainName: formData.domainName.trim(),
        domainType: formData.domainType,
        domainLevel: formData.domainLevel,
        items,
        comment: formData.comment.trim() || undefined,
        dataType: buildDataTypeString(formData.dataType)
      }

      const newDomain = await createValueDomain(request)
      setValueDomains((prev) => [...prev, newDomain])
      setTotal((prev) => prev + 1)
      setShowCreateModal(false)
    } catch {
      // 保存失败
    } finally {
      setSaving(false)
    }
  }

  // 删除值域
  const handleDelete = (domainCode: string) => {
    setValueDomains((prev) => prev.filter((d) => d.domainCode !== domainCode))
    setTotal((prev) => prev - 1)
  }

  // 更新值域后重新加载数据
  const handleDomainUpdated = () => {
    loadData()
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50/40 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      {/* 顶部导航栏 */}
      <div className="h-12 @md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 @md:px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
        <div className="flex items-center gap-2 @md:gap-3">
          <button
            onClick={onBack}
            className="p-1 @md:p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all"
          >
            <ArrowLeft size={iconSizeToken.large} />
          </button>
          <div className="flex items-center gap-2">
            <h2 className="text-body-sm @md:text-subtitle font-semibold text-slate-800 dark:text-slate-100">
              值域约束 <span className="font-normal text-slate-400">(Value Domains)</span>
            </h2>
            <Badge variant="blue">
              {filteredDomains.length} / {total}
            </Badge>
          </div>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="bg-slate-900 dark:bg-blue-600 text-white px-3 @md:px-4 py-1 @md:py-1.5 rounded-lg text-caption @md:text-body-sm font-medium flex items-center gap-1 @md:gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all"
        >
          <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">创建值域</span>
        </button>
      </div>

      {/* 卡片列表 */}
      <div className="flex-1 min-h-0 p-4 @md:p-6 overflow-auto custom-scrollbar">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16">
            <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
            <div className="text-slate-400 text-caption mt-3">加载中...</div>
          </div>
        ) : filteredDomains.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400 text-caption">
            未找到匹配的值域
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 @md:gap-5">
              {filteredDomains.map((domain) => (
                <ValueDomainCard
                  key={domain.domainCode}
                  domain={domain}
                  onDelete={handleDelete}
                  onEdit={setEditingDomain}
                />
              ))}
            </div>
            {/* 哨兵元素 + 加载更多 */}
            <div ref={sentinelRef} className="h-1" />
            {loadingMore && (
              <div className="flex justify-center py-6">
                <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
              </div>
            )}
          </>
        )}
      </div>

      {/* 创建值域模态框 */}
      <ValueDomainFormModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSave={handleSave}
        saving={saving}
      />

      {/* 编辑值域模态框 */}
      <EditValueDomainModal
        isOpen={editingDomain !== null}
        domain={editingDomain}
        onClose={() => setEditingDomain(null)}
        onUpdated={handleDomainUpdated}
      />
    </div>
  )
}

/** 编辑值域枚举项 */
interface EditEnumItem {
  key: string
  value: string
}

/** 编辑值域模态框 */
function EditValueDomainModal({
  isOpen,
  domain,
  onClose,
  onUpdated
}: {
  isOpen: boolean
  domain: ValueDomainDTO | null
  onClose: () => void
  onUpdated: () => void
}) {
  const [domainName, setDomainName] = useState('')
  const [domainLevel, setDomainLevel] = useState<'BUSINESS' | 'BUILTIN'>('BUSINESS')
  const [dataType, setDataType] = useState<DataTypeValue>({ type: 'STRING' })
  const [comment, setComment] = useState('')
  const [enumItems, setEnumItems] = useState<EditEnumItem[]>([])
  const [itemValue, setItemValue] = useState('') // RANGE/REGEX 类型的值
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (isOpen && domain) {
      setDomainName(domain.domainName)
      setDomainLevel((domain.domainLevel?.toUpperCase() as 'BUSINESS' | 'BUILTIN') || 'BUSINESS')
      setDataType(parseDataTypeString(domain.dataType))
      setComment(domain.comment || '')
      const items = domain.items?.map((item) => ({
        key: item.value,
        value: item.label || ''
      })) || []
      setEnumItems(items)
      // RANGE/REGEX 类型
      if (domain.domainType !== 'ENUM' && domain.items?.length) {
        setItemValue(domain.items[0].value)
      } else {
        setItemValue('')
      }
    }
  }, [isOpen, domain])

  // 添加枚举项
  const addEnumItem = () => {
    setEnumItems([...enumItems, { key: '', value: '' }])
  }

  // 删除枚举项
  const removeEnumItem = (index: number) => {
    if (enumItems.length <= 1) return
    setEnumItems(enumItems.filter((_, i) => i !== index))
  }

  // 更新枚举项
  const updateEnumItem = (index: number, field: 'key' | 'value', val: string) => {
    const newItems = [...enumItems]
    newItems[index] = { ...newItems[index], [field]: val }
    setEnumItems(newItems)
  }

  // 校验
  const isValid = (() => {
    if (!domainName.trim()) return false
    const normalizedType = domain?.domainType?.toUpperCase() || 'ENUM'
    if (normalizedType === 'ENUM') {
      return enumItems.some((item) => item.key.trim())
    }
    return itemValue.trim().length > 0
  })()

  const handleSave = async () => {
    if (!domain || saving || !isValid) return

    setSaving(true)
    try {
      const normalizedType = domain.domainType?.toUpperCase() || 'ENUM'

      // 构建 items
      let items: { value: string; label?: string }[] = []
      if (normalizedType === 'ENUM') {
        items = enumItems
          .filter((item) => item.key.trim())
          .map((item) => ({
            value: item.key.trim(),
            label: item.value.trim() || undefined
          }))
      } else {
        items = [{ value: itemValue.trim() }]
      }

      // 直接更新整个值域
      await updateValueDomain(domain.domainCode, {
        domainName: domainName.trim(),
        domainLevel,
        items,
        comment: comment.trim() || undefined,
        dataType: buildDataTypeString(dataType)
      })

      onUpdated()
      onClose()
    } catch {
      // 更新失败
    } finally {
      setSaving(false)
    }
  }

  if (!domain) return null

  const normalizedType = (domain.domainType?.toUpperCase() || 'ENUM') as ValueDomainType
  const typeLabel = normalizedType === 'ENUM' ? '枚举型 (ENUM)' : normalizedType === 'RANGE' ? '区间型 (RANGE)' : '模式型 (REGEX)'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="编辑值域"
      size="sm"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!isValid}
            loading={saving}
          >
            保存
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* 值域名称 + 编码 - 两栏布局 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域名称 <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={domainName}
              onChange={(e) => setDomainName(e.target.value)}
              placeholder="订单状态"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域编码
            </label>
            <div className="px-3 py-2 text-body-sm font-mono uppercase bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
              {domain.domainCode}
            </div>
          </div>
        </div>

        {/* 值域类型 + 值域级别 - 两栏布局 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域类型
            </label>
            <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
              {typeLabel}
            </div>
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域级别
            </label>
            <Select
              value={domainLevel}
              onChange={(value) => setDomainLevel(value as 'BUSINESS' | 'BUILTIN')}
              options={[
                { value: 'BUSINESS', label: '业务级 (BUSINESS)' },
                { value: 'BUILTIN', label: '内置级 (BUILTIN)' }
              ]}
              dropdownHeader="选择值域级别"
            />
          </div>
        </div>

        {/* 数据类型 - 窄宽 */}
        <div className="w-1/3">
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            数据类型 <span className="text-rose-500">*</span>
          </label>
          <DataTypeSelector
            value={dataType}
            onChange={setDataType}
            size="small"
            triggerClassName="w-full h-[38px]"
          />
        </div>

        {/* 值项 - 根据类型显示不同输入 */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {normalizedType === 'ENUM' ? '枚举值列表' : normalizedType === 'RANGE' ? '区间表达式' : '正则表达式'} <span className="text-rose-500">*</span>
          </label>

          {normalizedType === 'ENUM' ? (
            // ENUM: 可动态添加/删除的 Key-Value 列表
            <div className="space-y-2">
              {/* 表头 */}
              <div className="grid grid-cols-[1fr_1fr_32px] gap-2 text-micro text-slate-400 px-1">
                <span>值 (Key)</span>
                <span>标签 (Value)</span>
                <span></span>
              </div>
              {/* 列表项 */}
              {enumItems.map((item, index) => (
                <div key={index} className="grid grid-cols-[1fr_1fr_32px] gap-2 items-center">
                  <input
                    type="text"
                    value={item.key}
                    onChange={(e) => updateEnumItem(index, 'key', e.target.value)}
                    placeholder="PAID"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <input
                    type="text"
                    value={item.value}
                    onChange={(e) => updateEnumItem(index, 'value', e.target.value)}
                    placeholder="已支付"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <button
                    type="button"
                    onClick={() => removeEnumItem(index)}
                    disabled={enumItems.length <= 1}
                    className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              {/* 添加按钮 */}
              <button
                type="button"
                onClick={addEnumItem}
                className="w-full py-2 border border-dashed border-slate-300 dark:border-slate-600 rounded-lg text-caption text-slate-500 hover:text-blue-600 hover:border-blue-400 dark:hover:border-blue-500 transition-all flex items-center justify-center gap-1"
              >
                <Plus size={14} /> 添加枚举值
              </button>
            </div>
          ) : (
            // RANGE/REGEX: 单输入框（只读）
            <div>
              <div className="px-3 py-2 text-body-sm font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
                {itemValue || '-'}
              </div>
              <p className="text-micro text-slate-400 mt-1">
                {normalizedType === 'RANGE' ? '区间表达式不可修改' : '正则表达式不可修改'}
              </p>
            </div>
          )}
        </div>

        {/* 备注 */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            备注
          </label>
          <input
            type="text"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="值域说明（可选）"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    </Modal>
  )
}
