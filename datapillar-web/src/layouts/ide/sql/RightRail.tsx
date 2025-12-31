/**
 * SQL编辑器 - 右侧工具栏组件
 * 基于 BaseRightRail 骨架构建
 */

import { useState, useEffect } from 'react'
import {
  Database,
  Sparkles,
  Zap, Send, ChevronRight, Table, Loader2,
  FileCode, AlertTriangle, Clock, Code, Columns
} from 'lucide-react'
import {
  SiApachehive,
  SiApachespark,
  SiApachekafka,
  SiApacheflink,
  SiApachehadoop,
  SiClickhouse,
  SiSnowflake,
  SiDatabricks,
  SiElasticsearch,
  SiMongodb,
  SiPostgresql,
  SiMysql,
  SiRedis
} from 'react-icons/si'
import { BaseRightRail, type RightRailButton } from '../components'
import {
  fetchCatalogs,
  fetchTables,
  getTable,
  mapProviderToIcon,
  type CatalogItem,
  type TableItem
} from '@/services/oneMetaService'

interface RightRailProps {
  /** 当前选中的 catalog */
  selectedCatalog?: string
  /** 当前选中的 schema */
  selectedSchema?: string
  /** 请求打开 database 面板（用户主动选择时触发） */
  requestOpenDatabase?: boolean
  /** 重置请求状态的回调 */
  onResetOpenRequest?: () => void
}

/** 获取 Catalog 图标 */
function getCatalogIcon(iconName?: string, size = 12) {
  const iconProps = { size, className: 'text-blue-600 shrink-0' }
  switch (iconName) {
    case 'hive':
      return <SiApachehive {...iconProps} />
    case 'spark':
      return <SiApachespark {...iconProps} />
    case 'kafka':
      return <SiApachekafka {...iconProps} />
    case 'flink':
      return <SiApacheflink {...iconProps} />
    case 'hadoop':
      return <SiApachehadoop {...iconProps} />
    case 'clickhouse':
      return <SiClickhouse {...iconProps} />
    case 'snowflake':
      return <SiSnowflake {...iconProps} />
    case 'databricks':
      return <SiDatabricks {...iconProps} />
    case 'elasticsearch':
      return <SiElasticsearch {...iconProps} />
    case 'mongodb':
      return <SiMongodb {...iconProps} />
    case 'postgresql':
      return <SiPostgresql {...iconProps} />
    case 'mysql':
      return <SiMysql {...iconProps} />
    case 'redis':
      return <SiRedis {...iconProps} />
    case 'iceberg':
      return <Database size={size} className="text-cyan-600 shrink-0" />
    default:
      return <Database size={size} className="text-blue-600 shrink-0" />
  }
}

/** 右侧按钮配置 */
const RIGHT_RAIL_BUTTONS: RightRailButton[] = [
  {
    id: 'database',
    icon: <Database size={14} />,
    title: 'Database Management',
    activeClassName: 'bg-amber-500 text-white shadow-md shadow-amber-500/30',
    inactiveClassName: 'text-slate-300 dark:text-slate-600 hover:text-amber-500 hover:bg-slate-50 dark:hover:bg-slate-800'
  },
  {
    id: 'ai',
    icon: <Sparkles size={14} />,
    title: 'AI Assistant',
    activeClassName: 'bg-indigo-600 text-white shadow-indigo-600/30 shadow-md',
    inactiveClassName: 'text-slate-300 dark:text-slate-600 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800'
  }
]

export function RightRail({ selectedCatalog, selectedSchema, requestOpenDatabase, onResetOpenRequest }: RightRailProps) {
  const [activePanel, setActivePanel] = useState<string | null>(null)
  const [aiChatInput, setAiChatInput] = useState('')

  // 只有当父组件请求打开 database 面板时才打开（用户主动选择 schema 时）
  useEffect(() => {
    if (requestOpenDatabase) {
      const frameId = requestAnimationFrame(() => {
        setActivePanel('database')
      })
      onResetOpenRequest?.()
      return () => cancelAnimationFrame(frameId)
    }
  }, [requestOpenDatabase, onResetOpenRequest])

  // 根据当前激活面板渲染标题
  const panelTitle = activePanel === 'database' ? (
    <><Database size={12} className="text-amber-500" /><span className="text-micro font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">Data Sources</span></>
  ) : activePanel === 'ai' ? (
    <><Sparkles size={12} className="text-indigo-500 dark:text-indigo-400" /><span className="text-micro font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">AI Assistant</span></>
  ) : null

  return (
    <BaseRightRail
      buttons={RIGHT_RAIL_BUTTONS}
      activePanel={activePanel}
      onPanelChange={setActivePanel}
      panelTitle={panelTitle}
      panelWidth={320}
    >
      {activePanel === 'database' && (
        <DatabasePanel
          selectedCatalog={selectedCatalog}
          selectedSchema={selectedSchema}
        />
      )}
      {activePanel === 'ai' && (
        <AIPanel aiChatInput={aiChatInput} onInputChange={setAiChatInput} />
      )}
    </BaseRightRail>
  )
}

