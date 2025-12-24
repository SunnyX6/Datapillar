/**
 * Table 表单组件
 */

import { useEffect, useState, useRef, useCallback } from 'react'
import { ChevronDown } from 'lucide-react'
import {
  parseDDL,
  resolveDialect,
  DIALECT_LABELS,
  type ParsedColumn,
  type ParsedProperty
} from '@/services/sqlParserService'

type TableColumn = ParsedColumn
type TableProperty = ParsedProperty

interface CreateTableFormProps {
  parentName: string
  provider?: string
  onDDLButtonRender?: (button: React.ReactNode) => void
  onOverlayRender?: (overlay: React.ReactNode) => void
}

// Gravitino 统一数据类型
const GRAVITINO_DATA_TYPES = [
  { value: 'BOOLEAN', label: 'BOOLEAN' },
  { value: 'BYTE', label: 'BYTE' },
  { value: 'SHORT', label: 'SHORT' },
  { value: 'INTEGER', label: 'INTEGER' },
  { value: 'INT', label: 'INT' },
  { value: 'LONG', label: 'LONG' },
  { value: 'BIGINT', label: 'BIGINT' },
  { value: 'FLOAT', label: 'FLOAT' },
  { value: 'DOUBLE', label: 'DOUBLE' },
  { value: 'DECIMAL', label: 'DECIMAL' },
  { value: 'STRING', label: 'STRING' },
  { value: 'VARCHAR', label: 'VARCHAR' },
  { value: 'CHAR', label: 'CHAR' },
  { value: 'BINARY', label: 'BINARY' },
  { value: 'DATE', label: 'DATE' },
  { value: 'TIME', label: 'TIME' },
  { value: 'TIMESTAMP', label: 'TIMESTAMP' },
  { value: 'TIMESTAMP_TZ', label: 'TIMESTAMP WITH TIMEZONE' },
  { value: 'ARRAY', label: 'ARRAY' },
  { value: 'MAP', label: 'MAP' },
  { value: 'STRUCT', label: 'STRUCT' },
  { value: 'UUID', label: 'UUID' }
]

/**
 * DDL 输入覆盖层组件
 * 独立管理 textarea 状态，避免父组件重渲染导致光标丢失
 */
interface DDLOverlayProps {
  ddlExpanded: boolean
  dialectLabel: string
  defaultValue: string
  parseError: string | null
  onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void
}

function DDLOverlay({ ddlExpanded, dialectLabel, defaultValue, parseError, onChange }: DDLOverlayProps) {
  const [localValue, setLocalValue] = useState(defaultValue)

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setLocalValue(e.target.value)
    onChange(e)
  }

  return (
    <div
      className={`absolute inset-0 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-700 shadow-lg transition-transform duration-300 ease-out flex flex-col ${
        ddlExpanded ? 'translate-y-0' : 'translate-y-full'
      }`}
    >
      {/* Header */}
      <div className="flex-shrink-0 p-4 border-b border-slate-200 dark:border-slate-700">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-700 dark:text-slate-200">DDL 快捷导入</span>
          <span className="text-legal text-indigo-600 dark:text-indigo-400">{dialectLabel}</span>
        </div>
      </div>

      {/* Textarea - 自动填充剩余空间 */}
      <div className="flex-1 p-4 overflow-hidden">
        <textarea
          value={localValue}
          onChange={handleChange}
          placeholder="粘贴 CREATE TABLE 语句，自动解析表名、表描述、列定义和表参数"
          className="w-full h-full px-3 py-2 text-xs bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-mono resize-none"
          autoFocus={ddlExpanded}
        />
      </div>

      {/* Error message */}
      {parseError && (
        <div className="flex-shrink-0 px-4 pb-4">
          <p className="text-legal text-red-500">{parseError}</p>
        </div>
      )}
    </div>
  )
}

