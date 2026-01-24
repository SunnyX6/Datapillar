/**
 * SQL编辑器
 * 基于 BaseEditor 骨架构建
 */

import { useState, useEffect, useRef, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import Editor from '@monaco-editor/react'
import type * as Monaco from 'monaco-editor'
import {
  Play, ChevronDown, Database,
  History, Download,
  Share2, Loader2,
  Save,
  Wand2,
  Check,
  Folder,
  Activity,
  Box,
  Copy,
  Zap,
  MessageSquare,
  Bug
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
import { BaseEditor, type EditorTab, type ContextMenuGroup, Minimap } from '../components'
import { BottomPanel } from './BottomPanel'
import { RightRail } from './RightRail'
import { fetchCatalogs, fetchSchemas, mapProviderToIcon, type CatalogItem, type SchemaItem } from '@/services/oneMetaService'
import { executeSql, type ExecuteResult } from '@/services/sqlService'
import { useIsDark } from '@/stores/themeStore'
import { toast } from 'sonner'

/** 获取 Catalog 图标 */
function getCatalogIcon(iconName?: string, size = 11) {
  const iconProps = { size, className: 'text-blue-600' }
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
      return <Database size={size} className="text-cyan-600" />
    case 'folder':
      return <Folder size={size} className="text-amber-600" />
    case 'metric':
      return <Activity size={size} className="text-green-600" />
    case 'model':
      return <Box size={size} className="text-purple-600" />
    default:
      return <Database size={size} className="text-blue-600" />
  }
}

/** SQL方言配置 */
const DIALECTS = [
  { id: 'flink', name: 'Flink SQL 1.17', color: 'text-orange-500' },
  { id: 'pg', name: 'PostgreSQL 15', color: 'text-blue-500' },
  { id: 'spark', name: 'Spark 3.4', color: 'text-red-500' }
]

interface Worksheet {
  id: string
  name: string
  code: string
  catalog: string
  schema: string
}

/** 初始 Tab ID */
const INITIAL_TAB_ID = 'initial-tab'

/** 创建新的空白 Worksheet */
const createNewWorksheet = (id: string, index: number): Worksheet => ({
  id,
  name: `Untitled-${index}.sql`,
  code: '',
  catalog: '',
  schema: ''
})

function defineDatapillarMonacoThemes(monaco: typeof Monaco) {
  monaco.editor.defineTheme('datapillar-light', {
    base: 'vs',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#ffffff',
      'editorLineNumber.foreground': '#6b7280',
      'editorLineNumber.activeForeground': '#1f2937',
      'editorGutter.background': '#ffffff',
      'editor.foldBackground': '#ffffff00'
    }
  })

  monaco.editor.defineTheme('datapillar-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [],
    colors: {
      'editor.background': '#0f172a',
      'editorLineNumber.foreground': '#64748b',
      'editorLineNumber.activeForeground': '#e2e8f0',
      'editorGutter.background': '#0f172a',
      'editor.foldBackground': '#0f172a00'
    }
  })
}

