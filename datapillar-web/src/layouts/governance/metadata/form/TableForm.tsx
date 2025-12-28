/**
 * Table 表单组件 - 左右布局
 * 左侧：基础信息（物理表名、描述、智能提示）
 * 右侧：列定义区域（支持拖拽排序）
 */

import { useEffect, useState, useRef, useCallback, forwardRef, useImperativeHandle, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import { GripVertical, Plus, Trash2, Key, Sparkles, Layers, List, Hash, Type, ChevronLeft, ChevronRight, Pin } from 'lucide-react'
import { toast } from 'sonner'
import {
  parseDDL,
  resolveDialect,
  DIALECT_LABELS,
  type ParsedColumn,
  type ParsedProperty
} from '@/services/sqlParserService'
import { DataTypeSelector, parseDataTypeString, buildDataTypeString } from '@/components/ui'
import { fetchValueDomains, type ValueDomainDTO } from '@/services/oneMetaSemanticService'

/** 数据类型等价组（同组内的类型视为兼容） */
const TYPE_EQUIVALENTS: string[][] = [
  ['STRING', 'VARCHAR', 'TEXT', 'CHAR', 'FIXEDCHAR'],
  ['INT', 'INTEGER'],
  ['BIGINT', 'LONG'],
  ['FLOAT', 'REAL'],
  ['DOUBLE', 'DOUBLE PRECISION'],
  ['BOOLEAN', 'BOOL']
]

/** 检查列数据类型与值域数据类型是否兼容（忽略大小写，考虑等价类型） */
function isDataTypeCompatible(columnDataType: string, domainDataType?: string): boolean {
  if (!domainDataType) return false // 值域未指定数据类型，不允许关联
  // 提取基础类型（去掉括号中的参数）
  const normalizeType = (type: string) => type.toUpperCase().replace(/\(.*\)$/, '')
  const colType = normalizeType(columnDataType)
  const domType = normalizeType(domainDataType)

  // 完全相同
  if (colType === domType) return true

  // 检查是否在同一等价组
  for (const group of TYPE_EQUIVALENTS) {
    if (group.includes(colType) && group.includes(domType)) return true
  }

  return false
}

type TableColumn = ParsedColumn & { isPrimaryKey?: boolean; valueDomainCode?: string }
type TableProperty = ParsedProperty

/** 表单数据接口 */
export interface TableFormData {
  name: string
  comment: string
  columns: Array<{
    name: string
    type: string
    comment?: string
    nullable?: boolean
    valueDomainCode?: string
  }>
  properties: Record<string, string>
}

/** 表单操作句柄 */
export interface TableFormHandle {
  getData: () => TableFormData
  validate: () => boolean
}

interface CreateTableFormProps {
  parentName: string
  provider?: string
  onDDLButtonRender?: (button: React.ReactNode) => void
  onOverlayRender?: (overlay: React.ReactNode) => void
  /** 编辑模式时的初始数据 */
  initialData?: {
    name: string
    comment?: string
    columns: Array<{
      name: string
      type: string
      comment?: string
      nullable?: boolean
      valueDomainCode?: string
    }>
  }
}

/** 列编辑行组件 */
function ColumnRow({
  column,
  index,
  valueDomains,
  onUpdate,
  onDelete,
  onDragStart,
  onDragOver,
  onDrop,
  isDragging
}: {
  column: TableColumn
  index: number
  valueDomains: ValueDomainDTO[]
  onUpdate: (field: keyof TableColumn, value: string | boolean) => void
  onDelete: () => void
  onDragStart: (e: React.DragEvent, index: number) => void
  onDragOver: (e: React.DragEvent) => void
  onDrop: (e: React.DragEvent, index: number) => void
  isDragging: boolean
}) {
  const [hoveredDomain, setHoveredDomain] = useState<string | null>(null)
  const [cardPos, setCardPos] = useState<{ top: number; left: number } | null>(null)
  const hoverItemRef = useRef<HTMLDivElement>(null)
  const hoverTimeout = useRef<NodeJS.Timeout | null>(null)
  const cardRef = useRef<HTMLDivElement>(null)
  const isCardHovered = useRef(false)

  const handleItemMouseEnter = (domainCode: string) => {
    if (hoverTimeout.current) clearTimeout(hoverTimeout.current)
    setHoveredDomain(domainCode)
  }

  const handleItemMouseLeave = () => {
    hoverTimeout.current = setTimeout(() => {
      if (!isCardHovered.current) setHoveredDomain(null)
    }, 100)
  }

  const handleCardMouseEnter = () => {
    isCardHovered.current = true
    if (hoverTimeout.current) clearTimeout(hoverTimeout.current)
  }

  const handleCardMouseLeave = () => {
    isCardHovered.current = false
    setHoveredDomain(null)
  }

  useLayoutEffect(() => {
    if (!hoveredDomain || !hoverItemRef.current) return
    const rect = hoverItemRef.current.getBoundingClientRect()
    setCardPos({
      top: rect.top,
      left: rect.right + 8
    })
    return () => setCardPos(null)
  }, [hoveredDomain])

  /** 获取值域类型图标 */
  const getDomainIcon = (domainType?: string) => {
    switch (domainType?.toUpperCase()) {
      case 'ENUM':
        return <List size={10} className="text-purple-500" />
      case 'RANGE':
        return <Hash size={10} className="text-blue-500" />
      case 'REGEX':
        return <Type size={10} className="text-amber-500" />
      default:
        return <Pin size={10} className="text-indigo-400" />
    }
  }

  /** 获取值域类型标签 */
  const getDomainTypeLabel = (domainType?: string) => {
    switch (domainType?.toUpperCase()) {
      case 'ENUM':
        return '枚举'
      case 'RANGE':
        return '范围'
      case 'REGEX':
        return '正则'
      default:
        return '未知'
    }
  }

  const activeHoveredDomain = valueDomains.find((d) => d.domainCode === hoveredDomain)

  return (
    <div
      draggable
      onDragStart={(e) => onDragStart(e, index)}
      onDragOver={onDragOver}
      onDrop={(e) => onDrop(e, index)}
      className={`group relative w-full min-w-0 px-1.5 py-1.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg transition-all ${
        isDragging ? 'opacity-50 border-blue-400 shadow-sm' : 'hover:border-blue-300 dark:hover:border-blue-600 hover:shadow-sm'
      }`}
    >
      {/* 值域标签 - 左上角 */}
      {column.valueDomainCode && (
        <div className="absolute -top-1.5 left-6 z-10">
          <span
            className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-micro font-medium bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-full border border-indigo-200 dark:border-indigo-800 cursor-pointer hover:bg-indigo-200 dark:hover:bg-indigo-900/60"
            title={`值域: ${valueDomains.find((d) => d.domainCode === column.valueDomainCode)?.domainName || column.valueDomainCode}`}
            onClick={() => onUpdate('valueDomainCode', '')}
          >
            {getDomainIcon(valueDomains.find((d) => d.domainCode === column.valueDomainCode)?.domainType)}
            <span className="max-w-16 truncate">{valueDomains.find((d) => d.domainCode === column.valueDomainCode)?.domainName || column.valueDomainCode}</span>
            <span className="text-indigo-400 hover:text-indigo-600">×</span>
          </span>
        </div>
      )}

      <div className="grid grid-cols-[auto_minmax(0,1fr)_8.5rem_auto_auto_minmax(0,1fr)_auto_auto] items-center gap-1.5">
        {/* 拖拽手柄 */}
        <div className="flex items-center justify-center cursor-grab active:cursor-grabbing text-slate-300 dark:text-slate-600 hover:text-slate-400 dark:hover:text-slate-500">
          <GripVertical size={12} />
        </div>

        {/* 列名 */}
        <input
          type="text"
          value={column.name}
          onChange={(e) => onUpdate('name', e.target.value)}
          placeholder="列名"
          title={column.name}
          className="h-7 w-full min-w-0 px-2 text-xs text-slate-800 dark:text-slate-200 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
        />

        {/* 数据类型 */}
        <div className="w-full">
          <DataTypeSelector
            value={parseDataTypeString(column.dataType)}
            onChange={(value) => onUpdate('dataType', buildDataTypeString(value))}
            size="default"
            triggerClassName="h-7 w-full justify-between px-2 py-0 rounded-md"
            labelClassName="text-xs"
          />
        </div>

        {/* 主键 */}
        <button
          type="button"
          onClick={() => onUpdate('isPrimaryKey', !column.isPrimaryKey)}
          className={`h-7 w-7 inline-flex items-center justify-center rounded-md transition-colors ${
            column.isPrimaryKey
              ? 'text-amber-600 bg-amber-50 dark:bg-amber-900/30'
              : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 hover:bg-slate-100 dark:hover:bg-slate-700'
          }`}
          title="主键"
          aria-label="主键"
        >
          <Key size={12} />
        </button>

        {/* 可空 */}
        <label className="h-7 inline-flex items-center justify-center gap-0.5 px-0.5 text-xs text-slate-600 dark:text-slate-300 whitespace-nowrap select-none">
          <input
            type="checkbox"
            checked={!column.nullable}
            onChange={(e) => onUpdate('nullable', !e.target.checked)}
            className="w-3.5 h-3.5 text-blue-600 rounded border-slate-300 dark:border-slate-600 focus:ring-blue-500"
          />
          <span>必填</span>
        </label>

        {/* 列注释 */}
        <input
          type="text"
          value={column.comment || ''}
          onChange={(e) => onUpdate('comment', e.target.value)}
          placeholder="注释"
          title={column.comment || ''}
          className="h-7 w-full min-w-0 px-2 text-xs text-slate-800 dark:text-slate-200 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500"
        />

        {/* 值域图钉 */}
        <div className="relative">
          <button
            type="button"
            onClick={(e) => {
              const dropdown = e.currentTarget.nextElementSibling as HTMLDivElement
              if (dropdown) {
                dropdown.classList.toggle('hidden')
              }
            }}
            className={`h-7 w-7 inline-flex items-center justify-center rounded-md transition-colors ${
              column.valueDomainCode
                ? 'text-indigo-600 bg-indigo-50 dark:bg-indigo-900/30'
                : 'text-slate-400 dark:text-slate-500 hover:text-indigo-500 hover:bg-slate-100 dark:hover:bg-slate-700'
            }`}
            title="关联值域"
            aria-label="关联值域"
          >
            <Pin size={12} />
          </button>
          <div
            className="hidden absolute right-0 top-full mt-1 z-50 w-44 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-xl overflow-hidden"
            onMouseLeave={(e) => {
              e.currentTarget.classList.add('hidden')
              setHoveredDomain(null)
            }}
          >
            {/* 头部 */}
            <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900">
              <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">选择值域</span>
            </div>
            {/* 列表 */}
            <div className="max-h-40 overflow-y-auto py-1">
              {valueDomains.map((domain) => (
                <div
                  key={domain.domainCode}
                  ref={hoveredDomain === domain.domainCode ? hoverItemRef : null}
                  onMouseEnter={() => handleItemMouseEnter(domain.domainCode)}
                  onMouseLeave={handleItemMouseLeave}
                  className={`flex items-center justify-between px-3 py-1.5 text-xs cursor-pointer transition-colors ${
                    hoveredDomain === domain.domainCode
                      ? 'bg-blue-50 dark:bg-blue-900/30'
                      : column.valueDomainCode === domain.domainCode
                        ? 'text-blue-600 font-medium bg-blue-50/50 dark:bg-blue-900/20'
                        : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700'
                  }`}
                  onClick={(e) => {
                    // 校验数据类型是否匹配
                    if (!domain.dataType) {
                      toast.error(`值域 ${domain.domainName} 未指定数据类型，无法关联`)
                      return
                    }
                    if (!isDataTypeCompatible(column.dataType, domain.dataType)) {
                      toast.error(`数据类型不匹配：列类型为 ${column.dataType.toUpperCase()}，值域类型为 ${domain.dataType.toUpperCase()}`)
                      return
                    }
                    onUpdate('valueDomainCode', domain.domainCode)
                    ;(e.currentTarget.parentElement?.parentElement as HTMLDivElement).classList.add('hidden')
                    setHoveredDomain(null)
                  }}
                >
                  <span className="truncate">{domain.domainName}</span>
                  <ChevronRight size={12} className={`flex-shrink-0 ml-1 ${hoveredDomain === domain.domainCode ? 'text-blue-400' : 'text-slate-300'}`} />
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 删除 */}
        <button
          type="button"
          onClick={onDelete}
          className="h-7 w-7 inline-flex items-center justify-center text-slate-300 dark:text-slate-600 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-md transition-colors opacity-0 group-hover:opacity-100"
          title="删除"
          aria-label="删除"
        >
          <Trash2 size={12} />
        </button>
      </div>

      {/* 值域详情卡片 - Portal */}
      {activeHoveredDomain && cardPos && createPortal(
        <div
          ref={cardRef}
          style={{ top: cardPos.top, left: cardPos.left }}
          className="fixed z-[1000001] w-56 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-2xl animate-in fade-in-0 slide-in-from-left-2 duration-150"
          onMouseEnter={handleCardMouseEnter}
          onMouseLeave={handleCardMouseLeave}
        >
          {/* 卡片头部 */}
          <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 rounded-t-xl">
            <div className="flex items-center gap-2">
              {getDomainIcon(activeHoveredDomain.domainType)}
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 truncate">{activeHoveredDomain.domainName}</span>
            </div>
            <div className="flex items-center gap-2 mt-1">
              <span className="text-micro px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-700 text-slate-500 dark:text-slate-400">
                {getDomainTypeLabel(activeHoveredDomain.domainType)}
              </span>
              {activeHoveredDomain.dataType && (
                <span className="text-micro px-1.5 py-0.5 rounded font-mono bg-cyan-50 dark:bg-cyan-900/30 text-cyan-600 dark:text-cyan-400">
                  {activeHoveredDomain.dataType}
                </span>
              )}
              {activeHoveredDomain.domainLevel && (
                <span className={`text-micro px-1.5 py-0.5 rounded ${activeHoveredDomain.domainLevel === 'BUILTIN' ? 'bg-amber-100 text-amber-600' : 'bg-blue-100 text-blue-600'}`}>
                  {activeHoveredDomain.domainLevel === 'BUILTIN' ? '内置' : '业务'}
                </span>
              )}
            </div>
          </div>
          {/* 枚举项列表 */}
          {activeHoveredDomain.items.length > 0 && (
            <div className="px-3 py-2">
              <p className="text-micro font-medium text-slate-400 dark:text-slate-500 mb-1.5">枚举值 ({activeHoveredDomain.items.length})</p>
              <div className="max-h-32 overflow-y-auto space-y-1">
                {activeHoveredDomain.items.slice(0, 10).map((item) => (
                  <div key={item.value} className="flex items-center gap-2 text-micro">
                    <span className="font-mono text-slate-600 dark:text-slate-300">{item.value}</span>
                    {item.label && <span className="text-slate-400 truncate">({item.label})</span>}
                  </div>
                ))}
                {activeHoveredDomain.items.length > 10 && (
                  <p className="text-micro text-slate-400">...还有 {activeHoveredDomain.items.length - 10} 项</p>
                )}
              </div>
            </div>
          )}
        </div>,
        document.body
      )}
    </div>
  )
}

export const CreateTableForm = forwardRef<TableFormHandle, CreateTableFormProps>(
  ({ parentName: _parentName, provider, onDDLButtonRender, onOverlayRender, initialData }, ref) => {
  const [tableName, setTableName] = useState(initialData?.name || '')
  const [tableComment, setTableComment] = useState(initialData?.comment || '')
  const [userDdlInput, setUserDdlInput] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)
  const [columns, setColumns] = useState<TableColumn[]>(() => {
    if (initialData?.columns) {
      return initialData.columns.map((col) => ({
        name: col.name,
        dataType: col.type.toUpperCase(),
        comment: col.comment,
        nullable: col.nullable ?? true,
        isPrimaryKey: false,
        valueDomainCode: col.valueDomainCode
      }))
    }
    return []
  })
  const [properties, setProperties] = useState<TableProperty[]>([])
  const [ddlExpanded, setDdlExpanded] = useState(false)
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null)
  const [valueDomains, setValueDomains] = useState<ValueDomainDTO[]>([])
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false)

  const dialect = resolveDialect(provider)
  const dialectLabel = DIALECT_LABELS[dialect] || dialect

  const ddlInputRef = useRef(userDdlInput)
  useEffect(() => {
    ddlInputRef.current = userDdlInput
  }, [userDdlInput])

  // 暴露给父组件的方法
  useImperativeHandle(ref, () => ({
    getData: () => ({
      name: tableName,
      comment: tableComment,
      columns: columns.map((col) => ({
        name: col.name,
        type: col.dataType,
        comment: col.comment,
        nullable: col.nullable,
        valueDomainCode: col.valueDomainCode
      })),
      properties: properties.reduce((acc, prop) => {
        if (prop.key) acc[prop.key] = prop.value
        return acc
      }, {} as Record<string, string>)
    }),
    validate: () => {
      if (!tableName.trim()) {
        toast.error('请输入物理表名')
        return false
      }
      if (columns.length === 0) {
        toast.error('请至少添加一个列')
        return false
      }
      const emptyColumns = columns.filter((col) => !col.name.trim())
      if (emptyColumns.length > 0) {
        toast.error('存在未填写名称的列')
        return false
      }
      return true
    }
  }))

  // 加载值域列表
  useEffect(() => {
    fetchValueDomains(0, 100)
      .then((result) => setValueDomains(result.items))
      .catch(console.error)
  }, [])

  const handleDdlChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setUserDdlInput(e.target.value)
  }, [])

  // DDL 解析
  useEffect(() => {
    if (!userDdlInput.trim()) return

    const handler = setTimeout(() => {
      try {
        const parsed = parseDDL(userDdlInput, provider)
        if (parsed.tableName) setTableName(parsed.tableName)
        if (parsed.tableComment) setTableComment(parsed.tableComment)
        setColumns(parsed.columns.map((col) => ({ ...col, isPrimaryKey: false })))
        setProperties(parsed.properties)
        setParseError(null)
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '解析 DDL 失败'
        setParseError(message)
      }
    }, 400)
    return () => clearTimeout(handler)
  }, [userDdlInput, provider])

  // 渲染 DDL 按钮
  useEffect(() => {
    if (onDDLButtonRender) {
      onDDLButtonRender(
        <button
          type="button"
          onClick={() => setDdlExpanded((prev) => !prev)}
          className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-xl hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          DDL 快速导入
        </button>
      )
    }
  }, [ddlExpanded, onDDLButtonRender])

  // 渲染 DDL 抽屉
  useEffect(() => {
    if (onOverlayRender) {
      onOverlayRender(
        <div
          className={`absolute inset-0 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-700 shadow-lg transition-transform duration-300 ease-out flex flex-col ${
            ddlExpanded ? 'translate-y-0' : 'translate-y-full'
          }`}
        >
          <div className="flex-shrink-0 p-4 border-b border-slate-200 dark:border-slate-700">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">DDL 快捷导入</span>
              <span className="text-legal text-indigo-600 dark:text-indigo-400">{dialectLabel}</span>
            </div>
          </div>
          <div className="flex-1 p-4 overflow-hidden">
            <textarea
              defaultValue={ddlInputRef.current}
              onChange={handleDdlChange}
              placeholder="粘贴 CREATE TABLE 语句，自动解析表名、表描述、列定义"
              className="w-full h-full px-3 py-2 text-xs bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-mono resize-none"
              autoFocus={ddlExpanded}
            />
          </div>
          {parseError && (
            <div className="flex-shrink-0 px-4 pb-4">
              <p className="text-legal text-red-500">{parseError}</p>
            </div>
          )}
        </div>
      )
    }
  }, [ddlExpanded, parseError, dialectLabel, onOverlayRender, handleDdlChange])

  const handleAddColumn = () => {
    setColumns((prev) => [
      ...prev,
      { id: `col_${Date.now()}`, name: '', dataType: 'STRING', comment: '', nullable: true }
    ])
  }

  const handleDeleteColumn = (id: string) => {
    setColumns((prev) => prev.filter((col) => col.id !== id))
  }

  const handleColumnChange = (id: string, field: keyof TableColumn, value: string | boolean) => {
    setColumns((prev) => prev.map((col) => (col.id === id ? { ...col, [field]: value } : col)))
  }

  // 拖拽排序
  const handleDragStart = (e: React.DragEvent, index: number) => {
    setDraggedIndex(index)
    e.dataTransfer.effectAllowed = 'move'
  }

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
  }

  const handleDrop = (e: React.DragEvent, dropIndex: number) => {
    e.preventDefault()
    if (draggedIndex === null || draggedIndex === dropIndex) return

    setColumns((prev) => {
      const newColumns = [...prev]
      const [removed] = newColumns.splice(draggedIndex, 1)
      newColumns.splice(dropIndex, 0, removed)
      return newColumns
    })
    setDraggedIndex(null)
  }

  // 表参数操作
  const handleAddProperty = () => {
    setProperties((prev) => [...prev, { id: `prop_${Date.now()}`, key: '', value: '' }])
  }

  const handleDeleteProperty = (id: string) => {
    setProperties((prev) => prev.filter((p) => p.id !== id))
  }

  const handlePropertyChange = (id: string, field: 'key' | 'value', value: string) => {
    setProperties((prev) => prev.map((p) => (p.id === id ? { ...p, [field]: value } : p)))
  }

  return (
    <div className="flex min-h-[500px]">
      {/* 左侧：配置面板（参考 ComponentLibrarySidebar 折叠态保留窄栏） */}
      {isLeftCollapsed ? (
        <div className="w-12 flex-shrink-0 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col items-center py-4">
          <button
            type="button"
            onClick={() => setIsLeftCollapsed(false)}
            className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
            title="展开配置"
            aria-label="展开配置"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      ) : (
        <div className="relative w-72 flex-shrink-0 pr-6 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
          <button
            type="button"
            onClick={() => setIsLeftCollapsed(true)}
            className="absolute top-2 -right-2 w-5 h-8 flex items-center justify-center bg-white/90 dark:bg-slate-900/90 border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg shadow-sm transition-colors z-20"
            title="收起配置"
            aria-label="收起配置"
          >
            <ChevronLeft size={14} className="text-slate-400" />
          </button>

          <div className="space-y-4 overflow-y-auto custom-scrollbar h-full">
            {/* 物理表名 */}
            <div className="space-y-1.5">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                物理表名 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={tableName}
                onChange={(e) => setTableName(e.target.value)}
                placeholder="例如: fact_order_sales"
                className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
              />
            </div>

            {/* 描述信息 */}
            <div className="space-y-1.5">
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">描述信息</label>
              <textarea
                value={tableComment}
                onChange={(e) => setTableComment(e.target.value)}
                placeholder="描述表的数据来源与核心业务用途..."
                rows={3}
                className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
              />
            </div>

            {/* 表参数 */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">表参数</label>
                <button
                  type="button"
                  onClick={handleAddProperty}
                  className="text-xs text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
                >
                  + 添加
                </button>
              </div>
              {properties.length === 0 ? (
                <div className="py-3 text-center text-xs text-slate-400 border border-dashed border-slate-200 dark:border-slate-700 rounded-lg">
                  暂无表参数
                </div>
              ) : (
                <div className="space-y-1.5 max-h-32 overflow-y-auto custom-scrollbar">
                  {properties.map((prop) => (
                    <div key={prop.id} className="flex items-center gap-1">
                      <input
                        type="text"
                        placeholder="参数名"
                        value={prop.key}
                        onChange={(e) => handlePropertyChange(prop.id, 'key', e.target.value)}
                        className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
                      />
                      <span className="text-slate-300 flex-shrink-0">=</span>
                      <input
                        type="text"
                        placeholder="参数值"
                        value={prop.value}
                        onChange={(e) => handlePropertyChange(prop.id, 'value', e.target.value)}
                        className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
                      />
                      <button
                        type="button"
                        onClick={() => handleDeleteProperty(prop.id)}
                        className="p-1 text-slate-300 hover:text-red-500 rounded transition-colors flex-shrink-0"
                      >
                        <Trash2 size={12} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 智能提示卡片 */}
            <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg">
              <div className="flex items-center gap-1.5 text-blue-600 dark:text-blue-400 mb-1.5">
                <Sparkles size={14} />
                <span className="text-xs font-semibold">智能提示</span>
              </div>
              <p className="text-xs text-blue-600/80 dark:text-blue-400/80 leading-relaxed">
                输入列名时，系统将自动匹配 One Meta 语义标准并建议最合适的物理类型。
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 右侧：列定义 */}
      <div className="flex-1 flex flex-col min-w-0 pl-6">
        {/* 列定义头部 */}
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-1.5">
            <Layers size={14} className="text-blue-600" />
            <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">列定义</span>
            <span className="text-xs text-slate-400">({columns.length})</span>
          </div>
          <button
            type="button"
            onClick={handleAddColumn}
            className="flex items-center gap-1 px-2 py-1 text-xs font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 transition-colors"
          >
            <Plus size={12} />
            添加物理列
          </button>
        </div>

        {/* 列列表 */}
        <div className="flex-1 overflow-y-auto overflow-x-hidden space-y-1.5 pr-1 custom-scrollbar">
          {columns.map((column, index) => (
            <ColumnRow
              key={column.id}
              column={column}
              index={index}
              valueDomains={valueDomains}
              onUpdate={(field, value) => handleColumnChange(column.id, field, value)}
              onDelete={() => handleDeleteColumn(column.id)}
              onDragStart={handleDragStart}
              onDragOver={handleDragOver}
              onDrop={handleDrop}
              isDragging={draggedIndex === index}
            />
          ))}

          {columns.length === 0 && (
            <div className="flex flex-col items-center justify-center py-10 text-slate-400">
              <Layers size={28} className="mb-2 opacity-50" />
              <p className="text-xs">暂无列定义</p>
              <p className="text-xs mt-1">点击"添加物理列"或使用 DDL 快速导入</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
})

CreateTableForm.displayName = 'CreateTableForm'
