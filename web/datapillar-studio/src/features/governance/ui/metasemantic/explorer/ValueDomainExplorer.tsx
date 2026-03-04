import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Plus, Users, Trash2, Loader2, Pencil, Clock, ChevronsRight } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { Badge } from '../components'
import { useSearchStore } from '@/state'
import { useInfiniteScroll } from '@/hooks'
import {
  Card,
  DataTypeSelector,
  Modal,
  ModalCancelButton,
  ModalPrimaryButton,
  Select,
  parseDataTypeString,
  buildDataTypeString,
  type DataTypeValue
} from '@/components/ui'
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
import { cn, formatTime } from '@/utils'

/** Number of loads per page */
const PAGE_SIZE = 20
/** Enumeration value card preview display number per page（Keep card size stable） */
const ENUM_PREVIEW_PAGE_SIZE = 6

/** Type label configuration */
const TYPE_CONFIG: Record<ValueDomainType, { label: string; color: string }> = {
  ENUM: { label: 'ENUM', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400 border-blue-200 dark:border-blue-800' },
  RANGE: { label: 'RANGE', color: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' },
  REGEX: { label: 'REGEX', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 border-purple-200 dark:border-purple-800' }
}

/** Level label configuration */
const LEVEL_CONFIG: Record<string, { label: string; color: string }> = {
  BUILTIN: { label: 'Built-in', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 border-amber-200 dark:border-amber-800' },
  BUSINESS: { label: 'Business', color: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400 border-slate-200 dark:border-slate-700' }
}

interface ValueDomainExplorerProps {
  onBack: () => void
}

/** Value range card component */
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
  const [previewPageIndex, setPreviewPageIndex] = useState(0)

  const enumItems = domain.items || []
  const previewPageCount = Math.max(1, Math.ceil(enumItems.length / ENUM_PREVIEW_PAGE_SIZE))
  const hasMultiplePreviewPages = previewPageCount > 1
  const showPreviewControls = enumItems.length > 0
  const clampedPreviewPageIndex = Math.min(previewPageIndex, previewPageCount - 1)

  const handleDelete = async (e: React.MouseEvent) => {
    e.stopPropagation()
    if (deleting || isBuiltin) return
    setDeleting(true)
    try {
      // Delete the entire value range directly
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

  const handleMorePreview = (e: React.MouseEvent) => {
    e.stopPropagation()
    if (!hasMultiplePreviewPages) return
    setPreviewPageIndex((prev) => (prev + 1) % previewPageCount)
  }

  const handleSelectPreviewPage = (e: React.MouseEvent, index: number) => {
    e.stopPropagation()
    setPreviewPageIndex(index)
  }

  const previewPages = Array.from({ length: previewPageCount }, (_, index) =>
    enumItems.slice(index * ENUM_PREVIEW_PAGE_SIZE, (index + 1) * ENUM_PREVIEW_PAGE_SIZE)
  )

  return (
    <Card
      padding="sm"
      variant="default"
      className="w-full group hover:shadow-md hover:border-slate-300 dark:hover:border-slate-700 duration-200"
    >
      {/* head：type tag + Data type tag + level label + Action button */}
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
            title="Edit"
          >
            <Pencil size={iconSizeToken.small} />
          </button>
          {!isBuiltin && (
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-50"
              title="Delete"
            >
              {deleting ? <Loader2 size={iconSizeToken.small} className="animate-spin" /> : <Trash2 size={iconSizeToken.small} />}
            </button>
          )}
        </div>
      </div>

      {/* Name and code */}
      <h3 className="font-semibold text-slate-800 dark:text-slate-100 text-body-sm mb-0.5">{domain.domainName}</h3>
      <p className="text-micro font-mono text-slate-400 dark:text-slate-500 uppercase tracking-tight mb-3">{domain.domainCode}</p>

      {/* Enum value preview area */}
      <div className="bg-black rounded-lg mb-2 h-14 flex overflow-hidden">
        <div className="flex-1 px-3 py-2 overflow-hidden">
          <div className="relative w-full h-full overflow-hidden">
            <div
              className="absolute inset-0 flex transition-transform duration-500 ease-in-out"
              style={{ transform: `translateX(-${clampedPreviewPageIndex * 100}%)` }}
            >
              {previewPages.map((pageItems, pageIndex) => (
                <div key={pageIndex} className="w-full flex-shrink-0">
                  {pageItems.length > 0 ? (
                    <div className="flex flex-nowrap items-center gap-1.5 h-full">
                      {pageItems.map((item) => (
                        <span
                          key={item.value}
                          className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-micro whitespace-nowrap"
                          title={item.label || item.value}
                        >
                          <code className="text-emerald-400 font-mono">{item.value}</code>
                          {item.label && <span className="text-slate-500">({item.label})</span>}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <div className="h-full flex items-center">
                      <span className="text-slate-500 text-micro">No enumeration value</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {showPreviewControls && (
          <button
            type="button"
            onClick={handleMorePreview}
            disabled={!hasMultiplePreviewPages}
            className={cn(
              'w-14 flex flex-col items-center justify-center border-l border-dashed border-slate-700/70 transition-colors flex-shrink-0',
              hasMultiplePreviewPages
                ? 'hover:border-emerald-400/80 text-slate-300 hover:text-emerald-300'
                : 'text-slate-300 cursor-default opacity-50'
            )}
            title={hasMultiplePreviewPages ? 'More' : 'No more enumeration values'}
            aria-label={hasMultiplePreviewPages ? 'More enumeration values' : 'No more enumeration values'}
          >
            <ChevronsRight size={iconSizeToken.extraLarge} className="mb-1" />
            <span className="text-micro font-semibold tracking-wide">More</span>
          </button>
        )}
      </div>
      {showPreviewControls && (
        <div className="mb-3 flex justify-center gap-2">
          {previewPages.map((_, index) => (
            <button
              key={index}
              type="button"
              onClick={(e) => handleSelectPreviewPage(e, index)}
              className={cn(
                'rounded-full transition-all duration-300',
                index === clampedPreviewPageIndex
                  ? 'w-2 h-2 bg-emerald-500 dark:bg-emerald-400'
                  : 'w-1.5 h-1.5 bg-slate-200 dark:bg-slate-700'
              )}
              aria-label={`Switch to page ${index + 1} page`}
              title={`No. ${index + 1} page`}
            />
          ))}
        </div>
      )}

      {/* bottom：Creator + creation time + Enumeration quantity */}
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
        <span className="text-micro text-slate-400">{domain.items?.length || 0} enumeration values</span>
      </div>
    </Card>
  )
}

export function ValueDomainExplorer({ onBack }: ValueDomainExplorerProps) {
  const searchTerm = useSearchStore((state) => state.searchTerm)

  // Data status
  const [valueDomains, setValueDomains] = useState<ValueDomainDTO[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  // Modal state
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingDomain, setEditingDomain] = useState<ValueDomainDTO | null>(null)
  const [saving, setSaving] = useState(false)

  // Is there more data?
  const hasMore = valueDomains.length < total

  // Load homepage data
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const result = await fetchValueDomains(0, PAGE_SIZE)
      setValueDomains(result.items)
      setTotal(result.total)
    } catch {
      // Keep empty list when loading fails
    } finally {
      setLoading(false)
    }
  }, [])

  // load more data
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const result = await fetchValueDomains(valueDomains.length, PAGE_SIZE)
      setValueDomains((prev) => [...prev, ...result.items])
      setTotal(result.total)
    } catch {
      // Loading failed
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, valueDomains.length])

  // infinite scroll
  const { sentinelRef } = useInfiniteScroll({
    hasMore,
    loading: loadingMore,
    onLoadMore: loadMore
  })

  useEffect(() => {
    loadData()
  }, [loadData])

  // Filter and sort ranges：BUILTIN in front，then sort by name
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
      // BUILTIN at the front
      if (levelA === 'BUILTIN' && levelB !== 'BUILTIN') return -1
      if (levelA !== 'BUILTIN' && levelB === 'BUILTIN') return 1
      // Sort by name at the same level
      return a.domainName.localeCompare(b.domainName, 'zh-CN')
    })

  // Save new value range
  const handleSave = async (formData: ValueDomainFormData) => {
    if (saving) return
    setSaving(true)
    try {
      let items: { value: string; label?: string }[] = []

      if (formData.domainType === 'ENUM') {
        // ENUM: Create multiple enumeration values in batches
        items = formData.enumItems
          .filter((item) => item.key.trim())
          .map((item) => ({
            value: item.key.trim(),
            label: item.value.trim() || undefined
          }))
      } else {
        // RANGE/REGEX: Single creation
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
      // Save failed
    } finally {
      setSaving(false)
    }
  }

  // Delete range
  const handleDelete = (domainCode: string) => {
    setValueDomains((prev) => prev.filter((d) => d.domainCode !== domainCode))
    setTotal((prev) => prev - 1)
  }

  // Reload data after updating range
  const handleDomainUpdated = () => {
    loadData()
  }

  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50/40 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      {/* top navigation bar */}
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
              range constraints <span className="font-normal text-slate-400">(Value Domains)</span>
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
          <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">Create a value range</span>
        </button>
      </div>

      {/* card list */}
      <div className="flex-1 min-h-0 p-4 @md:p-6 overflow-auto custom-scrollbar">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16">
            <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
            <div className="text-slate-400 text-caption mt-3">Loading...</div>
          </div>
        ) : filteredDomains.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400 text-caption">
            No matching range found
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 @md:grid-cols-3 gap-3 @md:gap-4 items-start">
              {filteredDomains.map((domain) => (
                <ValueDomainCard
                  key={domain.domainCode}
                  domain={domain}
                  onDelete={handleDelete}
                  onEdit={setEditingDomain}
                />
              ))}
            </div>
            {/* Sentinel element + load more */}
            <div ref={sentinelRef} className="h-1" />
            {loadingMore && (
              <div className="flex justify-center py-6">
                <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
              </div>
            )}
          </>
        )}
      </div>

      {/* Create a value field modal box */}
      <ValueDomainFormModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSave={handleSave}
        saving={saving}
      />

      {/* Edit value range modal box */}
      <EditValueDomainModal
        isOpen={editingDomain !== null}
        domain={editingDomain}
        onClose={() => setEditingDomain(null)}
        onUpdated={handleDomainUpdated}
      />
    </div>
  )
}

/** Edit value range enumeration items */
interface EditEnumItem {
  key: string
  value: string
}

/** Edit value range modal box */
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
  const [itemValue, setItemValue] = useState('') // RANGE/REGEX value of type
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
      // RANGE/REGEX Type
      if (domain.domainType !== 'ENUM' && domain.items?.length) {
        setItemValue(domain.items[0].value)
      } else {
        setItemValue('')
      }
    }
  }, [isOpen, domain])

  // Add enumeration item
  const addEnumItem = () => {
    setEnumItems([...enumItems, { key: '', value: '' }])
  }

  // Delete enumeration item
  const removeEnumItem = (index: number) => {
    if (enumItems.length <= 1) return
    setEnumItems(enumItems.filter((_, i) => i !== index))
  }

  // Update enumeration item
  const updateEnumItem = (index: number, field: 'key' | 'value', val: string) => {
    const newItems = [...enumItems]
    newItems[index] = { ...newItems[index], [field]: val }
    setEnumItems(newItems)
  }

  // Verify
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

      // Build items
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

      // Directly update the entire value range
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
      // Update failed
    } finally {
      setSaving(false)
    }
  }

  if (!domain) return null

  const normalizedType = (domain.domainType?.toUpperCase() || 'ENUM') as ValueDomainType
  const typeLabel = normalizedType === 'ENUM' ? 'enumeration type (ENUM)' : normalizedType === 'RANGE' ? 'Interval (RANGE)' : 'pattern (REGEX)'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Edit range"
      size="sm"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!isValid}
            loading={saving}
          >
            save
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* Value field name + encoding - two column layout */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Value field name <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={domainName}
              onChange={(e) => setDomainName(e.target.value)}
              placeholder="Order status"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              range encoding
            </label>
            <div className="px-3 py-2 text-body-sm font-mono uppercase bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
              {domain.domainCode}
            </div>
          </div>
        </div>

        {/* Range type + range level - two column layout */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Range type
            </label>
            <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
              {typeLabel}
            </div>
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              range level
            </label>
            <Select
              value={domainLevel}
              onChange={(value) => setDomainLevel(value as 'BUSINESS' | 'BUILTIN')}
              options={[
                { value: 'BUSINESS', label: 'business level (BUSINESS)' },
                { value: 'BUILTIN', label: 'Built-in level (BUILTIN)' }
              ]}
              dropdownHeader="Select range level"
              size="sm"
            />
          </div>
        </div>

        {/* data type - Narrow and wide */}
        <div className="w-1/3">
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            data type <span className="text-rose-500">*</span>
          </label>
          <DataTypeSelector
            value={dataType}
            onChange={setDataType}
            size="small"
            triggerClassName="w-full h-[38px] bg-white dark:bg-slate-900"
          />
        </div>

        {/* value item - Display different inputs based on type */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {normalizedType === 'ENUM' ? 'List of enumeration values' : normalizedType === 'RANGE' ? 'interval expression' : 'regular expression'} <span className="text-rose-500">*</span>
          </label>

          {normalizedType === 'ENUM' ? (
            // ENUM: Can be added dynamically/deleted Key-Value list
            <div className="space-y-2">
              {/* Header */}
              <div className="grid grid-cols-[1fr_1fr_32px] gap-2 text-micro text-slate-400 px-1">
                <span>value (Key)</span>
                <span>label (Value)</span>
                <span></span>
              </div>
              {/* list item */}
              {enumItems.map((item, index) => (
                <div key={index} className="grid grid-cols-[1fr_1fr_32px] gap-2 items-center">
                  <input
                    type="text"
                    value={item.key}
                    onChange={(e) => updateEnumItem(index, 'key', e.target.value)}
                    placeholder="PAID"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <input
                    type="text"
                    value={item.value}
                    onChange={(e) => updateEnumItem(index, 'value', e.target.value)}
                    placeholder="paid"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
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
              {/* Add button */}
              <button
                type="button"
                onClick={addEnumItem}
                className="w-full py-2 border border-dashed border-slate-300 dark:border-slate-600 rounded-lg text-caption text-slate-500 hover:text-blue-600 hover:border-blue-400 dark:hover:border-blue-500 transition-all flex items-center justify-center gap-1"
              >
                <Plus size={14} /> Add enumeration value
              </button>
            </div>
          ) : (
            // RANGE/REGEX: Single input box（read only）
            <div>
              <div className="px-3 py-2 text-body-sm font-mono bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
                {itemValue || '-'}
              </div>
              <p className="text-micro text-slate-400 mt-1">
                {normalizedType === 'RANGE' ? 'Interval expressions cannot be modified' : 'Regular expressions cannot be modified'}
              </p>
            </div>
          )}
        </div>

        {/* Remarks */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            Remarks
          </label>
          <input
            type="text"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Value range description（Optional）"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    </Modal>
  )
}
