import { useState, useRef, useEffect, useLayoutEffect, useCallback, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Code, X, Loader2, ChevronDown, Check, Sparkles, BookOpen, Target, Scale } from 'lucide-react'
import type { RefObject, Dispatch, SetStateAction } from 'react'
import type { MetricFormData } from './types'
import { DataTypeSelector, InfiniteSelect, type DataTypeValue, type InfiniteSelectItem } from '@/components/ui'
import { fetchModifiers, fetchWordRoots, fetchMetrics, fetchUnits, type MetricModifierDTO } from '@/services/oneMetaSemanticService'

interface MetricFormLeftProps {
  form: MetricFormData
  setForm: Dispatch<SetStateAction<MetricFormData>>
  isEditMode?: boolean
  units: Array<{ symbol: string; name: string; code: string }>
  modifiers: Array<{ symbol: string; name: string; code: string }>
  wordRoots: Array<{ code: string; name: string }>
  atomicMetrics: Array<{ code: string; name: string }>
  modifierTotal?: number
  atomicTotal?: number
  loadUnits: () => Promise<unknown>
  loadModifiers: () => Promise<MetricModifierDTO[]>
  loadWordRoots: () => Promise<unknown>
  loadAtomicMetrics: () => Promise<Array<{ code: string; name: string }>>
  updateDerivedCode: (baseCode: string, modifiers: string[]) => void
  updateDerivedCustomSuffix: (customSuffix: string) => void
  addWordRoot: (rootCode: string) => void
  removeWordRoot: (rootCode: string) => void
  updateCustomSuffix: (customSuffix: string) => void
  updateAggregation: (aggregation: string) => void
  formulaRef: RefObject<HTMLTextAreaElement>
}

const PAGE_SIZE = 8

/** 单位选择器 - 支持无限滚动 */
function UnitSelector({
  value,
  unitName,
  unitSymbol,
  onChange
}: {
  value: string
  unitName?: string
  unitSymbol?: string
  onChange: (code: string, name: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [units, setUnits] = useState<Array<{ symbol: string; name: string; code: string }>>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialized, setInitialized] = useState(false)

  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)

  const hasMore = units.length < total

  // 加载首页数据
  const loadInitial = useCallback(async () => {
    if (initialized || loading) return
    setLoading(true)
    try {
      const data = await fetchUnits(0, PAGE_SIZE)
      setUnits(data.items.map((u) => ({ symbol: u.symbol || u.code[0], name: u.name, code: u.code })))
      setTotal(data.total)
      setInitialized(true)
    } catch {
      setUnits([])
    } finally {
      setLoading(false)
    }
  }, [initialized, loading])

  // 加载更多
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const data = await fetchUnits(units.length, PAGE_SIZE)
      setUnits((prev) => [...prev, ...data.items.map((u) => ({ symbol: u.symbol || u.code[0], name: u.name, code: u.code }))])
      setTotal(data.total)
    } catch {
      // 加载失败
    } finally {
      setLoadingMore(false)
    }
  }, [loadingMore, hasMore, units.length])

  const handleOpen = () => {
    if (!open) {
      loadInitial()
    }
    setOpen(!open)
  }

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
    if (!open) {
      setDropdownPos(null)
      return
    }
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

  // 滚动加载更多
  useEffect(() => {
    if (!open || !listRef.current) return
    const list = listRef.current
    const handleScroll = () => {
      if (list.scrollTop + list.clientHeight >= list.scrollHeight - 20) {
        loadMore()
      }
    }
    list.addEventListener('scroll', handleScroll)
    return () => list.removeEventListener('scroll', handleScroll)
  }, [open, loadMore])

  const selectedUnit = units.find((u) => u.code === value)
  const displayLabel = selectedUnit
    ? `${selectedUnit.symbol} ${selectedUnit.name}`
    : (unitName ? `${unitSymbol || ''} ${unitName}`.trim() : (value || '请选择单位'))

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={handleOpen}
        className={`w-full flex items-center justify-between bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm focus:outline-none focus:border-blue-500 transition-all ${!value ? 'text-slate-400' : 'text-slate-800 dark:text-slate-200'}`}
      >
        <span className="truncate">{displayLabel}</span>
        <ChevronDown size={14} className={`text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && dropdownPos && createPortal(
        <div
          ref={dropdownRef}
          style={{
            '--unit-dropdown-top': `${dropdownPos.top}px`,
            '--unit-dropdown-left': `${dropdownPos.left}px`,
            '--unit-dropdown-width': `${dropdownPos.width}px`
          } as React.CSSProperties}
          className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl top-[var(--unit-dropdown-top)] left-[var(--unit-dropdown-left)] w-[var(--unit-dropdown-width)]"
        >
          <div ref={listRef} className="max-h-60 overflow-y-auto p-1">
            {loading ? (
              <div className="flex items-center justify-center py-4">
                <Loader2 size={16} className="animate-spin text-slate-400" />
              </div>
            ) : units.length === 0 ? (
              <div className="py-4 text-center text-caption text-slate-400">暂无单位数据</div>
            ) : (
              <>
                {units.map((unit) => (
                  <button
                    key={unit.code}
                    type="button"
                    onClick={() => {
                      onChange(unit.code, unit.name)
                      setOpen(false)
                    }}
                    className={`w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg text-left transition-all ${
                      value === unit.code
                        ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                        : 'hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                  >
                    <span className="flex items-center gap-1.5">
                      <Scale size={10} className="text-amber-400" />
                      <span className="text-caption text-slate-500 font-mono">{unit.code}</span>
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="text-caption text-slate-600 dark:text-slate-400">{unit.name}</span>
                      {value === unit.code ? <Check size={12} className="text-blue-500" /> : <span className="w-3" />}
                    </span>
                  </button>
                ))}
                {loadingMore && (
                  <div className="flex items-center justify-center py-2">
                    <Loader2 size={14} className="animate-spin text-slate-400" />
                  </div>
                )}
                {!hasMore && units.length > PAGE_SIZE && (
                  <div className="py-1.5 text-center text-micro text-slate-300">已加载全部</div>
                )}
              </>
            )}
          </div>
        </div>,
        document.body
      )}
    </>
  )
}