export function SqlEditor() {
  const isDark = useIsDark()
  const [activeDialect, setActiveDialect] = useState(DIALECTS[0])
  const [tabCounter, setTabCounter] = useState(1)
  const [worksheets, setWorksheets] = useState<Worksheet[]>([
    createNewWorksheet(INITIAL_TAB_ID, 1)
  ])
  const [activeTabId, setActiveTabId] = useState(INITIAL_TAB_ID)
  const [isExecuting, setIsExecuting] = useState(false)
  const [cursor, setCursor] = useState({ ln: 1, col: 1 })
  const [executionLogs, setExecutionLogs] = useState<{ time: string; msg: string; type: string }[]>([])
  const [executeResult, setExecuteResult] = useState<ExecuteResult | null>(null)
  const [bottomPanelCollapsed, setBottomPanelCollapsed] = useState(true)
  const [bottomPanelTab, setBottomPanelTab] = useState('results')

  // Monaco Editor 实例引用
  const [editorInstance, setEditorInstance] = useState<Monaco.editor.IStandaloneCodeEditor | null>(null)
  const [monacoInstance, setMonacoInstance] = useState<typeof Monaco | null>(null)

  // Catalog/Schema 选择相关状态
  const [catalogs, setCatalogs] = useState<CatalogItem[]>([])
  const [schemas, setSchemas] = useState<SchemaItem[]>([])
  const [showCatalogPicker, setShowCatalogPicker] = useState(false)
  const [hoveredCatalog, setHoveredCatalog] = useState<string>('')
  const [loadingCatalogs, setLoadingCatalogs] = useState(false)
  const [loadingSchemas, setLoadingSchemas] = useState(false)
  const [requestOpenDatabase, setRequestOpenDatabase] = useState(false)
  const catalogPickerRef = useRef<HTMLDivElement>(null)
  const catalogButtonRef = useRef<HTMLButtonElement>(null)
  const [catalogPickerStyle, setCatalogPickerStyle] = useState<{
    top?: number
    bottom?: number
    right: number
    maxHeight: number
    listMaxHeight: number
  } | null>(null)

  const activeWorksheet = worksheets.find(w => w.id === activeTabId) || worksheets[0]

  // 转换为 BaseEditor 需要的 tabs 格式
  const tabs: EditorTab[] = worksheets.map(w => ({
    id: w.id,
    name: w.name
  }))

  const handleExecute = async () => {
    // 校验：必须选择 catalog 和 schema
    if (!activeWorksheet.catalog || !activeWorksheet.schema) {
      toast.warning('请先选择 Catalog 和 Schema')
      return
    }

    // 校验：SQL 不能为空
    const sql = activeWorksheet.code.trim()
    if (!sql) {
      toast.warning('SQL 语句不能为空')
      return
    }

    setIsExecuting(true)
    setExecuteResult(null)
    setBottomPanelCollapsed(false) // 执行时自动展开底部面板
    setBottomPanelTab('messages') // 执行时显示 messages

    const startTime = new Date()
    const startTimeStr = startTime.toTimeString().slice(0, 8)
    setExecutionLogs([
      { time: startTimeStr, msg: `正在执行 SQL...`, type: 'info' },
      { time: startTimeStr, msg: `Catalog: ${activeWorksheet.catalog}, Schema: ${activeWorksheet.schema}`, type: 'info' }
    ])

    try {
      const result = await executeSql({
        sql,
        catalog: activeWorksheet.catalog,
        database: activeWorksheet.schema
      })

      const endTime = new Date()
      const endTimeStr = endTime.toTimeString().slice(0, 8)

      if (result.success) {
        setExecuteResult(result)
        setExecutionLogs(prev => [
          ...prev,
          { time: endTimeStr, msg: `执行成功，返回 ${result.rowCount} 行，耗时 ${result.executionTime}ms`, type: 'success' }
        ])
      } else {
        setExecutionLogs(prev => [
          ...prev,
          { time: endTimeStr, msg: `执行失败: ${result.error}`, type: 'error' }
        ])
      }
    } catch (error) {
      const endTime = new Date()
      const endTimeStr = endTime.toTimeString().slice(0, 8)
      setExecutionLogs(prev => [
        ...prev,
        { time: endTimeStr, msg: `执行异常: ${error instanceof Error ? error.message : '未知错误'}`, type: 'error' }
      ])
    } finally {
      setIsExecuting(false)
      setBottomPanelTab('results') // 执行完成后显示 results
    }
  }

  // 加载 Catalog 列表
  useEffect(() => {
    const loadCatalogs = async () => {
      setLoadingCatalogs(true)
      try {
        const data = await fetchCatalogs()
        setCatalogs(data)
      } catch (error) {
        console.error('Failed to load catalogs:', error)
      } finally {
        setLoadingCatalogs(false)
      }
    }
    loadCatalogs()
  }, [])

  // 当悬浮 catalog 变化时加载 Schema 列表
  useEffect(() => {
    const loadSchemas = async () => {
      if (!hoveredCatalog) {
        setSchemas([])
        return
      }
      setLoadingSchemas(true)
      try {
        const data = await fetchSchemas(hoveredCatalog)
        setSchemas(data)
      } catch (error) {
        console.error('Failed to load schemas:', error)
        setSchemas([])
      } finally {
        setLoadingSchemas(false)
      }
    }
    loadSchemas()
  }, [hoveredCatalog])

  // 点击外部关闭下拉框
  useEffect(() => {
    if (!showCatalogPicker) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (catalogButtonRef.current?.contains(target)) return
      if (catalogPickerRef.current?.contains(target)) return
      setShowCatalogPicker(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showCatalogPicker])

  useLayoutEffect(() => {
    if (!showCatalogPicker) {
      setCatalogPickerStyle(null)
      return
    }
    const updatePosition = () => {
      const btn = catalogButtonRef.current
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const margin = 12
      const spaceBelow = window.innerHeight - rect.bottom - margin
      const spaceAbove = rect.top - margin
      const openUp = spaceAbove > spaceBelow
      const availableSpace = Math.max(0, openUp ? spaceAbove : spaceBelow)
      const maxHeight = Math.min(availableSpace, 420)
      const headerHeight = 28
      const listMaxHeight = Math.max(0, maxHeight - headerHeight)
      const right = window.innerWidth - rect.right

      if (openUp) {
        setCatalogPickerStyle({
          bottom: window.innerHeight - rect.top + 4,
          right,
          maxHeight,
          listMaxHeight
        })
      } else {
        setCatalogPickerStyle({
          top: rect.bottom + 4,
          right,
          maxHeight,
          listMaxHeight
        })
      }
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [showCatalogPicker])

  // 切换 Tab 时重置 Sticky Scroll 状态（通过禁用再启用强制刷新）
  useEffect(() => {
    if (editorInstance) {
      editorInstance.updateOptions({ stickyScroll: { enabled: false } })
      requestAnimationFrame(() => {
        editorInstance.updateOptions({ stickyScroll: { enabled: true } })
      })
    }
  }, [activeTabId, editorInstance])

  // 悬浮 catalog 时加载对应 schema
  const handleCatalogHover = (catalogName: string) => {
    if (hoveredCatalog !== catalogName) {
      setHoveredCatalog(catalogName)
    }
  }

  // 点击 schema 确认选择
  const handleSchemaChange = (schemaName: string) => {
    setWorksheets(prev => prev.map(w =>
      w.id === activeTabId ? { ...w, catalog: hoveredCatalog, schema: schemaName } : w
    ))
    setShowCatalogPicker(false)
    setRequestOpenDatabase(true)
  }

  // 打开下拉框时初始化 hoveredCatalog
  const handleOpenPicker = () => {
    setShowCatalogPicker(!showCatalogPicker)
    if (!showCatalogPicker) {
      setHoveredCatalog(activeWorksheet.catalog || (catalogs[0]?.name ?? ''))
    }
  }

  // 工具栏左侧内容
  const toolbarLeft = (
    <>
      <div className="w-12 flex items-center justify-center">
        <button
          onClick={handleExecute}
          disabled={isExecuting}
          className={`flex items-center justify-center w-6 h-6 rounded-full border-2 transition-all active:scale-90 ${isExecuting ? 'border-slate-200 dark:border-slate-700 text-slate-300 dark:text-slate-600' : 'border-emerald-500 text-emerald-600 hover:shadow-md hover:shadow-emerald-500/10'}`}
        >
          {isExecuting ? <Loader2 size={10} className="animate-spin" /> : <Play size={10} className="ml-px" />}
        </button>
      </div>
      <div className="h-4 w-px bg-slate-200 dark:bg-slate-700" />
      <div className="flex ml-2">
        <button className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded transition-all" title="Format SQL"><Wand2 size={14} /></button>
        <button className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded transition-all" title="Save"><Save size={14} /></button>
        <button className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded transition-all" title="Query History"><History size={14} /></button>
      </div>
      <div className="h-3 w-px bg-slate-200 dark:bg-slate-700 mx-0.5" />
      <div className="flex">
        <button className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-900/30 rounded transition-all" title="Export CSV"><Download size={14} /></button>
        <button className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-all" title="Share Workset"><Share2 size={14} /></button>
      </div>
    </>
  )

  // 工具栏右侧内容 - Catalog 选择器
  const toolbarRight = (
    <div className="relative">
      <button
        ref={catalogButtonRef}
        onClick={handleOpenPicker}
        className={`flex items-center gap-1.5 text-nano font-bold uppercase tracking-wider transition-colors ${
          showCatalogPicker ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400'
        }`}
      >
        <Database size={11} className={activeWorksheet.catalog && activeWorksheet.schema ? 'text-amber-500' : showCatalogPicker ? 'text-indigo-500 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500'} />
        {activeWorksheet.catalog && activeWorksheet.schema
          ? `${activeWorksheet.catalog}.${activeWorksheet.schema}`
          : '选择 Catalog'}
        <ChevronDown size={10} className={`transition-transform ${showCatalogPicker ? 'rotate-180' : ''}`} />
      </button>

      {/* Catalog/Schema 下拉选择器 */}
      {showCatalogPicker && catalogPickerStyle && createPortal(
        <div
          ref={catalogPickerRef}
          className="fixed z-[100000] bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-xl dark:shadow-2xl overflow-hidden w-panel-responsive max-h-[var(--catalog-picker-max-height)]"
          style={{
            ...(catalogPickerStyle.top !== undefined ? { top: catalogPickerStyle.top } : {}),
            ...(catalogPickerStyle.bottom !== undefined ? { bottom: catalogPickerStyle.bottom } : {}),
            right: catalogPickerStyle.right,
            '--catalog-picker-max-height': `${catalogPickerStyle.maxHeight}px`,
            '--catalog-picker-list-max-height': `${catalogPickerStyle.listMaxHeight}px`
          } as React.CSSProperties}
        >
          <div className="flex">
            {/* Catalog 列表 */}
            <div className="flex-1 min-w-40 border-r border-slate-100 dark:border-slate-700">
              <div className="px-2.5 py-1.5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900">
                <span className="text-nano font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Catalog</span>
              </div>
              <div className="overflow-y-auto py-1 max-h-[var(--catalog-picker-list-max-height)]">
                {loadingCatalogs ? (
                  <div className="flex items-center justify-center py-4">
                    <Loader2 size={14} className="animate-spin text-slate-400 dark:text-slate-500" />
                  </div>
                ) : catalogs.length === 0 ? (
                  <div className="px-2.5 py-3 text-nano text-slate-400 dark:text-slate-500 text-center">暂无数据</div>
                ) : (
                  catalogs.map((cat) => {
                    const isHovered = hoveredCatalog === cat.name
                    return (
                      <div
                        key={cat.id}
                        onMouseEnter={() => handleCatalogHover(cat.name)}
                        className={`w-full flex items-center gap-2.5 px-2.5 py-2 text-left transition-colors cursor-pointer ${
                          isHovered
                            ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                            : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                        }`}
                      >
                        {getCatalogIcon(mapProviderToIcon(cat.provider))}
                        <span className="text-micro leading-4 font-semibold truncate flex-1">{cat.name}</span>
                        {isHovered && <Check size={10} className="text-indigo-500 dark:text-indigo-400 shrink-0" />}
                      </div>
                    )
                  })
                )}
              </div>
            </div>

            {/* Schema 列表 */}
            <div className="flex-1 min-w-40">
              <div className="px-2.5 py-1.5 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900">
                <span className="text-nano font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider">Schema</span>
              </div>
              <div className="overflow-y-auto py-1 max-h-[var(--catalog-picker-list-max-height)]">
                {!hoveredCatalog ? (
                  <div className="px-2.5 py-3 text-nano text-slate-400 dark:text-slate-500 text-center">请先选择 Catalog</div>
                ) : loadingSchemas ? (
                  <div className="flex items-center justify-center py-4">
                    <Loader2 size={14} className="animate-spin text-slate-400 dark:text-slate-500" />
                  </div>
                ) : schemas.length === 0 ? (
                  <div className="px-2.5 py-3 text-nano text-slate-400 dark:text-slate-500 text-center">暂无 Schema</div>
                ) : (
                  schemas.map((schema) => {
                    const isSelected = activeWorksheet.catalog === hoveredCatalog && activeWorksheet.schema === schema.name
                    return (
                      <button
                        key={schema.id}
                        onClick={() => handleSchemaChange(schema.name)}
                        className={`w-full flex items-center gap-2.5 px-2.5 py-2 text-left transition-colors ${
                          isSelected
                            ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                            : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                        }`}
                      >
                        <Database size={10} className="text-amber-500" />
                        <span className="text-micro leading-4 font-semibold truncate flex-1">{schema.name}</span>
                        {isSelected && <Check size={10} className="text-indigo-500 dark:text-indigo-400 shrink-0" />}
                      </button>
                    )
                  })
                )}
              </div>
            </div>
          </div>
        </div>,
        document.body
      )}
    </div>
  )

  // 底部面板
  const bottomPanel = (
    <BottomPanel
      cursor={cursor}
      activeDialect={activeDialect}
      dialects={DIALECTS}
      onDialectChange={setActiveDialect}
      executeResult={executeResult}
      executionLogs={executionLogs}
      isExecuting={isExecuting}
      collapsed={bottomPanelCollapsed}
      onCollapsedChange={setBottomPanelCollapsed}
      activeTab={bottomPanelTab}
      onActiveTabChange={setBottomPanelTab}
    />
  )

  // 右侧面板
  const rightPanel = (
    <RightRail
      selectedCatalog={activeWorksheet.catalog}
      selectedSchema={activeWorksheet.schema}
      requestOpenDatabase={requestOpenDatabase}
      onResetOpenRequest={() => setRequestOpenDatabase(false)}
    />
  )

  // 右键菜单分组
  const contextMenuGroups: ContextMenuGroup[] = [
    {
      id: 'editor',
      title: 'Code Context',
      items: [
        {
          id: 'run',
          label: 'Run Selection',
          icon: <Play size={14} className="text-emerald-500" />,
          onClick: handleExecute
        },
        {
          id: 'copy',
          label: 'Copy SQL',
          icon: <Copy size={14} />,
          onClick: () => {}
        }
      ]
    },
    {
      id: 'ai',
      title: 'AI Intelligent Agent',
      highlight: true,
      items: [
        {
          id: 'ai-optimize',
          label: 'Optimize',
          icon: <Zap size={14} className="text-indigo-500" />,
          onClick: () => {}
        },
        {
          id: 'ai-debug',
          label: 'Fix Bug',
          icon: <Bug size={14} className="text-rose-500" />,
          onClick: () => {}
        },
        {
          id: 'ai-explain',
          label: 'Explain',
          icon: <MessageSquare size={14} className="text-amber-500" />,
          onClick: () => {}
        }
      ]
    }
  ]

  return (
    <BaseEditor
      tabs={tabs}
      activeTabId={activeTabId}
      onTabChange={setActiveTabId}
      onAddTab={() => {
        const newIndex = tabCounter + 1
        setTabCounter(newIndex)
        const newId = String(Date.now())
        const newWorksheet = createNewWorksheet(newId, newIndex)
        setWorksheets(prev => [...prev, newWorksheet])
        setActiveTabId(newId)
      }}
      onTabClose={(id) => {
        // 至少保留一个 tab
        if (worksheets.length <= 1) return

        const index = worksheets.findIndex(w => w.id === id)
        setWorksheets(prev => prev.filter(w => w.id !== id))

        // 如果关闭的是当前 tab，切换到相邻 tab
        if (activeTabId === id) {
          const newIndex = index > 0 ? index - 1 : 0
          const newActiveId = worksheets[newIndex === index ? newIndex + 1 : newIndex]?.id
          if (newActiveId) setActiveTabId(newActiveId)
        }
      }}
      toolbarLeft={toolbarLeft}
      toolbarRight={toolbarRight}
      bottomPanel={bottomPanel}
      rightPanel={rightPanel}
      contextMenuGroups={contextMenuGroups}
    >
      <div className="flex h-full w-full">
        <div className="flex-1 min-w-0 h-full">
          <Editor
            height="100%"
            defaultLanguage="sql"
            theme={isDark ? 'datapillar-dark' : 'datapillar-light'}
            beforeMount={(monaco) => {
              // Monaco 默认主题是 light；在 mount 前注册自定义主题并交给 theme prop 管控，避免深色模式进入时闪白。
              defineDatapillarMonacoThemes(monaco)
            }}
            loading={
              <div className={`h-full w-full flex items-center justify-center ${isDark ? 'bg-slate-900 text-slate-400' : 'bg-white text-slate-500'}`}>
                <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
              </div>
            }
            value={activeWorksheet.code}
            onChange={(value) => {
              setWorksheets(prev => prev.map(w =>
                w.id === activeTabId ? { ...w, code: value || '' } : w
              ))
            }}
            onMount={(editor, monaco) => {
              setEditorInstance(editor)
              setMonacoInstance(monaco)
              editor.onDidChangeCursorPosition(e => setCursor({ ln: e.position.lineNumber, col: e.position.column }))

              // 获取编辑器 DOM 容器，添加拖放事件监听
              const editorDom = editor.getDomNode()
              if (editorDom) {
                editorDom.addEventListener('dragover', (e: DragEvent) => {
                  if (e.dataTransfer?.types.includes('application/x-table-drag')) {
                    e.preventDefault()
                    e.stopPropagation()
                    e.dataTransfer.dropEffect = 'copy'
                  }
                })

                editorDom.addEventListener('drop', (e: DragEvent) => {
                  const tableData = e.dataTransfer?.getData('application/x-table-drag')
                  if (tableData) {
                    e.preventDefault()
                    e.stopPropagation()
                    const { catalog, schema, table } = JSON.parse(tableData)
                    const sql = `SELECT *\nFROM ${catalog}.${schema}.${table}`
                    const position = editor.getPosition()
                    if (position) {
                      editor.executeEdits('drag-drop', [{
                        range: {
                          startLineNumber: position.lineNumber,
                          startColumn: position.column,
                          endLineNumber: position.lineNumber,
                          endColumn: position.column
                        },
                        text: sql
                      }])
                      editor.focus()
                    }
                  }
                })
              }
            }}
            options={{
              fontSize: 14,
              fontFamily: "Menlo, 'PingFang SC', 'Microsoft YaHei', Consolas, monospace",
              minimap: {
                enabled: false, // 关闭内置 minimap，使用自定义组件
              },
              contextmenu: false, // 禁用内置右键菜单，使用自定义菜单
              scrollbar: {
                vertical: 'auto',
                verticalScrollbarSize: 10,
                verticalSliderSize: 6,
              },
              padding: { top: 12 },
              lineNumbersMinChars: 3,
              renderLineHighlight: 'none',
              scrollBeyondLastLine: false,
              cursorHeight: 16,
              automaticLayout: true,
            }}
          />
        </div>
        {/* 自定义 Minimap */}
        <Minimap
          editor={editorInstance}
          monaco={monacoInstance}
          width={220}
          scale={0.35}
          fontSize={14}
        />
      </div>
    </BaseEditor>
  )
}
