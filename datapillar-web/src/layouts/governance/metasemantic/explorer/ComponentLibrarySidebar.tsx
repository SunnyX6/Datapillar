/**
 * 语义组件库侧边栏
 * 包含修饰符和单位两个分类
 * 支持折叠/展开，默认折叠
 */

import { useState, useEffect, useCallback, useRef, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import { Sparkles, Scale, Plus, ChevronLeft, ChevronRight, Loader2, Trash2, ChevronDown, X, Check, Tag, Pencil } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { Modal, ModalCancelButton, ModalPrimaryButton } from '@/components/ui'
import {
  fetchUnits,
  fetchModifiers,
  createUnit,
  createModifier,
  deleteUnit,
  deleteModifier,
  updateModifier,
  updateUnit,
  fetchValueDomains,
  type UnitDTO,
  type MetricModifierDTO
} from '@/services/oneMetaSemanticService'

// 常量定义

type TabType = 'MODIFIER' | 'UNIT'

/** 值域分组数据 */
interface ValueDomainGroup {
  domainCode: string
  domainName: string
  items: Array<{ value: string; label?: string }>
}

/** 单位符号分组 */
const SYMBOL_GROUPS = [
  {
    name: '货币',
    color: 'text-amber-500 bg-amber-50 dark:bg-amber-900/30',
    items: [
      { symbol: '¥', label: '人民币' },
      { symbol: '$', label: '美元' },
      { symbol: '€', label: '欧元' },
      { symbol: '£', label: '英镑' },
      { symbol: '₩', label: '韩元' },
      { symbol: '₹', label: '印度卢比' }
    ]
  },
  {
    name: '比率',
    color: 'text-blue-500 bg-blue-50 dark:bg-blue-900/30',
    items: [
      { symbol: '%', label: '百分比' },
      { symbol: '‰', label: '千分比' },
      { symbol: '‱', label: '万分比' }
    ]
  },
  {
    name: '温度',
    color: 'text-rose-500 bg-rose-50 dark:bg-rose-900/30',
    items: [
      { symbol: '℃', label: '摄氏度' },
      { symbol: '℉', label: '华氏度' },
      { symbol: 'K', label: '开尔文' }
    ]
  },
  {
    name: '长度',
    color: 'text-cyan-500 bg-cyan-50 dark:bg-cyan-900/30',
    items: [
      { symbol: 'm', label: '米' },
      { symbol: '㎞', label: '千米' },
      { symbol: '㎝', label: '厘米' },
      { symbol: '㎜', label: '毫米' }
    ]
  },
  {
    name: '面积/体积',
    color: 'text-purple-500 bg-purple-50 dark:bg-purple-900/30',
    items: [
      { symbol: '㎡', label: '平方米' },
      { symbol: '㎥', label: '立方米' }
    ]
  },
  {
    name: '重量',
    color: 'text-orange-500 bg-orange-50 dark:bg-orange-900/30',
    items: [
      { symbol: '㎏', label: '千克' },
      { symbol: 'g', label: '克' },
      { symbol: '㎎', label: '毫克' },
      { symbol: 't', label: '吨' }
    ]
  },
  {
    name: '时间',
    color: 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/30',
    items: [
      { symbol: 's', label: '秒' },
      { symbol: 'min', label: '分钟' },
      { symbol: 'h', label: '小时' },
      { symbol: 'd', label: '天' }
    ]
  }
]

const normalizeText = (value: string) => value.trim().toLowerCase()

const findSymbolByName = (name: string) => {
  const normalized = normalizeText(name)
  if (!normalized) return null
  for (const group of SYMBOL_GROUPS) {
    const item = group.items.find((i) => normalizeText(i.label ?? '') === normalized)
    if (item) return item.symbol
  }
  return null
}

const filterSymbolGroups = (query: string) => {
  const normalized = normalizeText(query)
  if (!normalized) return SYMBOL_GROUPS
  return SYMBOL_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        const labelText = normalizeText(item.label ?? '')
        return labelText.includes(normalized) || item.symbol.includes(query.trim())
      })
    }))
    .filter((group) => group.items.length > 0)
}

const findSymbolMeta = (symbol: string) => {
  const normalized = symbol.trim()
  if (!normalized) return null
  for (const group of SYMBOL_GROUPS) {
    const item = group.items.find((i) => i.symbol === normalized)
    if (item) return { ...item, color: group.color }
  }
  return null
}

