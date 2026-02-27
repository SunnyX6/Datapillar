/**
 * 通用数据类型选择器组件
 *
 * 支持 Gravitino 全部数据类型，可按场景过滤：
 * - filter="all": 全部类型（创建表）
 * - filter="numeric": 数值类型（创建指标）
 * - filter={['STRING', 'INTEGER']}: 自定义类型列表
 */

import { useState, useRef, useEffect, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import {
  Type,
  Hash,
  Database,
  Ruler,
  Calendar,
  ToggleLeft,
  Clock,
  Binary,
  Braces,
  List,
  GitBranch,
  Fingerprint,
  CircleDot,
  ChevronDown,
  Check
} from 'lucide-react'
import { DEFAULT_LENGTH, getMaxLengthForType, type DataTypeValue } from '@/utils/dataType'
import { Button } from './Button'

/** 数据类型配置 */
interface DataTypeConfig {
  type: string
  label: string
  icon: React.ElementType
  category: 'numeric' | 'string' | 'datetime' | 'boolean' | 'binary' | 'complex' | 'other'
  hasParams?: 'decimal' | 'length'
}

/** Gravitino 完整数据类型配置 */
const DATA_TYPE_CONFIGS: DataTypeConfig[] = [
  { type: 'STRING', label: 'STRING', icon: Type, category: 'string' },
  { type: 'VARCHAR', label: 'VARCHAR', icon: Type, category: 'string', hasParams: 'length' },
  { type: 'FIXEDCHAR', label: 'CHAR', icon: Type, category: 'string', hasParams: 'length' },
  { type: 'BYTE', label: 'BYTE', icon: Hash, category: 'numeric' },
  { type: 'SHORT', label: 'SHORT', icon: Hash, category: 'numeric' },
  { type: 'INTEGER', label: 'INT', icon: Hash, category: 'numeric' },
  { type: 'BIGINT', label: 'BIGINT', icon: Database, category: 'numeric' },
  { type: 'FLOAT', label: 'FLOAT', icon: CircleDot, category: 'numeric' },
  { type: 'DOUBLE', label: 'DOUBLE', icon: CircleDot, category: 'numeric' },
  { type: 'DECIMAL', label: 'DECIMAL', icon: Ruler, category: 'numeric', hasParams: 'decimal' },
  { type: 'BOOLEAN', label: 'BOOL', icon: ToggleLeft, category: 'boolean' },
  { type: 'DATE', label: 'DATE', icon: Calendar, category: 'datetime' },
  { type: 'TIME', label: 'TIME', icon: Clock, category: 'datetime' },
  { type: 'TIMESTAMP', label: 'TIMESTAMP', icon: Clock, category: 'datetime' },
  { type: 'BINARY', label: 'BINARY', icon: Binary, category: 'binary' },
  { type: 'FIXED', label: 'FIXED', icon: Binary, category: 'binary', hasParams: 'length' },
  { type: 'UUID', label: 'UUID', icon: Fingerprint, category: 'other' },
  { type: 'STRUCT', label: 'STRUCT', icon: Braces, category: 'complex' },
  { type: 'LIST', label: 'LIST', icon: List, category: 'complex' },
  { type: 'MAP', label: 'MAP', icon: Braces, category: 'complex' },
  { type: 'UNION', label: 'UNION', icon: GitBranch, category: 'complex' }
]

/** 数值类型列表 */
const NUMERIC_TYPES = ['BYTE', 'SHORT', 'INTEGER', 'BIGINT', 'FLOAT', 'DOUBLE', 'DECIMAL']

export interface DataTypeSelectorProps {
  value: DataTypeValue
  onChange: (value: DataTypeValue) => void
  disabled?: boolean
  /** 类型过滤：'all' | 'numeric' | 自定义类型数组 */
  filter?: 'all' | 'numeric' | string[]
  /** 尺寸：'default' | 'small' */
  size?: 'default' | 'small'
  /** 空值占位文案 */
  placeholder?: string
  /** 触发按钮额外样式（用于容器内不溢出/对齐高度） */
  triggerClassName?: string
  /** 触发按钮文案额外样式（用于统一字号/截断） */
  labelClassName?: string
  /** 下拉宽度：'trigger' 跟随触发按钮 | 'auto' 自适应 | number 指定像素 */
  dropdownWidth?: 'trigger' | 'auto' | number
  /** 下拉容器额外样式 */
  dropdownClassName?: string
  /** 下拉列表额外样式 */
  dropdownListClassName?: string
  /** 选项按钮额外样式 */
  optionClassName?: string
}

/** 精度选择卡片 */
function DecimalPrecisionCard({
  precision,
  scale,
  onChange
}: {
  precision: number
  scale: number
  onChange: (p: number, s: number) => void
}) {
  return (
    <div className="w-52 bg-white dark:bg-slate-800 rounded-xl p-2.5 shadow-2xl border border-slate-200 dark:border-slate-700">
      <div className="text-micro text-slate-500 dark:text-slate-400 mb-2">
        设置精度 (P) 和小数位 (S)
      </div>
      <div className="space-y-2">
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-micro font-medium text-blue-600 dark:text-blue-400">精度 P</span>
            <span className="text-body-sm font-bold text-slate-800 dark:text-slate-200">{precision}</span>
          </div>
          <input
            type="range"
            min={1}
            max={38}
            value={precision}
            onChange={(e) => {
              const newP = Number(e.target.value)
              const newS = Math.min(scale, newP)
              onChange(newP, newS)
            }}
            className="w-full h-1.5 bg-slate-200 dark:bg-slate-600 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-blue-500 [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white"
          />
        </div>
        <div>
          <div className="flex items-center justify-between mb-1">
            <span className="text-micro font-medium text-amber-600 dark:text-amber-400">小数位 S</span>
            <span className="text-body-sm font-bold text-slate-800 dark:text-slate-200">{scale}</span>
          </div>
          <input
            type="range"
            min={0}
            max={precision}
            value={scale}
            onChange={(e) => onChange(precision, Number(e.target.value))}
            className="w-full h-1.5 bg-slate-200 dark:bg-slate-600 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-amber-500 [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white"
          />
        </div>
      </div>
      <div className="mt-1.5 text-micro text-slate-400 dark:text-slate-500">
        示例: DECIMAL({precision},{scale})
      </div>
    </div>
  )
}

/** 长度选择卡片 */
function LengthCard({
  length,
  min,
  max,
  onChange
}: {
  length: number
  min: number
  max: number
  onChange: (l: number) => void
}) {
  return (
    <div className="w-48 bg-white dark:bg-slate-800 rounded-xl p-2.5 shadow-2xl border border-slate-200 dark:border-slate-700">
      <div className="text-micro text-slate-500 dark:text-slate-400 mb-2">
        设置字符长度
      </div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-micro font-medium text-blue-600 dark:text-blue-400">长度</span>
        <span className="text-body-sm font-bold text-slate-800 dark:text-slate-200">{length}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        value={length}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full h-1.5 bg-slate-200 dark:bg-slate-600 rounded-full appearance-none cursor-pointer [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-blue-500 [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-white"
      />
      <div className="mt-1.5 text-micro text-slate-400 dark:text-slate-500">
        范围: {min} - {max}
      </div>
    </div>
  )
}

export function DataTypeSelector({
  value,
  onChange,
  disabled = false,
  filter = 'all',
  size = 'default',
  placeholder,
  triggerClassName,
  labelClassName,
  dropdownWidth = 'trigger',
  dropdownClassName,
  dropdownListClassName,
  optionClassName
}: DataTypeSelectorProps) {
  const [open, setOpen] = useState(false)
  const [hoveredType, setHoveredType] = useState<string | null>(null)
  const [isHoveringCard, setIsHoveringCard] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const paramCardRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number; width?: number } | null>(null)
  const [paramCardPos, setParamCardPos] = useState<{ top: number; left: number } | null>(null)
  const hoverItemRef = useRef<HTMLButtonElement | null>(null)
  const leaveTimerRef = useRef<number | null>(null)

  const isSmall = size === 'small'
  const dropdownMinWidth = isSmall ? 160 : 176
  const dropdownHeight = isSmall ? 252 : 272
  const dropdownRadiusClass = isSmall ? 'rounded-lg shadow-lg' : 'rounded-xl shadow-xl'
  const dropdownListClass = isSmall ? 'max-h-60 p-1' : 'max-h-64 p-1.5'
  const itemSizeClass = isSmall ? 'gap-2 px-2 py-1.5 rounded-md' : 'gap-2.5 px-2.5 py-2 rounded-lg'
  const itemTextClass = isSmall ? 'text-caption' : 'text-body-sm'
  const itemIconSize = isSmall ? 14 : 16
  const itemCheckSize = isSmall ? 12 : 14
  const itemChevronSize = isSmall ? 10 : 12
  const triggerIconSize = isSmall ? 12 : 16
  const triggerChevronSize = isSmall ? 12 : 14

  const filteredTypes = DATA_TYPE_CONFIGS.filter((config) => {
    if (filter === 'all') return true
    if (filter === 'numeric') return NUMERIC_TYPES.includes(config.type)
    if (Array.isArray(filter)) return filter.includes(config.type)
    return true
  })

  const currentConfig = DATA_TYPE_CONFIGS.find((c) => c.type === value.type)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (triggerRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      if (paramCardRef.current?.contains(target)) return
      setOpen(false)
      setHoveredType(null)
      setIsHoveringCard(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  useEffect(() => {
    return () => {
      if (leaveTimerRef.current) {
        clearTimeout(leaveTimerRef.current)
      }
    }
  }, [])

  useLayoutEffect(() => {
    if (!open) return
    const updatePosition = () => {
      const btn = triggerRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const spaceBelow = window.innerHeight - rect.bottom - 20
      const spaceAbove = rect.top - 20

      const resolvedWidth = dropdownWidth === 'auto'
        ? undefined
        : typeof dropdownWidth === 'number'
          ? dropdownWidth
          : Math.max(rect.width, dropdownMinWidth)

      // 如果下方空间不够且上方空间更大，则向上展开
      if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
        const top = rect.top - Math.min(dropdownHeight, spaceAbove) - 4
        const left = typeof resolvedWidth === 'number'
          ? Math.max(12, Math.min(rect.left, window.innerWidth - resolvedWidth - 12))
          : rect.left
        setDropdownPos({ top, left, width: resolvedWidth })
      } else {
        const top = rect.bottom + 4
        const left = typeof resolvedWidth === 'number'
          ? Math.max(12, Math.min(rect.left, window.innerWidth - resolvedWidth - 12))
          : rect.left
        setDropdownPos({ top, left, width: resolvedWidth })
      }
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [open, dropdownMinWidth, dropdownWidth, dropdownHeight])

  useLayoutEffect(() => {
    const updatePos = () => {
      if (!hoveredType || !hoverItemRef.current) {
        if (!isHoveringCard) {
          setParamCardPos(null)
        }
        return
      }
      const hoveredConfig = DATA_TYPE_CONFIGS.find((c) => c.type === hoveredType)
      if (!hoveredConfig?.hasParams) {
        setParamCardPos(null)
        return
      }
      const itemRect = hoverItemRef.current.getBoundingClientRect()
      const cardWidth = hoveredType === 'DECIMAL' ? 208 : 192
      const cardHeight = hoveredType === 'DECIMAL' ? 160 : 115

      let top = itemRect.top
      let left = itemRect.right + 8

      if (top + cardHeight > window.innerHeight - 20) {
        top = window.innerHeight - cardHeight - 20
      }
      if (left + cardWidth > window.innerWidth - 20) {
        left = itemRect.left - cardWidth - 8
      }

      setParamCardPos({ top, left })
    }
    const rafId = requestAnimationFrame(updatePos)
    return () => cancelAnimationFrame(rafId)
  }, [hoveredType, isHoveringCard])

  const handleSelect = (config: DataTypeConfig) => {
    const newValue: DataTypeValue = { type: config.type }
    if (config.hasParams === 'decimal') {
      newValue.precision = value.type === 'DECIMAL' ? (value.precision ?? 10) : 10
      newValue.scale = value.type === 'DECIMAL' ? (value.scale ?? 2) : 2
    } else if (config.hasParams === 'length') {
      newValue.length = config.type === value.type ? (value.length ?? DEFAULT_LENGTH) : DEFAULT_LENGTH
    }
    onChange(newValue)
    if (!config.hasParams) {
      setOpen(false)
      setHoveredType(null)
    }
  }

  const handleParamChange = (p?: number, s?: number, l?: number) => {
    const newValue = { ...value }
    if (p !== undefined) newValue.precision = p
    if (s !== undefined) newValue.scale = s
    if (l !== undefined) newValue.length = l
    onChange(newValue)
  }

  const handleItemMouseEnter = (type: string) => {
    if (leaveTimerRef.current) {
      clearTimeout(leaveTimerRef.current)
      leaveTimerRef.current = null
    }
    setHoveredType(type)
  }

  const handleItemMouseLeave = () => {
    leaveTimerRef.current = window.setTimeout(() => {
      if (!isHoveringCard) {
        setHoveredType(null)
      }
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
    setHoveredType(null)
  }

  const rawLabel = currentConfig
    ? currentConfig.hasParams === 'decimal'
      ? `${currentConfig.label}(${value.precision ?? 10},${value.scale ?? 2})`
      : currentConfig.hasParams === 'length'
        ? `${currentConfig.label}(${value.length ?? DEFAULT_LENGTH})`
        : currentConfig.label
    : value.type
  const isPlaceholder = !rawLabel && !!placeholder
  const displayLabel = isPlaceholder ? placeholder : rawLabel

  const IconComponent = currentConfig?.icon ?? Type

  const activeHoveredType = hoveredType || (isHoveringCard ? value.type : null)
  const showParamCard = open && paramCardPos && activeHoveredType &&
    DATA_TYPE_CONFIGS.find((c) => c.type === activeHoveredType)?.hasParams

  const dropdownStyle = dropdownPos
    ? {
        top: dropdownPos.top,
        left: dropdownPos.left,
        ...(typeof dropdownPos.width === 'number' ? { width: dropdownPos.width } : {})
      }
    : undefined

  return (
    <>
      <Button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen(!open)}
        title={displayLabel}
        variant="outline"
        size={isSmall ? 'small' : 'normal'}
        className={`flex items-center min-w-0 max-w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-600 transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
          isSmall ? 'gap-1.5 px-2 py-1 rounded-lg' : 'gap-2 px-3 py-2 rounded-xl'
        } ${triggerClassName ?? ''}`}
      >
        <IconComponent size={triggerIconSize} className="text-slate-500" />
        <span
          className={`flex-1 min-w-0 truncate font-medium ${
            isSmall ? 'text-xs' : 'text-body-sm'
          } ${labelClassName ?? ''} ${isPlaceholder ? 'text-slate-400 dark:text-slate-500' : 'text-slate-700 dark:text-slate-200'}`}
        >
          {displayLabel}
        </span>
        <ChevronDown
          size={triggerChevronSize}
          className={`text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </Button>

      {open &&
        dropdownPos &&
        createPortal(
          <div
            ref={dropdownRef}
            style={dropdownStyle}
            className={`fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 ${dropdownRadiusClass} ${dropdownClassName ?? ''}`}
          >
            <div
              className={`overflow-y-auto overscroll-contain custom-scrollbar ${dropdownListClass} ${dropdownListClassName ?? ''}`}
              style={{ scrollbarWidth: 'thin' }}
              onWheel={(e) => e.stopPropagation()}
            >
              {filteredTypes.map((config) => {
                const isSelected = value.type === config.type
                const Icon = config.icon
                return (
                  <Button
                    key={config.type}
                    ref={hoveredType === config.type ? hoverItemRef : undefined}
                    type="button"
                    onClick={() => handleSelect(config)}
                    onMouseEnter={() => handleItemMouseEnter(config.type)}
                    onMouseLeave={handleItemMouseLeave}
                    variant="ghost"
                    size={isSmall ? 'small' : 'normal'}
                    className={`w-full flex items-center text-left transition-all ${itemSizeClass} ${
                      isSelected
                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                        : 'hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300'
                    } ${optionClassName ?? ''}`}
                  >
                    <Icon size={itemIconSize} className={isSelected ? 'text-blue-500' : 'text-slate-400'} />
                    <span className={`flex-1 font-medium ${itemTextClass}`}>{config.label}</span>
                    {config.hasParams && (
                      <ChevronDown size={itemChevronSize} className="-rotate-90 text-slate-400" />
                    )}
                    {isSelected && <Check size={itemCheckSize} className="text-blue-500" />}
                  </Button>
                )
              })}
            </div>
          </div>,
          document.body
        )}

      {showParamCard &&
        createPortal(
          <div
            ref={paramCardRef}
            style={{ top: paramCardPos.top, left: paramCardPos.left }}
            className="fixed z-[1000001]"
            onMouseEnter={handleCardMouseEnter}
            onMouseLeave={handleCardMouseLeave}
          >
            {activeHoveredType === 'DECIMAL' && (
              <DecimalPrecisionCard
                precision={value.type === 'DECIMAL' ? (value.precision ?? 10) : 10}
                scale={value.type === 'DECIMAL' ? (value.scale ?? 2) : 2}
                onChange={(p, s) => {
                  if (value.type !== 'DECIMAL') {
                    onChange({ type: 'DECIMAL', precision: p, scale: s })
                  } else {
                    handleParamChange(p, s)
                  }
                }}
              />
            )}
            {(activeHoveredType === 'VARCHAR' || activeHoveredType === 'FIXEDCHAR' || activeHoveredType === 'FIXED') && (
              <LengthCard
                length={value.type === activeHoveredType ? (value.length ?? DEFAULT_LENGTH) : DEFAULT_LENGTH}
                min={DEFAULT_LENGTH}
                max={getMaxLengthForType(activeHoveredType)}
                onChange={(l) => {
                  if (value.type !== activeHoveredType) {
                    onChange({ type: activeHoveredType, length: l })
                  } else {
                    handleParamChange(undefined, undefined, l)
                  }
                }}
              />
            )}
          </div>,
          document.body
        )}
    </>
  )
}
