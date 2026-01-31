/**
 * 自定义 Select 组件 - 支持深色模式
 */

import { useState, useRef, useEffect, useLayoutEffect, useMemo } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown, Check } from 'lucide-react'
import { Button } from './Button'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectProps {
  value: string
  options: SelectOption[]
  onChange: (value: string) => void
  placeholder?: string
  /** 下拉卡片头部提示文字 */
  dropdownHeader?: string
  disabled?: boolean
  size?: 'md' | 'sm' | 'xs'
  className?: string
}

export function Select({
  value,
  options,
  onChange,
  placeholder = '请选择',
  dropdownHeader,
  disabled = false,
  size = 'md',
  className = ''
}: SelectProps) {
  const [open, setOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const isSmall = size === 'sm'
  const isExtraSmall = size === 'xs'
  const triggerSizeClass = isExtraSmall
    ? 'px-2 py-1.5 text-caption rounded-lg border-slate-300 dark:border-slate-600'
    : isSmall
      ? 'px-3 py-2 text-body-sm rounded-xl border-slate-300 dark:border-slate-600'
      : 'px-3 py-1.5 text-sm rounded-md border-slate-200 dark:border-slate-700'
  const optionSizeClass = isExtraSmall
    ? 'px-2 py-1.5 text-caption'
    : isSmall
      ? 'px-3 py-2 text-body-sm'
      : 'px-3 py-2 text-sm'
  const headerTextClass = isExtraSmall ? 'text-micro' : isSmall ? 'text-caption' : 'text-xs'
  const iconSize = isExtraSmall ? 12 : isSmall ? 12 : 14

  const selectedOption = options.find((opt) => opt.value === value)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (triggerRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  useLayoutEffect(() => {
    if (!open) return
    const updatePosition = () => {
      const btn = triggerRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      setDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [open])

  // 计算下拉框样式
  const dropdownStyle = useMemo(() => {
    if (!dropdownPos) return undefined
    return {
      '--dropdown-top': `${dropdownPos.top}px`,
      '--dropdown-left': `${dropdownPos.left}px`,
      '--dropdown-width': `${dropdownPos.width}px`
    } as React.CSSProperties
  }, [dropdownPos])

  return (
    <>
      <Button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={() => setOpen(!open)}
        variant="outline"
        size={isExtraSmall ? 'tiny' : 'small'}
        className={`w-full flex items-center justify-between bg-white dark:bg-slate-900 border focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed transition-all ${triggerSizeClass} ${className}`}
      >
        <span className={`truncate ${selectedOption ? 'text-slate-800 dark:text-slate-200' : 'text-slate-400'}`}>
          {selectedOption?.label || placeholder}
        </span>
        <ChevronDown size={iconSize} className={`text-slate-400 transition-transform flex-shrink-0 ml-2 ${open ? 'rotate-180' : ''}`} />
      </Button>

      {open && dropdownPos && createPortal(
        <div
          ref={dropdownRef}
          style={dropdownStyle}
          className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-xl overflow-hidden top-[var(--dropdown-top)] left-[var(--dropdown-left)] w-[var(--dropdown-width)]"
        >
          {dropdownHeader && (
            <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900">
              <span className={`${headerTextClass} font-medium text-slate-500 dark:text-slate-400`}>{dropdownHeader}</span>
            </div>
          )}
          <div className="max-h-48 overflow-y-auto py-1 custom-scrollbar">
            {options.map((option) => {
              const isSelected = value === option.value
              return (
                <Button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    onChange(option.value)
                    setOpen(false)
                  }}
                  variant="ghost"
                  size={isExtraSmall ? 'tiny' : isSmall ? 'small' : 'small'}
                  className={`w-full flex items-center justify-between text-left transition-colors ${optionSizeClass} ${
                    isSelected
                      ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                      : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                  }`}
                >
                  <span>{option.label}</span>
                  {isSelected && <Check size={14} className="text-indigo-500 flex-shrink-0" />}
                </Button>
              )
            })}
          </div>
        </div>,
        document.body
      )}
    </>
  )
}
