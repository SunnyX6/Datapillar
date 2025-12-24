import { useState, useEffect, useRef, useCallback, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import { Database, Box, Hash, ChevronRight, Loader2, ArrowRight, Search, Filter, Target } from 'lucide-react'
import { fetchCatalogs, fetchSchemas, fetchTables, getTable, fetchMetrics } from '@/services/oneMetaService'
import type { MetricFormData, MeasureColumn, FilterColumn } from './types'

/** 模拟值域数据 */
const MOCK_VALUE_DOMAINS: Record<string, Array<{ key: string; label: string }>> = {
  status: [
    { key: 'pending', label: '待处理' },
    { key: 'paid', label: '已支付' },
    { key: 'shipped', label: '已发货' },
    { key: 'completed', label: '已完成' },
    { key: 'cancelled', label: '已取消' }
  ],
  type: [
    { key: 'normal', label: '普通' },
    { key: 'vip', label: 'VIP' },
    { key: 'enterprise', label: '企业' }
  ],
  category: [
    { key: 'electronics', label: '电子产品' },
    { key: 'clothing', label: '服装' },
    { key: 'food', label: '食品' }
  ]
}

function getColumnValueDomain(columnName: string): Array<{ key: string; label: string }> {
  const lowerName = columnName.toLowerCase()
  for (const [key, values] of Object.entries(MOCK_VALUE_DOMAINS)) {
    if (lowerName.includes(key)) {
      return values
    }
  }
  return [
    { key: 'value1', label: '值1' },
    { key: 'value2', label: '值2' },
    { key: 'value3', label: '值3' }
  ]
}

function ColumnTableRow({ colInfo, isMeasure, isFilter, filterValues, onMeasureToggle, onFilterToggle }: {
  colInfo: { name: string; type: string; comment?: string }
  isMeasure: boolean
  isFilter: boolean
  filterValues: Array<{ key: string; label: string }>
  onMeasureToggle: () => void
  onFilterToggle: (values: Array<{ key: string; label: string }>) => void
}) {
  const [filterOpen, setFilterOpen] = useState(false)
  const filterButtonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null)
  const valueDomain = getColumnValueDomain(colInfo.name)

  const closeDropdown = () => {
    setFilterOpen(false)
    setDropdownPos(null)
  }

  useEffect(() => {
    if (!filterOpen) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (filterButtonRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      closeDropdown()
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [filterOpen])

  useLayoutEffect(() => {
    if (!filterOpen) return
    const DROPDOWN_WIDTH = 192
    const updatePosition = () => {
      const btn = filterButtonRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const left = Math.max(12, rect.right - DROPDOWN_WIDTH)
      const top = rect.top // 用 transform 向上移动
      setDropdownPos({ top, left })
    }
    updatePosition()
    const handleScroll = () => updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', handleScroll, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', handleScroll, true)
    }
  }, [filterOpen])
  const toggleValue = (val: { key: string; label: string }) => {
    const exists = filterValues.some((v) => v.key === val.key)
    if (exists) {
      onFilterToggle(filterValues.filter((v) => v.key !== val.key))
    } else {
      onFilterToggle([...filterValues, val])
    }
  }

  const isSelected = isMeasure || isFilter

  return (
    <tr className={`border-b transition-all ${
      isSelected
        ? 'bg-blue-50/50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800'
        : 'border-slate-100 dark:border-slate-800 hover:bg-white dark:hover:bg-slate-900 hover:shadow-sm'
    }`}>
      <td className="px-4 py-2.5" title={colInfo.comment || ''}>
        <div className={`text-body-sm font-medium truncate ${
          isSelected ? 'text-blue-700 dark:text-blue-300' : 'text-slate-800 dark:text-slate-200'
        }`}>{colInfo.name}</div>
      </td>
      <td className="px-4 py-2.5">
        <span className="text-caption font-mono text-slate-500">{colInfo.type}</span>
      </td>
      <td className="px-4 py-2.5 w-20">
        <div className="flex items-center justify-center gap-1">
          <button
            type="button"
            onClick={onMeasureToggle}
            title={isMeasure ? '取消度量' : '设为度量列'}
            className={`p-1 rounded transition-all ${
              isMeasure
                ? 'bg-blue-500 text-white'
                : 'text-slate-400 hover:bg-blue-50 hover:text-blue-500 dark:hover:bg-blue-900/30'
            }`}
          >
            <Target size={14} />
          </button>

          <div className="relative">
            <button
              type="button"
              ref={filterButtonRef}
              onClick={() => {
                if (filterOpen) {
                  closeDropdown()
                } else {
                  setFilterOpen(true)
                }
              }}
              title={isFilter ? `已选${filterValues.length}个过滤值` : '设为过滤列'}
              className={`p-1 rounded transition-all ${
                isFilter
                  ? 'bg-amber-500 text-white'
                  : 'text-slate-400 hover:bg-amber-50 hover:text-amber-500 dark:hover:bg-amber-900/30'
              }`}
            >
              <Filter size={14} />
            </button>

            {filterOpen && dropdownPos && createPortal(
              <div
                ref={dropdownRef}
                style={{ top: dropdownPos.top, left: dropdownPos.left, transform: 'translateY(-100%)' }}
                className="fixed w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-[1000000]"
              >
                <div className="p-2 border-b border-slate-100 dark:border-slate-800">
                  <div className="text-micro font-semibold text-slate-500">选择过滤值</div>
                </div>
                <div className="max-h-48 overflow-y-auto custom-scrollbar">
                  {valueDomain.map((val) => {
                    const checked = filterValues.some((v) => v.key === val.key)
                    return (
                      <label
                        key={val.key}
                        className="flex items-center gap-2 px-3 py-2 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleValue(val)}
                          className="w-4 h-4 rounded border-slate-300 text-amber-500 focus:ring-amber-500"
                        />
                        <span className="text-caption text-slate-700 dark:text-slate-300">{val.label}</span>
                        <span className="text-micro text-slate-400 ml-auto">{val.key}</span>
                      </label>
                    )
                  })}
                </div>
                {filterValues.length > 0 && (
                  <div className="p-2 border-t border-slate-100 dark:border-slate-800">
                    <button
                      type="button"
                      onClick={() => onFilterToggle([])}
                      className="w-full text-caption text-red-500 hover:text-red-600"
                    >
                      清除全部
                    </button>
                  </div>
                )}
              </div>,
              document.body
            )}
          </div>
        </div>
      </td>
    </tr>
  )
}

