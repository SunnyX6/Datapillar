import { useState, useEffect, useRef, useCallback, useLayoutEffect, memo, startTransition, useMemo } from 'react'
import { createPortal } from 'react-dom'
import { Database, Box, Hash, ChevronRight, Loader2, ArrowRight, Search, Filter, Target, Pin } from 'lucide-react'
import { fetchCatalogs, fetchSchemas, fetchTables, getTable, getObjectTags } from '@/services/oneMetaService'
import { fetchValueDomains } from '@/services/oneMetaSemanticService'
import type { MetricFormData, MeasureColumn, FilterColumn } from './types'

const VALUE_DOMAIN_TAG_PREFIX = 'vd:'

const ColumnTableRow = memo(function ColumnTableRow({ colInfo, isMeasure, isFilter, filterValues, domainCode, onMeasureToggle, onFilterToggle, mode = 'atomic' }: {
  colInfo: { name: string; type: string; comment?: string }
  isMeasure: boolean
  isFilter: boolean
  filterValues: Array<{ key: string; label: string }>
  domainCode: string | undefined
  onMeasureToggle: (col: MeasureColumn, selected: boolean) => void
  onFilterToggle: (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => void
  mode?: 'atomic' | 'derived'
}) {
  const [open, setOpen] = useState(false)
  const [loadingDomain, setLoadingDomain] = useState(false)
  const [valueDomain, setValueDomain] = useState<Array<{ key: string; label: string }>>([])
  const filterButtonRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ bottom: number; left: number } | null>(null)

  const col = useMemo<MeasureColumn>(() => ({ name: colInfo.name, type: colInfo.type, comment: colInfo.comment }), [colInfo.name, colInfo.type, colInfo.comment])
  const DROPDOWN_WIDTH = 192

  const handleMeasureClick = useCallback(() => {
    onMeasureToggle(col, !isMeasure)
  }, [col, isMeasure, onMeasureToggle])

  const handleFilterChange = useCallback((values: Array<{ key: string; label: string }>) => {
    if (values.length > 0) {
      onFilterToggle(col, true, { ...col, values })
    } else {
      onFilterToggle(col, false)
    }
  }, [col, onFilterToggle])

  // 点击只负责切换开关
  const handleFilterClick = () => {
    setOpen((prev) => !prev)
  }

  // 点击外部关闭
  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (filterButtonRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  // 位置计算 - 和左侧完全一致的模式
  useLayoutEffect(() => {
    if (!open) return
    const updatePosition = () => {
      const btn = filterButtonRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const left = Math.max(12, rect.right - DROPDOWN_WIDTH)
      const bottom = window.innerHeight - rect.top + 4
      setDropdownPos({ bottom, left })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      setDropdownPos(null)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [open])

  // 数据加载 - 只在首次打开且有 domainCode 时加载
  const loadedRef = useRef(false)
  useEffect(() => {
    if (!open || !domainCode || loadedRef.current) return
    loadedRef.current = true
    startTransition(() => setLoadingDomain(true))
    fetchValueDomains(0, 100)
      .then((result) => {
        const domain = result.items.find((d) => d.domainCode === domainCode)
        if (domain) {
          setValueDomain(domain.items.map((i) => ({ key: i.value, label: i.label || i.value })))
        }
      })
      .catch(() => {})
      .finally(() => setLoadingDomain(false))
  }, [open, domainCode])
  const toggleValue = (val: { key: string; label: string }) => {
    const exists = filterValues.some((v) => v.key === val.key)
    if (exists) {
      handleFilterChange(filterValues.filter((v) => v.key !== val.key))
    } else {
      handleFilterChange([...filterValues, val])
    }
  }

  // 派生模式只看过滤列，原子模式看度量或过滤
  const isSelected = mode === 'derived' ? isFilter : (isMeasure || isFilter)

  return (
    <tr className={`border-b ${
      isSelected
        ? 'bg-blue-50/50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800'
        : 'border-slate-100 dark:border-slate-800 hover:bg-white dark:hover:bg-slate-900'
    }`}>
      <td className="px-4 py-2.5" title={colInfo.comment || ''}>
        <div className={`flex items-center gap-1.5 text-body-sm font-medium truncate ${
          isSelected ? 'text-blue-700 dark:text-blue-300' : 'text-slate-800 dark:text-slate-200'
        }`}>
          {colInfo.name}
          {domainCode && (
            <span
              className="inline-flex items-center px-1.5 py-0.5 text-micro font-medium bg-indigo-50 dark:bg-indigo-900/30 text-indigo-500 dark:text-indigo-400 rounded-full"
              title="已关联值域"
            >
              <Pin size={8} />
            </span>
          )}
        </div>
      </td>
      <td className="px-4 py-2.5">
        <span className="text-caption font-mono text-slate-500">{colInfo.type}</span>
      </td>
      <td className="px-4 py-2.5 w-20">
        <div className="flex items-center justify-center gap-1">
          {/* 原子模式显示度量按钮，派生模式隐藏 */}
          {mode === 'atomic' && (
            <button
              type="button"
              onClick={handleMeasureClick}
              title={isMeasure ? '取消度量' : '设为度量列'}
              className={`p-1 rounded ${
                isMeasure
                  ? 'bg-blue-500 text-white'
                  : 'text-slate-400 hover:bg-blue-50 hover:text-blue-500 dark:hover:bg-blue-900/30'
              }`}
            >
              <Target size={14} />
            </button>
          )}

          <div className="relative">
            <button
              type="button"
              ref={filterButtonRef}
              onClick={handleFilterClick}
              title={isFilter ? `已选${filterValues.length}个过滤值` : '设为过滤列'}
              className={`p-1 rounded ${
                isFilter
                  ? 'bg-amber-500 text-white'
                  : loadingDomain
                    ? 'text-slate-300'
                    : 'text-slate-400 hover:bg-amber-50 hover:text-amber-500 dark:hover:bg-amber-900/30'
              }`}
              disabled={loadingDomain}
            >
              {loadingDomain ? <Loader2 size={14} className="animate-spin" /> : <Filter size={14} />}
            </button>

            {open && dropdownPos && createPortal(
              <div
                ref={dropdownRef}
                style={{ bottom: dropdownPos.bottom, left: dropdownPos.left }}
                className="fixed w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-[1000000]"
              >
                <div className="p-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
                  <span className="text-micro font-semibold text-slate-500">选择过滤值</span>
                  {loadingDomain && <Loader2 size={12} className="animate-spin text-slate-400" />}
                </div>
                <div className="max-h-48 overflow-y-auto custom-scrollbar">
                  {valueDomain.length > 0 ? (
                    valueDomain.map((val) => {
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
                          <span className="text-caption font-mono text-slate-700 dark:text-slate-300">{val.key}</span>
                          {val.label && val.label !== val.key && (
                            <span className="text-micro text-slate-400 truncate">({val.label})</span>
                          )}
                        </label>
                      )
                    })
                  ) : !loadingDomain ? (
                    <div className="py-4 text-center text-caption text-slate-400">暂无值域数据</div>
                  ) : null}
                </div>
                {filterValues.length > 0 && (
                  <div className="p-2 border-t border-slate-100 dark:border-slate-800">
                    <button
                      type="button"
                      onClick={() => handleFilterChange([])}
                      className="w-full text-micro text-red-500 hover:text-red-600"
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
})

function CascadingPicker({ mode = 'atomic', onMeasureToggle, onFilterToggle, onTableSelect, measureColumns, filterColumns, initialRef, aiSuggestedMeasures, aiSuggestedFilters }: {
  mode?: 'atomic' | 'derived'
  onMeasureToggle: (col: MeasureColumn, selected: boolean) => void
  onFilterToggle: (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => void
  onTableSelect?: (catalog: string, schema: string, table: string) => void
  measureColumns: MeasureColumn[]
  filterColumns: FilterColumn[]
  initialRef?: { catalog?: string; schema?: string; table?: string }
  aiSuggestedMeasures?: string[]
  aiSuggestedFilters?: string[]
}) {
  type Level = 'CATALOG' | 'SCHEMA' | 'TABLE' | 'COLUMN'

  const [catalogs, setCatalogs] = useState<string[]>([])
  const [schemas, setSchemas] = useState<string[]>([])
  const [tables, setTables] = useState<string[]>([])
  const [columns, setColumns] = useState<{ name: string; type: string; comment?: string }[]>([])

  const [selected, setSelected] = useState({ catalog: '', schema: '', table: '' })
  const [level, setLevel] = useState<Level>('CATALOG')

  // 使用 ref 存储回调函数和当前已选列，避免 effect 依赖频繁变化
  const callbacksRef = useRef({ onMeasureToggle, onFilterToggle, measureColumns, filterColumns })
  useLayoutEffect(() => {
    callbacksRef.current = { onMeasureToggle, onFilterToggle, measureColumns, filterColumns }
  })
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)

  // 列的值域标签映射（columnName -> domainCode），用于显示 Pin 图标
  const [columnDomainMap, setColumnDomainMap] = useState<Map<string, string>>(new Map())

  // 批量加载列的值域标签，返回 Map
  const loadColumnDomainTags = useCallback(async (catalog: string, schema: string, table: string, columnNames: string[]) => {
    const map = new Map<string, string>()
    await Promise.all(
      columnNames.map(async (colName) => {
        try {
          const fullName = `${catalog}.${schema}.${table}.${colName}`
          const tags = await getObjectTags('COLUMN', fullName)
          const tag = tags.find((t) => t.startsWith(VALUE_DOMAIN_TAG_PREFIX))
          if (tag) {
            map.set(colName, tag.slice(VALUE_DOMAIN_TAG_PREFIX.length))
          }
        } catch {
          // 忽略单个列的错误
        }
      })
    )
    return map
  }, [])

  // 初始化加载数据
  useEffect(() => {
    let cancelled = false
    const hasInitialRef = initialRef?.catalog && initialRef?.schema && initialRef?.table

    if (hasInitialRef) {
      // 编辑模式：直接用三个参数请求表详情
      startTransition(() => {
        setLoading(true)
        setSelected({
          catalog: initialRef.catalog!,
          schema: initialRef.schema!,
          table: initialRef.table!
        })
        setLevel('COLUMN')
      })
      getTable(initialRef.catalog!, initialRef.schema!, initialRef.table!)
        .then(async (data) => {
          if (cancelled) return
          const cols = data.columns?.map((c) => ({ name: c.name, type: c.dataType, comment: c.comment })) || []
          // 先加载标签，再一起设置状态
          const domainMap = await loadColumnDomainTags(initialRef.catalog!, initialRef.schema!, initialRef.table!, cols.map((c) => c.name))
          if (!cancelled) {
            setColumns(cols)
            setColumnDomainMap(domainMap)
          }
        })
        .catch(() => {
          if (!cancelled) {
            setColumns([])
            setColumnDomainMap(new Map())
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    } else {
      // 新建模式：从 catalog 开始
      fetchCatalogs()
        .then((data) => { if (!cancelled) setCatalogs(data.map((c) => c.name)) })
        .catch(() => { if (!cancelled) setCatalogs([]) })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }

    return () => { cancelled = true }
  }, [initialRef?.catalog, initialRef?.schema, initialRef?.table, loadColumnDomainTags])

  // AI 建议的列自动选中
  useEffect(() => {
    if (columns.length === 0) return
    const { onMeasureToggle: toggleMeasure, onFilterToggle: toggleFilter, measureColumns: measures, filterColumns: filters } = callbacksRef.current

    // 处理 AI 建议的度量列
    if (aiSuggestedMeasures && aiSuggestedMeasures.length > 0) {
      const existingMeasureNames = new Set(measures.map((c) => c.name))
      aiSuggestedMeasures.forEach((colName) => {
        if (existingMeasureNames.has(colName)) return
        const col = columns.find((c) => c.name === colName)
        if (col) {
          toggleMeasure({ name: col.name, type: col.type, comment: col.comment }, true)
        }
      })
    }

    // 处理 AI 建议的过滤列（简单选中，不设置值域值）
    if (aiSuggestedFilters && aiSuggestedFilters.length > 0) {
      const existingFilterNames = new Set(filters.map((c) => c.name))
      aiSuggestedFilters.forEach((colName) => {
        if (existingFilterNames.has(colName)) return
        const col = columns.find((c) => c.name === colName)
        if (col) {
          toggleFilter({ name: col.name, type: col.type, comment: col.comment }, true)
        }
      })
    }
  }, [aiSuggestedMeasures, aiSuggestedFilters, columns])

  const handleSelect = useCallback((item: string) => {
    setSearch('')
    if (level === 'CATALOG') {
      setSelected({ catalog: item, schema: '', table: '' })
      setSchemas([])
      setTables([])
      setColumns([])
      setColumnDomainMap(new Map())
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
      setColumnDomainMap(new Map())
      setLoading(true)
      fetchTables(selected.catalog, item)
        .then((data) => setTables(data.map((t) => t.name)))
        .catch(() => setTables([]))
        .finally(() => setLoading(false))
      setLevel('TABLE')
    } else if (level === 'TABLE') {
      setSelected((p) => ({ ...p, table: item }))
      setColumns([])
      setColumnDomainMap(new Map())
      setLoading(true)
      // 通知父组件表选择变化
      onTableSelect?.(selected.catalog, selected.schema, item)
      getTable(selected.catalog, selected.schema, item)
        .then(async (data) => {
          const cols = data.columns?.map((c) => ({ name: c.name, type: c.dataType, comment: c.comment })) || []
          const domainMap = await loadColumnDomainTags(selected.catalog, selected.schema, item, cols.map((c) => c.name))
          setColumns(cols)
          setColumnDomainMap(domainMap)
        })
        .catch(() => {
          setColumns([])
          setColumnDomainMap(new Map())
        })
        .finally(() => setLoading(false))
      setLevel('COLUMN')
    }
  }, [level, selected, loadColumnDomainTags, onTableSelect])

  const goBack = useCallback((targetLevel: Level) => {
    setSearch('')
    setLevel(targetLevel)

    if (targetLevel === 'CATALOG') {
      setLoading(true)
      setColumnDomainMap(new Map())
      fetchCatalogs()
        .then((data) => setCatalogs(data.map((c) => c.name)))
        .catch(() => setCatalogs([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'SCHEMA' && selected.catalog) {
      setLoading(true)
      setColumnDomainMap(new Map())
      fetchSchemas(selected.catalog)
        .then((data) => setSchemas(data.map((s) => s.name)))
        .catch(() => setSchemas([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'TABLE' && selected.catalog && selected.schema) {
      setLoading(true)
      setColumnDomainMap(new Map())
      fetchTables(selected.catalog, selected.schema)
        .then((data) => setTables(data.map((t) => t.name)))
        .catch(() => setTables([]))
        .finally(() => setLoading(false))
    } else if (targetLevel === 'COLUMN' && selected.catalog && selected.schema && selected.table) {
      setLoading(true)
      getTable(selected.catalog, selected.schema, selected.table)
        .then(async (data) => {
          const cols = data.columns?.map((c) => ({ name: c.name, type: c.dataType, comment: c.comment })) || []
          const domainMap = await loadColumnDomainTags(selected.catalog, selected.schema, selected.table, cols.map((c) => c.name))
          setColumns(cols)
          setColumnDomainMap(domainMap)
        })
        .catch(() => {
          setColumns([])
          setColumnDomainMap(new Map())
        })
        .finally(() => setLoading(false))
    }
  }, [selected, loadColumnDomainTags])

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
            className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg pl-9 pr-4 py-2 text-caption placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-blue-500"
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
                const domainCode = columnDomainMap.get(item)

                return (
                  <ColumnTableRow
                    key={item}
                    colInfo={colInfo}
                    isMeasure={isMeasure}
                    isFilter={isFilter}
                    filterValues={filterCol?.values || []}
                    domainCode={domainCode}
                    onMeasureToggle={onMeasureToggle}
                    onFilterToggle={onFilterToggle}
                    mode={mode}
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

/** 复合指标 - 指标选择器 */
function MetricSelector({ selectedMetrics, onMetricsChange }: {
  selectedMetrics: Array<{ code: string; name: string; comment?: string }>
  onMetricsChange?: (metrics: Array<{ code: string; name: string; comment?: string }>) => void
}) {
  const [metrics, setMetrics] = useState<Array<{ code: string; name: string; type: string; comment?: string }>>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  // 加载原子指标和派生指标
  useEffect(() => {
    import('@/services/oneMetaSemanticService').then(({ fetchMetrics }) => {
      fetchMetrics(0, 500)
        .then((data) => {
          // 只取原子指标和派生指标
          const filtered = data.items
            .filter((m) => m.type.toUpperCase() === 'ATOMIC' || m.type.toUpperCase() === 'DERIVED')
            .map((m) => ({ code: m.code, name: m.name, type: m.type.toUpperCase(), comment: m.comment }))
          setMetrics(filtered)
        })
        .catch(() => setMetrics([]))
        .finally(() => setLoading(false))
    })
  }, [])

  const filteredMetrics = metrics.filter((m) =>
    m.name.toLowerCase().includes(search.toLowerCase()) ||
    m.code.toLowerCase().includes(search.toLowerCase())
  )

  const toggleMetric = (metric: { code: string; name: string; comment?: string }) => {
    const exists = selectedMetrics.some((m) => m.code === metric.code)
    if (exists) {
      onMetricsChange?.(selectedMetrics.filter((m) => m.code !== metric.code))
    } else {
      onMetricsChange?.([...selectedMetrics, metric])
    }
  }

  const isSelected = (code: string) => selectedMetrics.some((m) => m.code === code)

  return (
    <div className="bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-200 dark:border-slate-700 flex flex-col h-[620px]">
      {/* 搜索 */}
      <div className="p-3 border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 rounded-t-2xl">
        <div className="relative">
          <Search size={14} className="absolute left-3 top-2.5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索指标..."
            className="w-full bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg pl-9 pr-4 py-2 text-caption placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-emerald-500"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* 指标列表 */}
      <div className="flex-1 overflow-y-auto custom-scrollbar">
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
          <div className="p-3 space-y-1.5">
            {filteredMetrics.map((metric) => {
              const selected = isSelected(metric.code)
              return (
                <button
                  key={metric.code}
                  type="button"
                  onClick={() => toggleMetric(metric)}
                  className={`w-full flex items-center gap-3 p-3 rounded-xl border text-left transition-all group ${
                    selected
                      ? 'border-emerald-300 dark:border-emerald-700 bg-emerald-50 dark:bg-emerald-900/20'
                      : 'border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 hover:border-emerald-200 dark:hover:border-emerald-700 hover:shadow-sm'
                  }`}
                >
                  <div className={`p-1.5 rounded-lg ${
                    selected
                      ? 'bg-emerald-500 text-white'
                      : metric.type === 'ATOMIC'
                        ? 'bg-purple-50 dark:bg-purple-900/30 text-purple-500'
                        : 'bg-blue-50 dark:bg-blue-900/30 text-blue-500'
                  }`}>
                    <Target size={14} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className={`text-body-sm font-medium truncate ${
                      selected ? 'text-emerald-700 dark:text-emerald-300' : 'text-slate-800 dark:text-slate-200'
                    }`}>
                      {metric.name}
                    </div>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-micro font-mono text-slate-400">{metric.code}</span>
                      <span className={`text-micro px-1.5 py-0.5 rounded ${
                        metric.type === 'ATOMIC'
                          ? 'bg-purple-50 dark:bg-purple-900/30 text-purple-500'
                          : 'bg-blue-50 dark:bg-blue-900/30 text-blue-500'
                      }`}>
                        {metric.type === 'ATOMIC' ? '原子' : '派生'}
                      </span>
                    </div>
                  </div>
                  {selected && (
                    <div className="w-5 h-5 rounded-full bg-emerald-500 flex items-center justify-center">
                      <span className="text-white text-micro">✓</span>
                    </div>
                  )}
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

interface MetricFormRightProps {
  form: MetricFormData
  onMeasureToggle: (col: MeasureColumn, selected: boolean) => void
  onFilterToggle: (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => void
  onTableSelect?: (catalog: string, schema: string, table: string) => void
  onMetricsChange?: (metrics: Array<{ code: string; name: string; comment?: string }>) => void
  aiSuggestedMeasures?: string[]
  aiSuggestedFilters?: string[]
}

export function MetricFormRight({
  form,
  onMeasureToggle,
  onFilterToggle,
  onTableSelect,
  onMetricsChange,
  aiSuggestedMeasures,
  aiSuggestedFilters
}: MetricFormRightProps) {
  // 原子指标：选择物理资产（度量列 + 过滤列）
  if (form.type === 'ATOMIC') {
    return (
      <div className="col-span-7 xl:col-span-8 flex h-full min-h-0 flex-col">
        <div className="h-full min-h-0 flex flex-col gap-1.5">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2 shrink-0">
            <Database size={14} className="text-blue-500" />
            物理资产（选择度量列或过滤列）
          </label>
          <div className="flex-1 min-h-0">
            <CascadingPicker
              mode="atomic"
              onMeasureToggle={onMeasureToggle}
              onFilterToggle={onFilterToggle}
              onTableSelect={onTableSelect}
              measureColumns={form.measureColumns}
              filterColumns={form.filterColumns}
              initialRef={{
                catalog: form.refCatalogName,
                schema: form.refSchemaName,
                table: form.refTableName
              }}
              aiSuggestedMeasures={aiSuggestedMeasures}
              aiSuggestedFilters={aiSuggestedFilters}
            />
          </div>
        </div>
      </div>
    )
  }

  // 派生指标：选择物理资产（仅维度过滤列）
  if (form.type === 'DERIVED') {
    return (
      <div className="col-span-7 xl:col-span-8 flex h-full min-h-0 flex-col">
        <div className="h-full min-h-0 flex flex-col gap-1.5">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2 shrink-0">
            <Database size={14} className="text-amber-500" />
            物理资产（选择维度列过滤）
          </label>
          <div className="flex-1 min-h-0">
            <CascadingPicker
              mode="derived"
              onMeasureToggle={onMeasureToggle}
              onFilterToggle={onFilterToggle}
              onTableSelect={onTableSelect}
              measureColumns={form.measureColumns}
              filterColumns={form.filterColumns}
              initialRef={{
                catalog: form.refCatalogName,
                schema: form.refSchemaName,
                table: form.refTableName
              }}
              aiSuggestedFilters={aiSuggestedFilters}
            />
          </div>
        </div>
      </div>
    )
  }

  // 复合指标：选择指标列表
  return (
    <div className="col-span-7 xl:col-span-8 flex h-full min-h-0 flex-col">
      <div className="h-full min-h-0 flex flex-col gap-1.5">
        <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2 shrink-0">
          <Target size={14} className="text-emerald-500" />
          选择参与运算的指标
        </label>
        <div className="flex-1 min-h-0">
          <MetricSelector
            selectedMetrics={form.compositeMetrics || []}
            onMetricsChange={onMetricsChange}
          />
        </div>
      </div>
    </div>
  )
}
