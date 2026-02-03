import type { ChangeEvent } from 'react'

export type NamespaceCreateFormValue = {
  name: string
  description: string
}

type NamespaceCreateFormProps = {
  value: NamespaceCreateFormValue
  onChange: (nextValue: NamespaceCreateFormValue) => void
  showNameError: boolean
}

export function NamespaceCreateForm({ value, onChange, showNameError }: NamespaceCreateFormProps) {
  const handleNameChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange({ ...value, name: event.target.value })
  }

  const handleDescriptionChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    onChange({ ...value, description: event.target.value })
  }

  return (
    <div className="space-y-4">
      <div>
        <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
          名称 <span className="text-red-500">*</span>
        </label>
        <input
          type="text"
          value={value.name}
          onChange={handleNameChange}
          placeholder="e.g. 市场营销知识库"
          className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          autoFocus
        />
        {showNameError && (
          <p className="mt-1 text-micro text-rose-500">名称已存在，请换一个</p>
        )}
      </div>
      <div>
        <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">描述 (可选)</label>
        <textarea
          value={value.description}
          onChange={handleDescriptionChange}
          placeholder="简要描述该空间的内容..."
          rows={4}
          className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600 resize-none"
        />
      </div>
    </div>
  )
}