function CascadingPicker({ onMeasureToggle, onFilterToggle, measureColumns, filterColumns }: {
  onMeasureToggle: (col: MeasureColumn, selected: boolean) => void
  onFilterToggle: (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => void
  measureColumns: MeasureColumn[]
  filterColumns: FilterColumn[]
}) {
  type Level = 'CATALOG' | 'SCHEMA' | 'TABLE' | 'COLUMN'

  const [catalogs, setCatalogs] = useState<string[]>([])
  const [schemas, setSchemas] = useState<string[]>([])
  const [tables, setTables] = useState<string[]>([])
  const [columns, setColumns] = useState<{ name: string; type: string; comment?: string }[]>([])

  const [selected, setSelected] = useState({ catalog: '', schema: '', table: '' })
  const [level, setLevel] = useState<Level>('CATALOG')
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchCatalogs()
      .then((data) => { if (!cancelled) setCatalogs(data.map((c) => c.name)) })
      .catch(() => { if (!cancelled) setCatalogs([]) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  const handleSelect = useCallback((item: string) => {
    setSearch('')
    if (level === 'CATALOG') {
      setSelected({ catalog: item, schema: '', table: '' })
      setSchemas([])
      setTables([])
      setColumns([])
      setLoading(true)
      fetchSchemas(item)
        .then((data) => setSchemas(data.map((s) => s.name)))
        .catch(() => setSchemas([]))
        .finally(() => setLoading(false))
      setLevel('SCHEMA')
    } else if (level === 'SCHEMA') {
      setSelected((p) => ({ ...p, schema: item, table: '' }))
      setTables([])
      setColumns([])
      setLoading(true)
      fetchTables(selected.catalog, item)
        .then((data) => setTables(data.map((t) => t.name)))
        .catch(() => setTables([]))
        .finally(() => setLoading(false))
      setLevel('TABLE')
    } else if (level === 'TABLE') {
      setSelected((p) => ({ ...p, table: item }))
      setColumns([])
      setLoading(true)
      getTable(selected.catalog, selected.schema, item)
        .then((data) => setColumns(data.columns?.map((c) => ({ name: c.name, type: c.type, comment: c.comment })) || []))
        .catch(() => setColumns([]))
        .finally(() => setLoading(false))
      setLevel('COLUMN')
    }
  }, [level, selected])

  const goBack = useCallback((targetLevel: Level) => {
    setSearch('')
    setLevel(targetLevel)

    if (targetLevel === 'CATALOG') {
      setLoading(true)
      fetchCatalogs()
        .then((data) => setCatalogs(data.map((c) => c.name)))
        .catch(() => setCatalogs([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'SCHEMA' && selected.catalog) {
      setLoading(true)
      fetchSchemas(selected.catalog)
        .then((data) => setSchemas(data.map((s) => s.name)))
        .catch(() => setSchemas([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'TABLE' && selected.catalog && selected.schema) {
      setLoading(true)
      fetchTables(selected.catalog, selected.schema)
        .then((data) => setTables(data.map((t) => t.name)))
        .catch(() => setTables([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'COLUMN' && selected.catalog && selected.schema && selected.table) {
      setLoading(true)
      getTable(selected.catalog, selected.schema, selected.table)
        .then((data) => setColumns(data.columns?.map((c) => ({ name: c.name, type: c.type, comment: c.comment })) || []))
        .catch(() => setColumns([]))
        .finally(() => setLoading(false))
    }
  }, [selected])

  const levels: { id: Level; label: string; icon: typeof Database; value: string }[] = [
    { id: 'CATALOG', label: '数据源', icon: Database, value: selected.catalog },
    { id: 'SCHEMA', label: '数据库', icon: Box, value: selected.schema },
    { id: 'TABLE', label: '物理表', icon: Box, value: selected.table },
    { id: 'COLUMN', label: '字段', icon: Hash, value: '' }
  ]

  const currentData = level === 'CATALOG' ? catalogs : level === 'SCHEMA' ? schemas : level === 'TABLE' ? tables : columns.map((c) => c.name)
  const filteredData = currentData.filter((item) => item.toLowerCase().includes(search.toLowerCase()))

  return (
    <div className="bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-slate-700 flex flex-col h-[620px]">
      <div className="flex items-center gap-1 p-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700 overflow-x-auto rounded-t-2xl">
        {levels.map((lvl, idx) => {
          const isActive = level === lvl.id
          const hasValue = lvl.value || (lvl.id === 'COLUMN' && level === 'COLUMN')
          const canClick = idx <= levels.findIndex((l) => l.id === level)
          return (
            <div key={lvl.id} className="flex items-center">
              <button
                disabled={!canClick}
                onClick={() => goBack(lvl.id)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-caption whitespace-nowrap transition-all ${
                  isActive
                    ? 'bg-blue-600 text-white font-semibold'
                    : hasValue
                      ? 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                      : 'text-slate-300 dark:text-slate-600 cursor-not-allowed'
                }`}
              >
                <lvl.icon size={12} />
                <span>{lvl.value || lvl.label}</span>
              </button>
              {idx < levels.length - 1 && <ChevronRight size={12} className="text-slate-300 dark:text-slate-600 mx-0.5" />}
            </div>
          )
        })}
      </div>

      <div className="p-3 border-b border-slate-100 dark:border-slate-800">
        <div className="relative">
          <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            placeholder={`搜索 ${levels.find((l) => l.id === level)?.label}...`}
            className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg pl-9 pr-4 py-2 text-caption placeholder:text-slate-400 focus:outline-none focus:border-blue-500"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 size={20} className="animate-spin text-slate-400" />
          </div>
        ) : filteredData.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-400">
            <Box size={24} className="opacity-20 mb-2" />
            <span className="text-caption">暂无数据</span>
          </div>
        ) : level === 'COLUMN' ? (
          <table className="w-full">
            <thead className="sticky top-0 bg-slate-100 dark:bg-slate-800 z-10">
              <tr className="text-micro font-semibold text-slate-500 uppercase">
                <th className="text-left px-4 py-2">列名</th>
                <th className="text-left px-4 py-2">类型</th>
                <th className="text-center px-4 py-2 w-20">操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredData.map((item) => {
                const colInfo = columns.find((c) => c.name === item)
                if (!colInfo) return null
                const isMeasure = measureColumns.some((c) => c.name === item)
                const filterCol = filterColumns.find((c) => c.name === item)
                const isFilter = !!filterCol

                return (
                  <ColumnTableRow
                    key={item}
                    colInfo={colInfo}
                    isMeasure={isMeasure}
                    isFilter={isFilter}
                    filterValues={filterCol?.values || []}
                    onMeasureToggle={() => {
                      const col: MeasureColumn = { name: colInfo.name, type: colInfo.type, comment: colInfo.comment }
                      onMeasureToggle(col, !isMeasure)
                    }}
                    onFilterToggle={(values) => {
                      const col: MeasureColumn = { name: colInfo.name, type: colInfo.type, comment: colInfo.comment }
                      if (values.length > 0) {
                        const filterCol: FilterColumn = { ...col, values }
                        onFilterToggle(col, true, filterCol)
                      } else {
                        onFilterToggle(col, false)
                      }
                    }}
                  />
                )
              })}
            </tbody>
          </table>
        ) : (
          <div className="p-3 space-y-1.5">
            {filteredData.map((item) => (
              <button
                key={item}
                onClick={() => handleSelect(item)}
                className="w-full flex items-center gap-3 p-3 rounded-xl border border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-blue-200 dark:hover:border-blue-700 hover:shadow-sm text-left transition-all group"
              >
                <div className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-400 group-hover:bg-blue-50 dark:group-hover:bg-blue-900/30 group-hover:text-blue-500">
                  <Box size={14} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-body-sm font-medium text-slate-800 dark:text-slate-200 truncate">{item}</div>
                </div>
                <ArrowRight size={14} className="text-slate-300 group-hover:text-blue-500" />
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function MetricPicker({ onInsert }: { onInsert: (text: string) => void }) {
  const [metrics, setMetrics] = useState<{ name: string; code: string; type: string }[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  useEffect(() => {
    let cancelled = false
    fetchMetrics(0, 100)
      .then((data) => { if (!cancelled) setMetrics(data.items.map((m) => ({ name: m.name, code: m.code, type: m.type }))) })
      .catch(() => { if (!cancelled) setMetrics([]) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  const filteredMetrics = metrics.filter(
    (m) => m.name.toLowerCase().includes(search.toLowerCase()) || m.code.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-slate-700 overflow-hidden flex flex-col h-[620px]">
      <div className="p-3 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-700">
        <div className="relative">
          <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索指标..."
            className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg pl-9 pr-4 py-2 text-caption placeholder:text-slate-400 focus:outline-none focus:border-blue-500"
          />
        </div>
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-1.5 custom-scrollbar">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 size={20} className="animate-spin text-slate-400" />
          </div>
        ) : filteredMetrics.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-400">
            <Target size={24} className="opacity-20 mb-2" />
            <span className="text-caption">暂无可用指标</span>
          </div>
        ) : (
          filteredMetrics.map((m) => (
            <button
              key={m.code}
              onClick={() => onInsert(`{${m.code}}`)}
              className="w-full flex items-center gap-3 p-3 rounded-xl border border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-blue-200 dark:hover:border-blue-700 hover:shadow-sm text-left transition-all"
            >
              <Target size={14} className="text-blue-500" />
              <div className="flex-1 min-w-0">
                <div className="text-body-sm font-medium text-slate-700 dark:text-slate-300">{m.name}</div>
                <div className="text-micro font-mono text-slate-400">{m.code}</div>
              </div>
            </button>
          ))
        )}
      </div>
    </div>
  )
}

interface MetricFormRightProps {
  form: MetricFormData
  onMeasureToggle: (col: MeasureColumn, selected: boolean) => void
  onFilterToggle: (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => void
  insertDerivedFormula: (text: string) => void
}

export function MetricFormRight({
  form,
  onMeasureToggle,
  onFilterToggle,
  insertDerivedFormula
}: MetricFormRightProps) {
  return (
    <div className="col-span-7 flex h-full min-h-0 flex-col">
      <div className="h-full min-h-0 flex flex-col gap-1.5">
        <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2 shrink-0">
          <Database size={14} className="text-blue-500" />
          {form.type === 'ATOMIC' ? '选择物理资产（度量列或者过滤列）' : '选择语义资产（指标）'}
        </label>
        <div className="flex-1 min-h-0">
          {form.type === 'ATOMIC' ? (
            <CascadingPicker
              onMeasureToggle={onMeasureToggle}
              onFilterToggle={onFilterToggle}
              measureColumns={form.measureColumns}
              filterColumns={form.filterColumns}
            />
          ) : (
            <MetricPicker onInsert={insertDerivedFormula} />
          )}
        </div>
      </div>
    </div>
  )
}
