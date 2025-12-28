/**
 * Schema 表单组件
 */

import { forwardRef, useImperativeHandle, useState } from 'react'
import { toast } from 'sonner'

interface CreateSchemaFormProps {
  parentName: string
}

export interface SchemaFormData {
  name: string
  comment: string
}

export interface SchemaFormHandle {
  getData: () => SchemaFormData
  validate: () => boolean
}

export const CreateSchemaForm = forwardRef<SchemaFormHandle, CreateSchemaFormProps>(
  ({ parentName: _parentName }, ref) => {
    const [formData, setFormData] = useState({
      name: '',
      comment: ''
    })

    useImperativeHandle(ref, () => ({
      getData: () => formData,
      validate: () => {
        if (!formData.name.trim()) {
          toast.error('请输入 Schema 名称')
          return false
        }
        return true
      }
    }))

    return (
      <div className="space-y-2.5">
        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            Schema 名称 <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            placeholder="例如: analytics_mart"
            value={formData.name}
            onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
            className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">描述 (可选)</label>
          <textarea
            placeholder="Schema 用途说明"
            value={formData.comment}
            onChange={(e) => setFormData((prev) => ({ ...prev, comment: e.target.value }))}
            rows={2}
            className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    )
  }
)

CreateSchemaForm.displayName = 'CreateSchemaForm'