/** 将 form 中的 dataType/precision/scale 转为 DataTypeValue */
function toDataTypeValue(form: MetricFormData): DataTypeValue {
  if (form.dataType === 'DECIMAL') {
    return { type: 'DECIMAL', precision: form.precision || 10, scale: form.scale || 2 }
  }
  return { type: form.dataType || '' }
}

/** 将 DataTypeValue 更新回 form */
function fromDataTypeValue(value: DataTypeValue): Partial<MetricFormData> {
  if (value.type === 'DECIMAL') {
    return { dataType: 'DECIMAL', precision: value.precision ?? 10, scale: value.scale ?? 2 }
  }
  return { dataType: value.type, precision: 10, scale: 2 }
}

export function MetricFormLeft({
  form,
  setForm,
  isEditMode = false,
  units: _units,
  modifiers: _modifiers,
  wordRoots: _wordRoots,
  atomicMetrics: _atomicMetrics,
  modifierTotal: _modifierTotal,
  atomicTotal: _atomicTotal,
  loadUnits: _loadUnits,
  loadModifiers: _loadModifiers,
  loadWordRoots: _loadWordRoots,
  loadAtomicMetrics: _loadAtomicMetrics,
  updateDerivedCode,
  updateDerivedCustomSuffix,
  addWordRoot,
  removeWordRoot,
  updateCustomSuffix,
  updateAggregation,
  formulaRef
}: MetricFormLeftProps) {
  // 修饰符 fetchData 适配器
  const fetchModifierData = useCallback(async (offset: number, limit: number): Promise<{ items: InfiniteSelectItem[]; total: number }> => {
    // 优先使用父级已缓存的数据，避免重复请求导致闪烁
    if (offset === 0) {
      if (_modifiers.length === 0) {
        const loaded = await _loadModifiers().catch(() => [] as MetricModifierDTO[])
        if (loaded.length > 0) {
          return {
            items: loaded.map((m) => ({
              key: m.code,
              code: m.code,
              name: m.name,
              icon: <Sparkles size={10} />
            })),
            total: _modifierTotal || loaded.length
          }
        }
      } else {
        return {
          items: _modifiers.map((m) => ({
            key: m.code,
            code: m.code,
            name: m.name,
            icon: <Sparkles size={10} />
          })),
          total: _modifierTotal || _modifiers.length
        }
      }
    }

    const data = await fetchModifiers(offset, limit).catch(() => ({ items: [], total: 0 }))
    return {
      items: data.items.map((m) => ({
        key: m.code,
        code: m.code,
        name: m.name,
        icon: <Sparkles size={10} />
      })),
      total: data.total || data.items.length
    }
  }, [_modifiers, _modifierTotal, _loadModifiers])

  // 词根 fetchData 适配器
  const fetchWordRootData = useCallback(async (offset: number, limit: number): Promise<{ items: InfiniteSelectItem[]; total: number }> => {
    const data = await fetchWordRoots(offset, limit).catch(() => ({ items: [], total: 0 }))
    return {
      items: data.items.map((r) => ({
        key: r.code,
        code: r.code,
        name: r.name,
        icon: <BookOpen size={10} />
      })),
      total: data.total
    }
  }, [])

  // 原子指标 fetchData 适配器
  const fetchAtomicMetricData = useCallback(async (offset: number, limit: number): Promise<{ items: InfiniteSelectItem[]; total: number }> => {
    // 先用父级缓存，避免每次挂载时重新请求导致下拉闪烁
    if (offset === 0) {
      if (_atomicMetrics.length === 0) {
        const loaded = await _loadAtomicMetrics().catch(() => [] as Array<{ code: string; name: string }>)
        if (loaded.length > 0) {
          return {
            items: loaded.map((m) => ({
              key: m.code,
              code: m.code,
              name: m.name,
              icon: <Target size={10} />
            })),
            total: _atomicTotal || loaded.length
          }
        }
      } else {
        return {
          items: _atomicMetrics.map((m) => ({
            key: m.code,
            code: m.code,
            name: m.name,
            icon: <Target size={10} />
          })),
          total: _atomicTotal || _atomicMetrics.length
        }
      }
    }

    const data = await fetchMetrics(offset, limit).catch(() => ({ items: [], total: 0 }))
    const atomics = data.items.filter((m) => m.type.toUpperCase() === 'ATOMIC')
    return {
      items: atomics.map((m) => ({
        key: m.code,
        code: m.code,
        name: m.name,
        icon: <Target size={10} />
      })),
      total: data.total || atomics.length
    }
  }, [_atomicMetrics, _atomicTotal, _loadAtomicMetrics])

  // 聚合函数选项
  const AGGREGATIONS = ['SUM', 'AVG', 'COUNT', 'MAX', 'MIN', 'DISTINCT_COUNT']
  const [aggOpen, setAggOpen] = useState(false)
  const aggInputRef = useRef<HTMLInputElement>(null)
  const aggDropdownRef = useRef<HTMLDivElement>(null)
  const [aggDropdownPos, setAggDropdownPos] = useState<{ top: number; left: number } | null>(null)

  // 聚合函数下拉位置计算
  useLayoutEffect(() => {
    if (!aggOpen) return
    const updatePosition = () => {
      const input = aggInputRef.current
      if (!input) return
      const rect = input.getBoundingClientRect()
      setAggDropdownPos({ top: rect.bottom + 4, left: rect.left })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      setAggDropdownPos(null)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [aggOpen])

  // 聚合函数点击外部关闭
  useEffect(() => {
    if (!aggOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (aggInputRef.current?.contains(target)) return
      if (aggDropdownRef.current?.contains(target)) return
      setAggOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [aggOpen])

  // 过滤匹配的聚合函数
  const filteredAggregations = AGGREGATIONS.filter((agg) =>
    agg.toLowerCase().includes(form.aggregation.toLowerCase())
  )

  return (
    <div className="col-span-5 flex flex-col gap-4 h-full">
      <div className="flex-1 min-h-0 flex flex-col gap-4">
        <div className="space-y-1.5">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">指标名称 *</label>
          <input
            type="text"
            placeholder="例如：累计订单金额"
            className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-blue-500 transition-all"
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
          />
        </div>

        {/* 指标编码区域 */}
        <div className="space-y-2">
          <div className="flex items-center justify-between h-5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">指标编码 *</label>
            <span className={`font-mono text-caption text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded ${!isEditMode && form.code ? 'visible' : 'invisible'}`}>
              {form.code || '-'}
            </span>
          </div>
          {isEditMode ? (
            <div className="flex items-center p-3 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-200 dark:border-purple-800 rounded-xl">
              <span className="text-body-sm font-mono font-semibold text-purple-600 dark:text-purple-400 tracking-wide">{form.code}</span>
            </div>
          ) : (
          <div className="flex items-center h-12 px-3 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-xl overflow-x-auto">
            {form.type === 'DERIVED' ? (
              <>
                <div className="flex items-center gap-1">
                  <div className={form.baseCode ? 'hidden' : 'flex'}>
                    <InfiniteSelect
                      placeholder="选择原子指标"
                      variant="slate"
                      initialItems={_atomicMetrics.map((m) => ({
                        key: m.code,
                        code: m.code,
                        name: m.name,
                        icon: <Target size={10} />
                      }))}
                      initialTotal={_atomicTotal || _atomicMetrics.length}
                      fetchData={fetchAtomicMetricData}
                      onSelect={(item) => updateDerivedCode(item.code, form.modifiers)}
                    />
                  </div>
                  {form.baseCode && (
                    <span className="shrink-0 inline-flex items-center text-caption font-mono text-slate-700 dark:text-slate-300 border-b border-dashed border-slate-300 px-1 py-0.5">
                      {form.baseCode}
                      <button type="button" onClick={() => updateDerivedCode('', form.modifiers)} className="text-slate-400 hover:text-red-500 ml-0.5"><X size={10} /></button>
                    </span>
                  )}
                </div>
                {form.modifiers.map((mod) => (
                  <span key={mod} className="shrink-0 inline-flex items-center text-caption font-mono text-blue-600 dark:text-blue-400">
                    <span className="text-slate-400 mx-1">_</span>
                    {mod}
                    <button type="button" onClick={() => updateDerivedCode(form.baseCode, form.modifiers.filter((m) => m !== mod))} className="text-slate-400 hover:text-red-500 ml-0.5"><X size={10} /></button>
                  </span>
                ))}
                <div className={form.modifiers.length < 2 ? 'flex items-center' : 'hidden'}>
                  <span className="text-slate-400 mx-1">_</span>
                  <InfiniteSelect
                    placeholder="选择修饰符"
                    variant="blue"
                    selectedKeys={form.modifiers}
                     initialItems={_modifiers.map((m) => ({
                      key: m.code,
                      code: m.code,
                      name: m.name,
                      icon: <Sparkles size={10} />
                    }))}
                    initialTotal={_modifierTotal || _modifiers.length}
                    fetchData={fetchModifierData}
                    onSelect={(item) => {
                      if (!form.modifiers.includes(item.code)) {
                        updateDerivedCode(form.baseCode, [...form.modifiers, item.code])
                      }
                    }}
                  />
                </div>
                {/* 自定义后缀 - 追加在最后 */}
                <div className="flex items-center">
                  <span className="text-slate-400 mx-1">_</span>
                  <input
                    type="text"
                    placeholder="自定义后缀"
                    className="w-20 shrink-0 bg-transparent border-b border-dashed border-slate-400 px-1 py-0.5 text-caption font-mono uppercase placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-blue-500"
                    value={form.customSuffix}
                    onChange={(e) => updateDerivedCustomSuffix(e.target.value.toUpperCase())}
                  />
                </div>
              </>
            ) : (
              <>
                {(() => {
                  const segments: ReactNode[] = []
                  const addSeg = (node: ReactNode, key: string) => {
                    if (segments.length > 0) {
                      segments.push(<span key={`${key}-sep`} className="inline-flex items-center text-slate-400 mx-1">_</span>)
                    }
                    segments.push(<span key={key}>{node}</span>)
                  }

                  form.wordRoots.forEach((root) =>
                    addSeg(
                      (
                        <span className="shrink-0 inline-flex items-center text-caption font-mono text-purple-600 dark:text-purple-400 border-b border-dashed border-slate-300 px-1 py-0.5">
                          {root}
                          <button type="button" onClick={() => removeWordRoot(root)} className="text-slate-400 hover:text-red-500 ml-0.5"><X size={10} /></button>
                        </span>
                      ),
                      `root-${root}`
                    )
                  )

                  if (form.wordRoots.length < 2) {
                    addSeg(
                      (
                        <InfiniteSelect
                          placeholder="选择词根"
                          variant="purple"
                          selectedKeys={form.wordRoots}
                          fetchData={fetchWordRootData}
                          onSelect={(item) => addWordRoot(item.code)}
                        />
                      ),
                      'select-root'
                    )
                  }

                  addSeg(
                    (
                      <div className="relative">
                        <input
                          ref={aggInputRef}
                          type="text"
                          placeholder="聚合函数"
                          className="w-20 shrink-0 bg-transparent border-b border-dashed border-emerald-400 px-1 py-0.5 text-caption font-mono uppercase text-emerald-600 placeholder:text-emerald-400 dark:placeholder:text-emerald-600 focus:outline-none focus:border-emerald-500"
                          value={form.aggregation}
                          onChange={(e) => updateAggregation(e.target.value.toUpperCase())}
                          onFocus={() => setAggOpen(true)}
                        />
                        {aggOpen && aggDropdownPos && filteredAggregations.length > 0 && createPortal(
                          <div
                            ref={aggDropdownRef}
                            style={{ top: aggDropdownPos.top, left: aggDropdownPos.left }}
                            className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl min-w-24"
                          >
                            <div className="max-h-48 overflow-y-auto p-1">
                              {filteredAggregations.map((agg) => (
                                <button
                                  key={agg}
                                  type="button"
                                  onClick={() => {
                                    updateAggregation(agg)
                                    setAggOpen(false)
                                  }}
                                  className="w-full flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-left transition-all hover:bg-slate-50 dark:hover:bg-slate-800"
                                >
                                  <span className="text-caption text-emerald-600 font-mono">{agg}</span>
                                </button>
                              ))}
                            </div>
                          </div>,
                          document.body
                        )}
                      </div>
                    ),
                    'agg'
                  )

                  // 自定义后缀 - 追加在最后
                  addSeg(
                    (
                      <input
                        type="text"
                        placeholder="自定义后缀"
                        className="w-20 shrink-0 bg-transparent border-b border-dashed border-slate-400 px-1 py-0.5 text-caption font-mono uppercase placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-blue-500"
                        value={form.customSuffix}
                        onChange={(e) => updateCustomSuffix(e.target.value.toUpperCase())}
                      />
                    ),
                    'suffix'
                  )

                  return segments
                })()}
              </>
            )}
          </div>
          )}
        </div>

        <div className="grid grid-cols-[200px_1fr] gap-3">
          <div className="space-y-1.5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">数据类型</label>
            <DataTypeSelector
              value={toDataTypeValue(form)}
              onChange={(value) => setForm((prev) => ({ ...prev, ...fromDataTypeValue(value) }))}
              filter="numeric"
              triggerClassName="w-full !border-2 !border-slate-100 dark:!border-slate-700 !py-2.5 !px-4"
            />
          </div>
          <div className="space-y-1.5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">单位</label>
            <UnitSelector
              value={form.unit}
              unitName={form.unitName}
              unitSymbol={form.unitSymbol}
              onChange={(code, name) => setForm((prev) => ({ ...prev, unit: code, unitName: name }))}
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5 flex-1 min-h-0">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">业务描述</label>
          <textarea
            placeholder="描述该指标的业务含义..."
            className="w-full flex-1 min-h-[60px] bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-blue-500 transition-all resize-none"
            value={form.comment}
            onChange={(e) => setForm((prev) => ({ ...prev, comment: e.target.value }))}
          />
        </div>

        {/* 公式表达式 */}
        <div className="flex flex-col gap-1.5 flex-1 min-h-0">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
            <Code size={14} className="text-emerald-500" /> 公式表达式 *
          </label>
          <div className="relative flex-1 min-h-[60px]">
            <Code size={14} className="absolute top-3.5 left-4 text-emerald-500/50 pointer-events-none" />
            <textarea
              ref={formulaRef}
              placeholder={
                form.type === 'ATOMIC'
                  ? "SUM(orders.amount) WHERE status = 'paid'"
                  : form.type === 'DERIVED'
                    ? "{SALES_AMOUNT} WHERE region = '北京'"
                    : '({SALES_AMOUNT} - {COST}) / {SALES_AMOUNT} * 100'
              }
              className="w-full h-full bg-slate-900 text-emerald-400 font-mono border-2 border-slate-100 dark:border-slate-700 rounded-xl pl-10 pr-4 py-3 text-body-sm focus:outline-none focus:border-emerald-500 shadow-lg resize-none"
              value={form.formula}
              onChange={(e) => setForm((prev) => ({ ...prev, formula: e.target.value }))}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
