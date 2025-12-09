/**
 * Schema 表单组件
 */

interface CreateSchemaFormProps {
  parentName: string
}

export function CreateSchemaForm({ parentName: _parentName }: CreateSchemaFormProps) {
  return (
    <div className="space-y-2.5">
      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">Schema 名称</label>
        <input
          type="text"
          placeholder="例如: analytics_mart"
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">描述 (可选)</label>
        <textarea
          placeholder="Schema 用途说明"
          rows={2}
          className="w-full px-3 py-1.5 text-sm bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none"
        />
      </div>
    </div>
  )
}