export function CreateTableForm({ parentName, provider, onDDLButtonRender, onOverlayRender }: CreateTableFormProps) {
  const [tableName, setTableName] = useState('')
  const [tableComment, setTableComment] = useState('')
  const [userDdlInput, setUserDdlInput] = useState('')
  const [parseError, setParseError] = useState<string | null>(null)
  const [columns, setColumns] = useState<TableColumn[]>([])
  const [properties, setProperties] = useState<TableProperty[]>([])
  const [ddlExpanded, setDdlExpanded] = useState(false)

  const dialect = resolveDialect(provider)
  const dialectLabel = DIALECT_LABELS[dialect] || dialect

  // 使用 ref 保存最新的 ddl 值，避免 overlay useEffect 依赖 userDdlInput 导致重渲染
  const ddlInputRef = useRef(userDdlInput)
  useEffect(() => {
    ddlInputRef.current = userDdlInput
  }, [userDdlInput])

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
        setColumns(parsed.columns)
        setProperties(parsed.properties)
        setParseError(null)
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '解析 DDL 失败'
        setParseError(message)
      }
    }, 400)
    return () => clearTimeout(handler)
  }, [userDdlInput, provider])

  // 渲染 DDL 按钮到 footer 左侧
  useEffect(() => {
    if (onDDLButtonRender) {
      onDDLButtonRender(
        <button
          type="button"
          onClick={() => setDdlExpanded((prev) => !prev)}
          className="flex items-center gap-1 text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition-colors"
        >
          <ChevronDown size={14} className={ddlExpanded ? '' : 'rotate-180'} />
          <span>DDL 快捷导入</span>
        </button>
      )
    }
  }, [ddlExpanded, onDDLButtonRender])

  // 渲染 DDL 抽屉到内容覆盖层
  useEffect(() => {
    if (onOverlayRender) {
      onOverlayRender(
        <DDLOverlay
          ddlExpanded={ddlExpanded}
          dialectLabel={dialectLabel}
          defaultValue={ddlInputRef.current}
          parseError={parseError}
          onChange={handleDdlChange}
        />
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

  const handleAddProperty = () => {
    setProperties((prev) => [...prev, { id: `prop_${Date.now()}`, key: '', value: '' }])
  }

  const handleDeleteProperty = (id: string) => {
    setProperties((prev) => prev.filter((p) => p.id !== id))
  }

  const handlePropertyChange = (id: string, field: 'key' | 'value', value: string) => {
    setProperties((prev) => prev.map((p) => (p.id === id ? { ...p, [field]: value } : p)))
  }

  const renderTypeOptions = (value: string) => {
    const hasValue = GRAVITINO_DATA_TYPES.some((item) => item.value === value)
    return (
      <>
        {GRAVITINO_DATA_TYPES.map((type) => (
          <option key={type.value} value={type.value}>
            {type.label}
          </option>
        ))}
        {!hasValue && value && (
          <option value={value} className="text-red-500">
            不支持的数据类型: {value}
          </option>
        )}
      </>
    )
  }

  return (
    <div className="space-y-4">
      {/* 基础信息 */}
        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              表名 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              placeholder="例如: fact_orders"
              value={tableName}
              onChange={(e) => setTableName(e.target.value)}
              className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
            />
            <p className="text-xs text-slate-400">将创建于 Schema「{parentName}」</p>
          </div>
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">表描述</label>
            <textarea
              placeholder="表用途说明"
              value={tableComment}
              onChange={(e) => setTableComment(e.target.value)}
              rows={2}
              className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none"
            />
          </div>
        </div>

        {/* 列定义 */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">列定义</label>
            <button
              type="button"
              onClick={handleAddColumn}
              className="px-2 py-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/30 rounded-md hover:bg-indigo-100 dark:hover:bg-indigo-500/20 transition-colors"
            >
              + 添加列
            </button>
          </div>

          <div className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 overflow-hidden lg:min-h-[280px] xl:min-h-[360px]">
            {columns.length === 0 ? (
              <div className="py-8 lg:py-0 lg:h-full lg:min-h-[280px] xl:min-h-[360px] flex items-center justify-center text-xs text-slate-400 dark:text-slate-500">
                暂无列定义，点击"添加列"或使用 DDL 快捷导入
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-xs border-separate border-spacing-0">
                  <thead>
                    <tr className="text-legal font-semibold text-slate-500 uppercase tracking-widest bg-slate-50 dark:bg-slate-800/50">
                      <th className="px-3 py-2 text-left w-[28%]">列名</th>
                      <th className="px-3 py-2 text-left w-[24%]">数据类型</th>
                      <th className="px-3 py-2 text-left w-[12%]">可空</th>
                      <th className="px-3 py-2 text-left w-[26%]">注释</th>
                      <th className="px-3 py-2 text-right w-[10%]">操作</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {columns.map((column) => (
                      <tr key={column.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                        <td className="px-3 py-2 align-middle">
                          <div className="relative">
                            <input
                              type="text"
                              placeholder="列名"
                              value={column.name}
                              onChange={(e) => handleColumnChange(column.id, 'name', e.target.value)}
                              className="w-full px-2 py-1 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                            />
                            {column.isPartition && (
                              <span className="absolute -top-1.5 -right-1.5 px-1 py-px text-micro font-medium text-amber-700 dark:text-amber-400 bg-amber-100 dark:bg-amber-500/20 border border-amber-200 dark:border-amber-500/30 rounded">
                                分区
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-2 align-middle">
                          <select
                            value={column.dataType}
                            onChange={(e) => handleColumnChange(column.id, 'dataType', e.target.value)}
                            className="w-full px-2 py-1 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                          >
                            {renderTypeOptions(column.dataType)}
                          </select>
                        </td>
                        <td className="px-3 py-2 align-middle">
                          <input
                            type="checkbox"
                            checked={column.nullable}
                            onChange={(e) => handleColumnChange(column.id, 'nullable', e.target.checked)}
                            className="size-3 text-indigo-600 rounded border-slate-300 focus:ring-indigo-500"
                          />
                        </td>
                        <td className="px-3 py-2 align-middle">
                          <input
                            type="text"
                            placeholder="列说明"
                            value={column.comment}
                            onChange={(e) => handleColumnChange(column.id, 'comment', e.target.value)}
                            className="w-full px-2 py-1 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                          />
                        </td>
                        <td className="px-3 py-2 align-middle text-right">
                          <button
                            type="button"
                            onClick={() => handleDeleteColumn(column.id)}
                            className="p-1 text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10 rounded transition-colors"
                            aria-label="删除列"
                          >
                            <svg className="size-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>

        {/* 表参数 */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <div>
              <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">表参数</label>
            </div>
            <button
              type="button"
              onClick={handleAddProperty}
              className="px-2 py-1 text-xs font-semibold text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/30 rounded-md hover:bg-indigo-100 dark:hover:bg-indigo-500/20 transition-colors"
            >
              + 添加参数
            </button>
          </div>
          {properties.length === 0 ? (
            <div className="py-4 text-center text-xs text-slate-400 dark:text-slate-500 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900">
              暂无表参数
            </div>
          ) : (
            <div className="space-y-2">
              {properties.map((prop) => (
                <div key={prop.id} className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="参数名"
                    value={prop.key}
                    onChange={(e) => handlePropertyChange(prop.id, 'key', e.target.value)}
                    className="flex-1 px-2 py-1.5 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                  <span className="text-slate-400">=</span>
                  <input
                    type="text"
                    placeholder="参数值"
                    value={prop.value}
                    onChange={(e) => handlePropertyChange(prop.id, 'value', e.target.value)}
                    className="flex-1 px-2 py-1.5 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                  <button
                    type="button"
                    onClick={() => handleDeleteProperty(prop.id)}
                    className="p-1 text-red-500 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-500/10 rounded transition-colors"
                    aria-label="删除参数"
                  >
                    <svg className="size-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
    </div>
  )
}
