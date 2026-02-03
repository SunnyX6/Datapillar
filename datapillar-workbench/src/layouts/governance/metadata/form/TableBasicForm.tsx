/**
 * 表基础信息编辑表单
 * 用于编辑表名、描述和表参数
 */

import { useState, forwardRef, useImperativeHandle } from 'react'
import { Sparkles, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

/** 表参数类型 */
interface TableProperty {
  id: string
  key: string
  value: string
}

/** 表单数据接口 */
export interface TableBasicFormData {
  name: string
  comment: string
  properties: Record<string, string>
}

/** 表单操作句柄 */
export interface TableBasicFormHandle {
  getData: () => TableBasicFormData
  validate: () => boolean
}

interface TableBasicFormProps {
  /** 初始数据 */
  initialData: {
    name: string
    comment?: string
    properties?: Record<string, string>
  }
}

export const TableBasicForm = forwardRef<TableBasicFormHandle, TableBasicFormProps>(
  ({ initialData }, ref) => {
    const [tableName, setTableName] = useState(initialData.name)
    const [tableComment, setTableComment] = useState(initialData.comment || '')
    const [properties, setProperties] = useState<TableProperty[]>(() => {
      if (initialData.properties) {
        return Object.entries(initialData.properties).map(([key, value], index) => ({
          id: `prop_${index}`,
          key,
          value
        }))
      }
      return []
    })

    // 暴露给父组件的方法
    useImperativeHandle(ref, () => ({
      getData: () => ({
        name: tableName,
        comment: tableComment,
        properties: properties.reduce((acc, prop) => {
          if (prop.key) acc[prop.key] = prop.value
          return acc
        }, {} as Record<string, string>)
      }),
      validate: () => {
        if (!tableName.trim()) {
          toast.error('请输入表名')
          return false
        }
        return true
      }
    }))

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
      <div className="space-y-4">
        {/* 智能提示卡片 - 放在上方 */}
        <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg">
          <div className="flex items-center gap-1.5 text-blue-600 dark:text-blue-400 mb-1.5">
            <Sparkles size={14} />
            <span className="text-xs font-semibold">智能提示</span>
          </div>
          <p className="text-xs text-blue-600/80 dark:text-blue-400/80 leading-relaxed">
            修改表名可能影响依赖该表的 SQL 和 ETL 任务，请谨慎操作。表参数用于配置存储引擎、分区等高级属性。
          </p>
        </div>

        {/* 表名 */}
        <div className="space-y-1.5">
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            物理表名 <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={tableName}
            onChange={(e) => setTableName(e.target.value)}
            placeholder="例如: fact_order_sales"
            className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
          {tableName !== initialData.name && (
            <p className="text-xs text-amber-600 dark:text-amber-400">
              ⚠️ 修改表名可能影响依赖该表的 SQL 和 ETL 任务
            </p>
          )}
        </div>

        {/* 描述信息 */}
        <div className="space-y-1.5">
          <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
            描述信息
          </label>
          <textarea
            value={tableComment}
            onChange={(e) => setTableComment(e.target.value)}
            placeholder="精准的业务描述更有助于AI的生成..."
            rows={3}
            className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
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
            <div className="space-y-1.5 max-h-40 overflow-y-auto custom-scrollbar">
              {properties.map((prop) => (
                <div key={prop.id} className="flex items-center gap-1">
                  <input
                    type="text"
                    placeholder="参数名"
                    value={prop.key}
                    onChange={(e) => handlePropertyChange(prop.id, 'key', e.target.value)}
                    className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
                  />
                  <span className="text-slate-300 flex-shrink-0">=</span>
                  <input
                    type="text"
                    placeholder="参数值"
                    value={prop.value}
                    onChange={(e) => handlePropertyChange(prop.id, 'value', e.target.value)}
                    className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
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
      </div>
    )
  }
)

TableBasicForm.displayName = 'TableBasicForm'