/** 数据源面板 - 树形结构（与 tab 内 catalog/schema 下拉联动） */
function DatabasePanel({
  selectedCatalog,
  selectedSchema
}: {
  selectedCatalog?: string
  selectedSchema?: string
}) {
  const [catalog, setCatalog] = useState<CatalogItem | null>(null)
  const [loading, setLoading] = useState(false)
  const [tables, setTables] = useState<TableItem[]>([])
  const [loadingTables, setLoadingTables] = useState(false)
  const [expandedCatalog, setExpandedCatalog] = useState(true)
  const [expandedSchema, setExpandedSchema] = useState(true)
  const [expandedTables, setExpandedTables] = useState<Set<string>>(new Set())
  const [tableDetails, setTableDetails] = useState<Map<string, TableItem>>(new Map())
  const [loadingTableId, setLoadingTableId] = useState<string | null>(null)

  // 当选中 catalog 变化时，加载 catalog 信息
  useEffect(() => {
    if (!selectedCatalog) {
      setCatalog(null)
      return
    }

    const loadCatalog = async () => {
      setLoading(true)
      try {
        const allCatalogs = await fetchCatalogs()
        const currentCatalog = allCatalogs.find(c => c.name === selectedCatalog)
        setCatalog(currentCatalog || null)
      } catch (error) {
        console.error('Failed to load catalog:', error)
      } finally {
        setLoading(false)
      }
    }
    loadCatalog()
  }, [selectedCatalog])

  // 当选中的 schema 变化时，加载 tables
  useEffect(() => {
    if (!selectedCatalog || !selectedSchema) {
      setTables([])
      setExpandedTables(new Set())
      setTableDetails(new Map())
      return
    }

    const loadTables = async () => {
      setLoadingTables(true)
      try {
        const data = await fetchTables(selectedCatalog, selectedSchema)
        setTables(data)
      } catch (error) {
        console.error('Failed to load tables:', error)
        setTables([])
      } finally {
        setLoadingTables(false)
      }
    }
    loadTables()
  }, [selectedCatalog, selectedSchema])

  // 切换表的展开/折叠状态
  const toggleTable = async (table: TableItem) => {
    const isExpanded = expandedTables.has(table.id)

    if (isExpanded) {
      setExpandedTables(prev => {
        const next = new Set(prev)
        next.delete(table.id)
        return next
      })
    } else {
      setExpandedTables(prev => new Set(prev).add(table.id))

      // 如果还没有加载过列信息，则加载
      if (!tableDetails.has(table.id) && selectedCatalog && selectedSchema) {
        setLoadingTableId(table.id)
        try {
          const detail = await getTable(selectedCatalog, selectedSchema, table.name)
          setTableDetails(prev => new Map(prev).set(table.id, detail))
        } catch (error) {
          console.error('Failed to load table details:', error)
        } finally {
          setLoadingTableId(null)
        }
      }
    }
  }

  // 未选择 catalog 时显示提示
  if (!selectedCatalog || !catalog) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        <Database size={32} className="text-slate-200 dark:text-slate-700 mb-3" />
        <p className="text-micro text-slate-400 dark:text-slate-500">请先在编辑器工具栏中</p>
        <p className="text-micro text-slate-400 dark:text-slate-500">选择 Catalog 和 Schema</p>
      </div>
    )
  }

  const iconName = mapProviderToIcon(catalog.provider)

  return (
    <div className="space-y-0.5">
      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 size={16} className="animate-spin text-slate-400 dark:text-slate-500" />
        </div>
      ) : (
        <div>
          {/* Catalog 节点 */}
          <div
            onClick={() => setExpandedCatalog(!expandedCatalog)}
            className="flex items-center gap-1.5 px-2 py-1.5 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md cursor-pointer transition-colors"
          >
            <ChevronRight
              size={12}
              className={`text-slate-400 dark:text-slate-500 shrink-0 transition-transform ${expandedCatalog ? 'rotate-90' : ''}`}
            />
            {getCatalogIcon(iconName, 13)}
            <span className="text-body-sm font-medium truncate">{catalog.name}</span>
          </div>

          {/* Schema 节点 */}
          {expandedCatalog && selectedSchema && (
            <div className="ml-4 border-l border-slate-100 dark:border-slate-800 pl-2 mt-0.5">
              <div
                onClick={() => setExpandedSchema(!expandedSchema)}
                className="flex items-center gap-1.5 px-2 py-1 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md cursor-pointer transition-colors"
              >
                <ChevronRight
                  size={11}
                  className={`text-slate-400 dark:text-slate-500 shrink-0 transition-transform ${expandedSchema ? 'rotate-90' : ''}`}
                />
                <Database size={11} className="text-amber-500 shrink-0" />
                <span className="text-xs truncate">{selectedSchema}</span>
              </div>

              {/* Tables */}
              {expandedSchema && (
                <div className="ml-4 border-l border-slate-100 dark:border-slate-800 pl-2 mt-0.5">
                {loadingTables ? (
                  <div className="flex items-center gap-2 px-2 py-1 text-xs text-slate-400 dark:text-slate-500">
                    <Loader2 size={11} className="animate-spin" />
                    <span>加载中...</span>
                  </div>
                ) : tables.length === 0 ? (
                  <div className="px-2 py-1 text-xs text-slate-400 dark:text-slate-500">暂无表</div>
                ) : (
                  tables.map(table => {
                    const isExpanded = expandedTables.has(table.id)
                    const detail = tableDetails.get(table.id)
                    const isLoadingThis = loadingTableId === table.id

                    return (
                      <div key={table.id}>
                        <div
                          draggable
                          onDragStart={(e) => {
                            const fullTableName = `${selectedCatalog}.${selectedSchema}.${table.name}`
                            e.dataTransfer.setData('text/plain', fullTableName)
                            e.dataTransfer.setData('application/x-table-drag', JSON.stringify({
                              catalog: selectedCatalog,
                              schema: selectedSchema,
                              table: table.name
                            }))
                            e.dataTransfer.effectAllowed = 'copy'
                          }}
                          onClick={() => toggleTable(table)}
                          className="flex items-center gap-1.5 px-2 py-1 rounded-md text-left hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-300 cursor-grab active:cursor-grabbing transition-colors"
                          title={`${selectedCatalog}.${selectedSchema}.${table.name}`}
                        >
                          <ChevronRight
                            size={11}
                            className={`text-slate-400 dark:text-slate-500 shrink-0 transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                          />
                          <Table size={11} className="text-slate-400 dark:text-slate-500 shrink-0" />
                          <span className="text-xs truncate flex-1">{table.name}</span>
                          {isLoadingThis && <Loader2 size={11} className="animate-spin text-slate-400 dark:text-slate-500" />}
                        </div>

                        {/* Columns */}
                        {isExpanded && (
                          <div className="ml-4 border-l border-slate-100 dark:border-slate-800 pl-2 mt-0.5">
                            {isLoadingThis ? (
                              <div className="flex items-center gap-2 px-2 py-0.5 text-legal text-slate-400 dark:text-slate-500">
                                <Loader2 size={9} className="animate-spin" />
                                <span>加载列...</span>
                              </div>
                            ) : detail?.columns && detail.columns.length > 0 ? (
                              detail.columns.map(col => (
                                <div
                                  key={col.name}
                                  className="flex items-center gap-1.5 px-2 py-0.5 text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded transition-colors"
                                  title={col.comment || col.name}
                                >
                                  <Columns size={10} className="text-slate-300 dark:text-slate-600 shrink-0" />
                                  <span className="text-legal truncate">{col.name}</span>
                                  <span className="text-legal text-slate-300 dark:text-slate-600 truncate">{col.dataType}</span>
                                </div>
                              ))
                            ) : (
                              <div className="px-2 py-0.5 text-legal text-slate-400 dark:text-slate-500">暂无列信息</div>
                            )}
                          </div>
                        )}
                      </div>
                    )
                  })
                )}
              </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/** AI助手面板 */
function AIPanel({ aiChatInput, onInputChange }: { aiChatInput: string; onInputChange: (value: string) => void }) {
  const recentDecisions = [
    'Enable State TTL for analysis_01',
    "Apply Masking to 'user_email'",
    'Optimize JOIN with Hash Distribution'
  ]

  return (
    <div className="h-full flex flex-col">
      {/* LIVE INSIGHT 卡片 */}
      <div className="mb-4 p-3 rounded-xl bg-gradient-to-br from-slate-50 to-white dark:from-slate-800 dark:to-slate-900 border border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-2 mb-2">
          <div className="p-1.5 rounded-lg bg-indigo-100 dark:bg-indigo-900/50">
            <Sparkles size={14} className="text-indigo-600 dark:text-indigo-400" />
          </div>
          <div>
            <p className="text-micro font-bold text-slate-700 dark:text-slate-200">LIVE INSIGHT</p>
            <p className="text-tiny text-indigo-500 dark:text-indigo-400">Context: current query</p>
          </div>
        </div>
        <p className="text-micro text-slate-500 dark:text-slate-400 leading-relaxed mb-3">
          Select a snippet or type below to start optimizing your SQL pipelines.
        </p>
        <div className="flex items-center gap-2 px-2.5 py-2 rounded-lg bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800">
          <AlertTriangle size={14} className="text-amber-500 shrink-0" />
          <span className="text-micro text-amber-700 dark:text-amber-400">PII Data Detected in current query.</span>
        </div>
      </div>

      {/* RECENT ARCHITECT DECISIONS */}
      <div className="flex-1 overflow-y-auto">
        <div className="flex items-center gap-1.5 mb-2">
          <Clock size={12} className="text-slate-400 dark:text-slate-500" />
          <span className="text-tiny text-slate-400 dark:text-slate-500 uppercase tracking-wider font-medium">Recent Architect Decisions</span>
        </div>
        <div className="space-y-1.5">
          {recentDecisions.map((decision, index) => (
            <div
              key={index}
              className="flex items-center gap-2 px-2.5 py-2 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer transition-colors"
            >
              <div className="w-1.5 h-1.5 rounded-full bg-indigo-400 shrink-0" />
              <span className="text-micro text-slate-600 dark:text-slate-300">{decision}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 快捷操作按钮 */}
      <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-800">
        <div className="flex flex-wrap gap-2 mb-3">
          <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-indigo-600 text-white text-tiny font-medium hover:bg-indigo-500 transition-colors">
            <Zap size={11} />
            SQL Optimize
          </button>
          <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full border border-indigo-200 dark:border-indigo-800 text-indigo-600 dark:text-indigo-400 text-tiny font-medium hover:bg-indigo-50 dark:hover:bg-indigo-900/30 transition-colors">
            <FileCode size={11} />
            Explain Logic
          </button>
          <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full border border-rose-200 dark:border-rose-800 text-rose-600 dark:text-rose-400 text-tiny font-medium hover:bg-rose-50 dark:hover:bg-rose-900/30 transition-colors">
            <AlertTriangle size={11} />
            Fix Errors
          </button>
          <button className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full border border-emerald-200 dark:border-emerald-800 text-emerald-600 dark:text-emerald-400 text-tiny font-medium hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition-colors">
            <Code size={11} />
            AI Write SQL
          </button>
        </div>

        {/* 输入区域 */}
        <div className="relative">
          <input
            type="text"
            value={aiChatInput}
            onChange={(e) => onInputChange(e.target.value)}
            placeholder="Ask me to 'Join user with logs'..."
            className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-full pl-4 pr-10 py-2.5 text-micro text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-300 dark:focus:border-indigo-600 transition-all"
          />
          <button className="absolute right-1.5 top-1/2 -translate-y-1/2 p-2 bg-indigo-600 text-white rounded-full shadow hover:bg-indigo-500 transition-all active:scale-95">
            <Send size={12} />
          </button>
        </div>
      </div>
    </div>
  )
}