/** 创建单位模态框 */
function CreateUnitModal({
  isOpen,
  onClose,
  onCreated
}: {
  isOpen: boolean
  onClose: () => void
  onCreated: (unit: UnitDTO) => void
}) {
  const [form, setForm] = useState({ code: '', name: '', symbol: '', comment: '' })
  const [saving, setSaving] = useState(false)
  const [nameDropdownOpen, setNameDropdownOpen] = useState(false)
  const nameTriggerRef = useRef<HTMLInputElement>(null)
  const nameDropdownRef = useRef<HTMLDivElement>(null)
  const [nameDropdownPos, setNameDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const [nameFilterActive, setNameFilterActive] = useState(false)
  const nameFilterQuery = nameFilterActive ? form.name : ''
  const filteredNameGroups = filterSymbolGroups(nameFilterQuery)
  const symbolMeta = findSymbolMeta(form.symbol)
  const hasSymbol = !!form.symbol.trim()

  // 点击外部关闭
  useEffect(() => {
    if (!nameDropdownOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (nameTriggerRef.current?.contains(target)) return
      if (nameDropdownRef.current?.contains(target)) return
      setNameDropdownOpen(false)
      setNameFilterActive(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [nameDropdownOpen])

  // 计算下拉位置
  useLayoutEffect(() => {
    if (!nameDropdownOpen) return
    const updatePosition = () => {
      const btn = nameTriggerRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const dropdownHeight = 340
      const spaceBelow = window.innerHeight - rect.bottom - 20
      const spaceAbove = rect.top - 20

      // 如果下方空间不够且上方空间更大，则向上展开
      if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
        setNameDropdownPos({
          top: rect.top - Math.min(dropdownHeight, spaceAbove) - 4,
          left: rect.left,
          width: rect.width
        })
      } else {
        setNameDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
      }
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [nameDropdownOpen])

  const handleSave = async () => {
    if (!form.code.trim() || !form.name.trim() || saving) return
    setSaving(true)
    try {
      const unit = await createUnit({
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        symbol: form.symbol.trim() || undefined,
        comment: form.comment.trim() || undefined
      })
      onCreated(unit)
      setForm({ code: '', name: '', symbol: '', comment: '' })
      onClose()
    } catch {
      // 创建失败
    } finally {
      setSaving(false)
    }
  }

  const handleNameChange = (name: string) => {
    const matchedSymbol = findSymbolByName(name)
    setNameFilterActive(true)
    setNameDropdownOpen(true)
    setForm((prev) => ({
      ...prev,
      name,
      symbol: matchedSymbol ?? prev.symbol
    }))
  }

  const handleSelectName = (symbol: string, name?: string) => {
    const nextName = name ?? symbol
    setForm((prev) => ({
      ...prev,
      name: nextName,
      symbol
    }))
    setNameDropdownOpen(false)
    setNameFilterActive(false)
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="新建单位"
      size="mini"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!form.code.trim() || !form.name.trim()}
            loading={saving}
          >
            保存
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">编码 *</label>
          <input
            type="text"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
            placeholder="如: CNY"
            className="w-full px-4 py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">名称 *</label>
          <div className="relative">
            {hasSymbol && (
              <span
                className={`absolute left-3 top-1/2 -translate-y-1/2 size-6 flex items-center justify-center rounded text-sm font-bold ${
                  symbolMeta
                    ? symbolMeta.color
                    : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
                }`}
              >
                {form.symbol}
              </span>
            )}
            <input
              ref={nameTriggerRef}
              type="text"
              value={form.name}
              onChange={(e) => handleNameChange(e.target.value)}
              onFocus={() => {
                setNameDropdownOpen(true)
                setNameFilterActive(false)
              }}
              placeholder="如: 人民币"
              className={`w-full ${hasSymbol ? 'pl-12 pr-10' : 'px-4 pr-10'} py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600`}
            />
            <button
              type="button"
              onClick={() => {
                setNameFilterActive(false)
                setNameDropdownOpen((open) => !open)
              }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-500 transition-colors"
              aria-label="打开单位名称列表"
            >
              <ChevronDown size={14} className={`transition-transform ${nameDropdownOpen ? 'rotate-180' : ''}`} />
            </button>
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">符号</label>
          <input
            type="text"
            value={form.symbol}
            onChange={(e) => setForm({ ...form, symbol: e.target.value })}
            placeholder="可选，支持自定义符号"
            className="w-full px-4 py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">描述</label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="可选"
            className="w-full px-4 py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>

      {/* 名称选择下拉 */}
      {nameDropdownOpen && nameDropdownPos && createPortal(
        <div
          ref={nameDropdownRef}
          style={{
            '--symbol-dropdown-top': `${nameDropdownPos.top}px`,
            '--symbol-dropdown-left': `${nameDropdownPos.left}px`,
            '--symbol-dropdown-width': `${nameDropdownPos.width}px`
          } as React.CSSProperties}
          className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl overflow-hidden top-[var(--symbol-dropdown-top)] left-[var(--symbol-dropdown-left)] w-[var(--symbol-dropdown-width)]"
        >
          {/* 分组列表 */}
          <div className="max-h-64 overflow-y-auto p-2 custom-scrollbar">
            {filteredNameGroups.length === 0 ? (
              <div className="py-6 text-center text-caption text-slate-400">暂无匹配名称</div>
            ) : (
              filteredNameGroups.map((group) => (
                <div key={group.name} className="mb-2 last:mb-0">
                  <div className="px-2 py-1 text-micro font-semibold text-slate-400 uppercase">{group.name}</div>
                  <div className="grid grid-cols-3 gap-1">
                    {group.items.map((item) => (
                      <button
                        key={item.symbol}
                        type="button"
                        onClick={() => handleSelectName(item.symbol, item.label)}
                        className={`flex items-center gap-2 px-2 py-1.5 rounded-lg transition-all ${
                          form.name === item.label
                            ? 'bg-amber-100 dark:bg-amber-900/50 ring-1 ring-amber-400'
                            : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                        }`}
                      >
                        <span className={`w-5 h-5 flex items-center justify-center rounded text-xs font-bold ${group.color}`}>
                          {item.symbol}
                        </span>
                        <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>,
        document.body
      )}
    </Modal>
  )
}

/** 创建修饰符模态框 */
function CreateModifierModal({
  isOpen,
  onClose,
  onCreated
}: {
  isOpen: boolean
  onClose: () => void
  onCreated: (modifier: MetricModifierDTO) => void
}) {
  const [form, setForm] = useState({ code: '', name: '', comment: '', modifierType: '' })
  const [saving, setSaving] = useState(false)
  const [valueDomains, setValueDomains] = useState<ValueDomainGroup[]>([])
  const [selectedDomain, setSelectedDomain] = useState<string>('')
  const [loadingDomains, setLoadingDomains] = useState(false)

  // 下拉状态
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [hoveredDomain, setHoveredDomain] = useState<string | null>(null)
  const [isHoveringCard, setIsHoveringCard] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const itemCardRef = useRef<HTMLDivElement>(null)
  const hoverItemRef = useRef<HTMLDivElement | null>(null)
  const leaveTimerRef = useRef<number | null>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const [itemCardPos, setItemCardPos] = useState<{ top: number; left: number } | null>(null)

  // 加载值域列表
  useEffect(() => {
    if (!isOpen) return
    setLoadingDomains(true)
    fetchValueDomains(0, 100)
      .then((res) => {
        const groups: ValueDomainGroup[] = res.items.map((domain) => ({
          domainCode: domain.domainCode,
          domainName: domain.domainName,
          items: domain.items.map((item) => ({
            value: item.value,
            label: item.label || item.value
          }))
        }))
        setValueDomains(groups)
      })
      .catch(() => {})
      .finally(() => setLoadingDomains(false))
  }, [isOpen])

  // 点击外部关闭
  useEffect(() => {
    if (!dropdownOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (triggerRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      if (itemCardRef.current?.contains(target)) return
      setDropdownOpen(false)
      setHoveredDomain(null)
      setIsHoveringCard(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [dropdownOpen])

  // 清理定时器
  useEffect(() => {
    return () => {
      if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current)
    }
  }, [])

  // 计算下拉位置
  useLayoutEffect(() => {
    if (!dropdownOpen) return
    const updatePosition = () => {
      const btn = triggerRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const dropdownHeight = 280
      const spaceBelow = window.innerHeight - rect.bottom - 20
      const spaceAbove = rect.top - 20

      if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
        setDropdownPos({ top: rect.top - Math.min(dropdownHeight, spaceAbove) - 4, left: rect.left, width: rect.width })
      } else {
        setDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
      }
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [dropdownOpen])

  // 计算枚举项卡片位置
  useLayoutEffect(() => {
    const updatePos = () => {
      if (!hoveredDomain || !hoverItemRef.current) {
        if (!isHoveringCard) setItemCardPos(null)
        return
      }
      const domain = valueDomains.find((g) => g.domainCode === hoveredDomain)
      if (!domain?.items.length) {
        setItemCardPos(null)
        return
      }
      const itemRect = hoverItemRef.current.getBoundingClientRect()
      const cardWidth = 200
      const cardHeight = Math.min(domain.items.length * 44 + 12, 220)

      let top = itemRect.top
      let left = itemRect.right + 8

      if (top + cardHeight > window.innerHeight - 20) {
        top = window.innerHeight - cardHeight - 20
      }
      if (left + cardWidth > window.innerWidth - 20) {
        left = itemRect.left - cardWidth - 8
      }
      setItemCardPos({ top, left })
    }
    const rafId = requestAnimationFrame(updatePos)
    return () => cancelAnimationFrame(rafId)
  }, [hoveredDomain, isHoveringCard, valueDomains])

  // 获取显示文本（code (名称)）
  const getDisplayText = () => {
    if (!selectedDomain || !form.modifierType) return ''
    const domain = valueDomains.find((g) => g.domainCode === selectedDomain)
    const item = domain?.items.find((i) => i.value === form.modifierType)
    if (!item) return ''
    return item.label ? `${item.value} (${item.label})` : item.value
  }

  // 鼠标事件处理
  const handleItemMouseEnter = (domainCode: string) => {
    if (leaveTimerRef.current) {
      clearTimeout(leaveTimerRef.current)
      leaveTimerRef.current = null
    }
    setHoveredDomain(domainCode)
  }

  const handleItemMouseLeave = () => {
    leaveTimerRef.current = window.setTimeout(() => {
      if (!isHoveringCard) setHoveredDomain(null)
      leaveTimerRef.current = null
    }, 150)
  }

  const handleCardMouseEnter = () => {
    if (leaveTimerRef.current) {
      clearTimeout(leaveTimerRef.current)
      leaveTimerRef.current = null
    }
    setIsHoveringCard(true)
  }

  const handleCardMouseLeave = () => {
    setIsHoveringCard(false)
    setHoveredDomain(null)
  }

  // 选择枚举项
  const handleSelectItem = (domainCode: string, itemValue: string) => {
    setSelectedDomain(domainCode)
    setForm({ ...form, modifierType: itemValue })
    setDropdownOpen(false)
    setHoveredDomain(null)
    setIsHoveringCard(false)
  }

  // 清除选择
  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation()
    setSelectedDomain('')
    setForm({ ...form, modifierType: '' })
  }

  const handleSave = async () => {
    if (!form.code.trim() || !form.name.trim() || saving) return
    setSaving(true)
    try {
      const modifier = await createModifier({
        code: form.code.trim().toUpperCase(),
        name: form.name.trim(),
        comment: form.comment.trim() || undefined,
        modifierType: form.modifierType || undefined
      })

      onCreated(modifier)
      setForm({ code: '', name: '', comment: '', modifierType: '' })
      setSelectedDomain('')
      onClose()
    } catch {
      // 创建失败
    } finally {
      setSaving(false)
    }
  }

  const activeHoveredDomain = hoveredDomain || (isHoveringCard ? selectedDomain : null)
  const showItemCard = dropdownOpen && itemCardPos && activeHoveredDomain &&
    valueDomains.find((g) => g.domainCode === activeHoveredDomain)?.items.length

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="新建修饰符"
      size="sm"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!form.code.trim() || !form.name.trim()}
            loading={saving}
          >
            保存
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* 名称 */}
        <div>
          <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">名称 *</label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="如: 同比"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>

        {/* 编码 + 修饰符类型 并列 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">编码 *</label>
            <input
              type="text"
              value={form.code}
              onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
              placeholder="如: YOY"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">修饰符类型</label>
            <button
              ref={triggerRef}
              type="button"
              disabled={loadingDomains}
              onClick={() => setDropdownOpen(!dropdownOpen)}
              className={`w-full flex items-center gap-2 px-3 py-2 bg-white dark:bg-slate-800 border rounded-xl transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
                dropdownOpen
                  ? 'border-blue-400 dark:border-blue-500 ring-2 ring-blue-100 dark:ring-blue-900/50'
                  : 'border-slate-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-600'
              }`}
            >
              <Tag size={14} className={getDisplayText() ? 'text-blue-500' : 'text-slate-400'} />
              <span className={`flex-1 text-left text-body-sm truncate ${getDisplayText() ? 'text-slate-800 dark:text-slate-100 font-medium' : 'text-slate-400'}`}>
                {getDisplayText() || '选择类型'}
              </span>
              {getDisplayText() && (
                <button
                  onClick={handleClear}
                  className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-md transition-colors"
                >
                  <X size={12} className="text-slate-400 hover:text-slate-600" />
                </button>
              )}
              <ChevronDown size={12} className={`text-slate-400 transition-transform duration-200 ${dropdownOpen ? 'rotate-180' : ''}`} />
            </button>
          </div>
        </div>

        {/* 描述 */}
        <div>
          <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">描述</label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="可选"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>

      {/* 值域下拉面板 - Portal */}
      {dropdownOpen && dropdownPos && createPortal(
        <div
          ref={dropdownRef}
          style={{ top: dropdownPos.top, left: dropdownPos.left }}
          className="fixed z-[1000000] min-w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl animate-in fade-in-0 zoom-in-95 duration-150"
        >
          <div
            className="max-h-72 overflow-y-auto overscroll-contain p-1.5"
            style={{ scrollbarWidth: 'thin' }}
            onWheel={(e) => e.stopPropagation()}
          >
            {valueDomains.length === 0 ? (
              <div className="px-4 py-6 text-center">
                <Tag size={24} className="mx-auto mb-2 text-slate-300 dark:text-slate-600" />
                <p className="text-body-sm text-slate-500">暂无值域</p>
              </div>
            ) : (
              valueDomains.map((domain) => {
                const isSelected = selectedDomain === domain.domainCode
                const isHovered = hoveredDomain === domain.domainCode
                return (
                  <div
                    key={domain.domainCode}
                    ref={isHovered ? hoverItemRef : null}
                    onMouseEnter={() => handleItemMouseEnter(domain.domainCode)}
                    onMouseLeave={handleItemMouseLeave}
                    className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${
                      isHovered
                        ? 'bg-blue-50 dark:bg-blue-900/30'
                        : isSelected
                          ? 'bg-slate-50 dark:bg-slate-800'
                          : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                  >
                    <Tag size={iconSizeToken.small} className={isHovered ? 'text-blue-500' : 'text-slate-400'} />
                    <span className={`flex-1 text-body-sm font-medium ${
                      isHovered ? 'text-blue-600 dark:text-blue-400' : 'text-slate-700 dark:text-slate-300'
                    }`}>
                      {domain.domainName}
                    </span>
                    <div className="flex items-center gap-1">
                      <span className="text-micro text-slate-400">{domain.items.length}</span>
                      <ChevronRight size={iconSizeToken.tiny} className={isHovered ? 'text-blue-400' : 'text-slate-400'} />
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </div>,
        document.body
      )}

      {/* 枚举项卡片 - Portal */}
      {showItemCard && createPortal(
        <div
          ref={itemCardRef}
          style={{ top: itemCardPos.top, left: itemCardPos.left }}
          className="fixed z-[1000001] w-52 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl animate-in fade-in-0 slide-in-from-left-2 duration-150"
          onMouseEnter={handleCardMouseEnter}
          onMouseLeave={handleCardMouseLeave}
        >
          <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800">
            <p className="text-micro font-medium text-slate-500 dark:text-slate-400">选择类型</p>
          </div>
          <div
            className="max-h-52 overflow-y-auto overscroll-contain p-1.5"
            style={{ scrollbarWidth: 'thin' }}
            onWheel={(e) => e.stopPropagation()}
          >
            {(activeHoveredDomain ? valueDomains.find((g) => g.domainCode === activeHoveredDomain)?.items || [] : []).map((item) => {
              const isSelected = selectedDomain === activeHoveredDomain && form.modifierType === item.value
              const displayText = item.label ? `${item.value} (${item.label})` : item.value
              return (
                <button
                  key={item.value}
                  type="button"
                  onClick={() => handleSelectItem(activeHoveredDomain!, item.value)}
                  className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-left transition-all ${
                    isSelected
                      ? 'bg-blue-50 dark:bg-blue-900/30'
                      : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                  }`}
                >
                  <span className={`flex-1 text-body-sm font-medium ${
                    isSelected ? 'text-blue-600 dark:text-blue-400' : 'text-slate-700 dark:text-slate-300'
                  }`}>
                    {displayText}
                  </span>
                  {isSelected && <Check size={14} className="text-blue-500" />}
                </button>
              )
            })}
          </div>
        </div>,
        document.body
      )}
    </Modal>
  )
}

/** 编辑修饰符模态框 */
function EditModifierModal({
  isOpen,
  modifier,
  onClose,
  onUpdated
}: {
  isOpen: boolean
  modifier: MetricModifierDTO | null
  onClose: () => void
  onUpdated: (updated: MetricModifierDTO) => void
}) {
  const [form, setForm] = useState({ name: '', comment: '' })
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (isOpen && modifier) {
      setForm({
        name: modifier.name,
        comment: modifier.comment || ''
      })
    }
  }, [isOpen, modifier])

  const handleSave = async () => {
    if (!modifier || saving || !form.name.trim()) return
    setSaving(true)
    try {
      const updated = await updateModifier(modifier.code, {
        name: form.name.trim(),
        comment: form.comment.trim() || undefined
      })
      onUpdated(updated)
      onClose()
    } catch {
      // 更新失败
    } finally {
      setSaving(false)
    }
  }

  if (!modifier) return null

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="编辑修饰符"
      size="sm"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!form.name.trim()}
            loading={saving}
          >
            保存
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* 名称 */}
        <div>
          <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">名称 *</label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="如: 同比"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>

        {/* 编码 + 修饰符类型 并列 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">编码</label>
            <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400 font-mono">
              {modifier.code}
            </div>
          </div>
          <div>
            <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">修饰符类型</label>
            <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
              {modifier.modifierType || '-'}
            </div>
          </div>
        </div>

        {/* 描述 */}
        <div>
          <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">描述</label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="可选"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    </Modal>
  )
}

/** 编辑单位模态框 */
function EditUnitModal({
  isOpen,
  unit,
  onClose,
  onUpdated
}: {
  isOpen: boolean
  unit: UnitDTO | null
  onClose: () => void
  onUpdated: (updated: UnitDTO) => void
}) {
  const [form, setForm] = useState({ name: '', symbol: '', comment: '' })
  const [saving, setSaving] = useState(false)
  const [nameDropdownOpen, setNameDropdownOpen] = useState(false)
  const nameTriggerRef = useRef<HTMLInputElement>(null)
  const nameDropdownRef = useRef<HTMLDivElement>(null)
  const [nameDropdownPos, setNameDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const [nameFilterActive, setNameFilterActive] = useState(false)
  const nameFilterQuery = nameFilterActive ? form.name : ''
  const filteredNameGroups = filterSymbolGroups(nameFilterQuery)
  const symbolMeta = findSymbolMeta(form.symbol)
  const hasSymbol = !!form.symbol.trim()

  useEffect(() => {
    if (isOpen && unit) {
      setForm({
        name: unit.name,
        symbol: unit.symbol || '',
        comment: unit.comment || ''
      })
      setNameFilterActive(false)
    }
  }, [isOpen, unit])

  // 点击外部关闭符号下拉
  useEffect(() => {
    if (!nameDropdownOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (nameTriggerRef.current?.contains(target)) return
      if (nameDropdownRef.current?.contains(target)) return
      setNameDropdownOpen(false)
      setNameFilterActive(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [nameDropdownOpen])

  // 计算下拉位置
  useLayoutEffect(() => {
    if (!nameDropdownOpen) return
    const updatePosition = () => {
      const btn = nameTriggerRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const dropdownHeight = 340
      const spaceBelow = window.innerHeight - rect.bottom - 20
      const spaceAbove = rect.top - 20

      if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
        setNameDropdownPos({
          top: rect.top - Math.min(dropdownHeight, spaceAbove) - 4,
          left: rect.left,
          width: rect.width
        })
      } else {
        setNameDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
      }
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [nameDropdownOpen])

  const handleSave = async () => {
    if (!unit || saving || !form.name.trim()) return
    setSaving(true)
    try {
      const updated = await updateUnit(unit.code, {
        name: form.name.trim(),
        symbol: form.symbol.trim() || undefined,
        comment: form.comment.trim() || undefined
      })
      onUpdated(updated)
      onClose()
    } catch {
      // 更新失败
    } finally {
      setSaving(false)
    }
  }

  const handleNameChange = (name: string) => {
    const matchedSymbol = findSymbolByName(name)
    setNameFilterActive(true)
    setNameDropdownOpen(true)
    setForm((prev) => ({
      ...prev,
      name,
      symbol: matchedSymbol ?? prev.symbol
    }))
  }

  const handleSelectName = (symbol: string, name?: string) => {
    const nextName = name ?? symbol
    setForm((prev) => ({
      ...prev,
      name: nextName,
      symbol
    }))
    setNameDropdownOpen(false)
    setNameFilterActive(false)
  }

  if (!unit) return null

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="编辑单位"
      size="mini"
      footerRight={
        <>
          <ModalCancelButton onClick={onClose} disabled={saving} />
          <ModalPrimaryButton
            onClick={handleSave}
            disabled={!form.name.trim()}
            loading={saving}
          >
            保存
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">编码</label>
          <div className="px-4 py-2.5 text-sm bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400 font-mono">
            {unit.code}
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">名称 *</label>
          <div className="relative">
            {hasSymbol && (
              <span
                className={`absolute left-3 top-1/2 -translate-y-1/2 size-6 flex items-center justify-center rounded text-sm font-bold ${
                  symbolMeta
                    ? symbolMeta.color
                    : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
                }`}
              >
                {form.symbol}
              </span>
            )}
            <input
              ref={nameTriggerRef}
              type="text"
              value={form.name}
              onChange={(e) => handleNameChange(e.target.value)}
              onFocus={() => {
                setNameDropdownOpen(true)
                setNameFilterActive(false)
              }}
              placeholder="如: 人民币"
              className={`w-full ${hasSymbol ? 'pl-12 pr-10' : 'px-4 pr-10'} py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600`}
            />
            <button
              type="button"
              onClick={() => {
                setNameFilterActive(false)
                setNameDropdownOpen((open) => !open)
              }}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-500 transition-colors"
              aria-label="打开单位名称列表"
            >
              <ChevronDown size={14} className={`transition-transform ${nameDropdownOpen ? 'rotate-180' : ''}`} />
            </button>
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">符号</label>
          <input
            type="text"
            value={form.symbol}
            onChange={(e) => setForm({ ...form, symbol: e.target.value })}
            placeholder="可选，支持自定义符号"
            className="w-full px-4 py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">描述</label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="可选"
            className="w-full px-4 py-2.5 text-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>

      {/* 名称选择下拉 */}
      {nameDropdownOpen && nameDropdownPos && createPortal(
        <div
          ref={nameDropdownRef}
          style={{
            '--symbol-dropdown-top': `${nameDropdownPos.top}px`,
            '--symbol-dropdown-left': `${nameDropdownPos.left}px`,
            '--symbol-dropdown-width': `${nameDropdownPos.width}px`
          } as React.CSSProperties}
          className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl overflow-hidden top-[var(--symbol-dropdown-top)] left-[var(--symbol-dropdown-left)] w-[var(--symbol-dropdown-width)]"
        >
          {/* 分组列表 */}
          <div className="max-h-64 overflow-y-auto p-2 custom-scrollbar">
            {filteredNameGroups.length === 0 ? (
              <div className="py-6 text-center text-caption text-slate-400">暂无匹配名称</div>
            ) : (
              filteredNameGroups.map((group) => (
                <div key={group.name} className="mb-2 last:mb-0">
                  <div className="px-2 py-1 text-micro font-semibold text-slate-400 uppercase">{group.name}</div>
                  <div className="grid grid-cols-3 gap-1">
                    {group.items.map((item) => (
                      <button
                        key={item.symbol}
                        type="button"
                        onClick={() => handleSelectName(item.symbol, item.label)}
                        className={`flex items-center gap-2 px-2 py-1.5 rounded-lg transition-all ${
                          form.name === item.label
                            ? 'bg-amber-100 dark:bg-amber-900/50 ring-1 ring-amber-400'
                            : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                        }`}
                      >
                        <span className={`w-5 h-5 flex items-center justify-center rounded text-xs font-bold ${group.color}`}>
                          {item.symbol}
                        </span>
                        <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>,
        document.body
      )}
    </Modal>
  )
}

export function ComponentLibrarySidebar() {
  const [collapsed, setCollapsed] = useState(true)
  const [activeTab, setActiveTab] = useState<TabType>('UNIT')

  // 数据状态
  const [units, setUnits] = useState<UnitDTO[]>([])
  const [modifiers, setModifiers] = useState<MetricModifierDTO[]>([])
  const [loading, setLoading] = useState(false)

  // 模态框状态
  const [showUnitModal, setShowUnitModal] = useState(false)
  const [showModifierModal, setShowModifierModal] = useState(false)
  const [editingModifier, setEditingModifier] = useState<MetricModifierDTO | null>(null)
  const [editingUnit, setEditingUnit] = useState<UnitDTO | null>(null)

  // 删除状态
  const [deletingCode, setDeletingCode] = useState<string | null>(null)

  // 加载数据
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [unitsResult, modifiersResult] = await Promise.all([
        fetchUnits(0, 100),
        fetchModifiers(0, 100)
      ])
      setUnits(unitsResult.items)
      setModifiers(modifiersResult.items)
    } catch {
      // 加载失败
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!collapsed) {
      loadData()
    }
  }, [collapsed, loadData])

  // 删除单位
  const handleDeleteUnit = async (code: string) => {
    if (deletingCode) return
    setDeletingCode(code)
    try {
      await deleteUnit(code)
      setUnits((prev) => prev.filter((u) => u.code !== code))
    } catch {
      // 删除失败
    } finally {
      setDeletingCode(null)
    }
  }

  // 删除修饰符
  const handleDeleteModifier = async (code: string) => {
    if (deletingCode) return
    setDeletingCode(code)
    try {
      await deleteModifier(code)
      setModifiers((prev) => prev.filter((m) => m.code !== code))
    } catch {
      // 删除失败
    } finally {
      setDeletingCode(null)
    }
  }

  // 更新修饰符
  const handleModifierUpdated = (updated: MetricModifierDTO) => {
    setModifiers((prev) => prev.map((m) => (m.code === updated.code ? updated : m)))
  }

  // 更新单位
  const handleUnitUpdated = (updated: UnitDTO) => {
    setUnits((prev) => prev.map((u) => (u.code === updated.code ? updated : u)))
  }

  const items = activeTab === 'UNIT' ? units : modifiers
  const listTitle = activeTab === 'UNIT' ? '可用单位' : '可用修饰符'

  // 折叠状态
  if (collapsed) {
    return (
      <div className="w-12 border-l border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col items-center py-4">
        <div className="flex-1 flex flex-col items-center gap-3">
          <button
            onClick={() => {
              setActiveTab('MODIFIER')
              setCollapsed(false)
            }}
            className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-blue-500 hover:text-blue-600 transition-colors"
            title="修饰符"
          >
            <Sparkles size={iconSizeToken.medium} />
          </button>
          <button
            onClick={() => {
              setActiveTab('UNIT')
              setCollapsed(false)
            }}
            className="p-2 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded-lg text-amber-500 hover:text-amber-600 transition-colors"
            title="单位库"
          >
            <Scale size={iconSizeToken.medium} />
          </button>
        </div>
        <button
          onClick={() => setCollapsed(false)}
          className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          title="展开组件库"
        >
          <ChevronLeft size={iconSizeToken.medium} />
        </button>
      </div>
    )
  }

  return (
    <div className="w-64 border-l border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col overflow-hidden">
      {/* 标题 */}
      <div className="px-3 pt-3 pb-1.5">
        <h3 className="text-caption font-semibold text-slate-800 dark:text-slate-200">
          语义组件库 <span className="text-slate-400 font-normal text-micro">(COMPONENTS)</span>
        </h3>
      </div>

      {/* Tab 切换 */}
      <div className="px-3 py-1.5">
        <div className="flex bg-slate-100 dark:bg-slate-800 p-0.5 rounded-full">
          <button
            onClick={() => setActiveTab('MODIFIER')}
            className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
              activeTab === 'MODIFIER'
                ? 'bg-white dark:bg-slate-700 text-blue-500 shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Sparkles size={12} />
            修饰符
          </button>
          <button
            onClick={() => setActiveTab('UNIT')}
            className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
              activeTab === 'UNIT' ? 'bg-amber-500 text-white shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Scale size={12} />
            单位库
          </button>
        </div>
      </div>

      {/* 列表标题 */}
      <div className="px-3 py-1.5 flex items-center justify-between">
        <span className="text-micro text-slate-500">
          {listTitle} ({items.length})
        </span>
        <button
          onClick={() => (activeTab === 'UNIT' ? setShowUnitModal(true) : setShowModifierModal(true))}
          className="p-0.5 text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-colors"
        >
          <Plus size={14} />
        </button>
      </div>

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1.5 custom-scrollbar">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-5 h-5 animate-spin text-slate-400" />
          </div>
        ) : items.length === 0 ? (
          <div className="text-center py-8 text-micro text-slate-400">暂无数据</div>
        ) : activeTab === 'UNIT' ? (
          units.map((item) => (
            <div
              key={item.code}
              className="group flex items-center gap-2.5 p-2.5 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-lg hover:border-amber-200 dark:hover:border-amber-700 hover:shadow-sm transition-all"
            >
              <div className="w-8 h-8 rounded-full bg-amber-50 dark:bg-amber-900/30 flex items-center justify-center text-amber-600 dark:text-amber-400 font-bold text-caption flex-shrink-0">
                {item.symbol || item.code[0]}
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-caption font-medium text-slate-800 dark:text-slate-200 truncate">{item.name}</div>
                <div className="text-micro text-amber-600 dark:text-amber-400 font-medium truncate">{item.code}</div>
              </div>
              <div className="flex items-center gap-0.5">
                <button
                  onClick={() => setEditingUnit(item)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-amber-500 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded transition-all"
                  title="编辑"
                >
                  <Pencil size={12} />
                </button>
                <button
                  onClick={() => handleDeleteUnit(item.code)}
                  disabled={deletingCode === item.code}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded transition-all disabled:opacity-50"
                  title="删除"
                >
                  {deletingCode === item.code ? (
                    <Loader2 size={12} className="animate-spin" />
                  ) : (
                    <Trash2 size={12} />
                  )}
                </button>
              </div>
            </div>
          ))
        ) : (
          modifiers.map((item) => (
            <div
              key={item.code}
              className="group flex items-center gap-2.5 p-2.5 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-lg hover:border-blue-200 dark:hover:border-blue-700 hover:shadow-sm transition-all"
            >
              <div className="w-8 h-8 rounded-full bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-caption flex-shrink-0">
                {item.name[0]}
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-caption font-medium text-slate-800 dark:text-slate-200 truncate">{item.name}</div>
                <div className="text-micro text-blue-600 dark:text-blue-400 font-medium truncate">{item.code}</div>
              </div>
              <div className="flex items-center gap-0.5">
                <button
                  onClick={() => setEditingModifier(item)}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-all"
                  title="编辑"
                >
                  <Pencil size={12} />
                </button>
                <button
                  onClick={() => handleDeleteModifier(item.code)}
                  disabled={deletingCode === item.code}
                  className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded transition-all disabled:opacity-50"
                  title="删除"
                >
                  {deletingCode === item.code ? (
                    <Loader2 size={12} className="animate-spin" />
                  ) : (
                    <Trash2 size={12} />
                  )}
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* 底部区域 */}
      <div className="px-3 py-2 border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/50 flex items-center justify-between gap-2">
        <p className="text-micro text-slate-400 leading-relaxed flex-1">组装派生指标前，请确保组件库中已有所需口径片段。</p>
        <button
          onClick={() => setCollapsed(true)}
          className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-lg text-slate-400 hover:text-slate-600 transition-colors flex-shrink-0"
          title="折叠"
        >
          <ChevronRight size={iconSizeToken.medium} />
        </button>
      </div>

      {/* 创建单位模态框 */}
      <CreateUnitModal
        isOpen={showUnitModal}
        onClose={() => setShowUnitModal(false)}
        onCreated={(unit) => setUnits((prev) => [...prev, unit])}
      />

      {/* 编辑单位模态框 */}
      <EditUnitModal
        isOpen={editingUnit !== null}
        unit={editingUnit}
        onClose={() => setEditingUnit(null)}
        onUpdated={handleUnitUpdated}
      />

      {/* 创建修饰符模态框 */}
      <CreateModifierModal
        isOpen={showModifierModal}
        onClose={() => setShowModifierModal(false)}
        onCreated={(modifier) => setModifiers((prev) => [...prev, modifier])}
      />

      {/* 编辑修饰符模态框 */}
      <EditModifierModal
        isOpen={editingModifier !== null}
        modifier={editingModifier}
        onClose={() => setEditingModifier(null)}
        onUpdated={handleModifierUpdated}
      />
    </div>
  )
}
