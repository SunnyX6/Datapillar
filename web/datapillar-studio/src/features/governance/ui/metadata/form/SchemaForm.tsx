/**
 * Schema form component
 */

import { forwardRef, useImperativeHandle, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
    const { t } = useTranslation('oneMeta')
    const [formData, setFormData] = useState({
      name: '',
      comment: ''
    })

    useImperativeHandle(ref, () => ({
      getData: () => formData,
      validate: () => {
        if (!formData.name.trim()) {
          toast.error(t('metadataForm.schema.toast.enterName'))
          return false
        }
        return true
      }
    }), [formData, t])

    return (
      <div className="space-y-2.5">
        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">
            {t('metadataForm.schema.field.name')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            placeholder={t('metadataForm.schema.placeholder.name')}
            value={formData.name}
            onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
            className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-slate-700 dark:text-slate-300 mb-1">{t('metadataForm.schema.field.description')}</label>
          <textarea
            placeholder={t('metadataForm.schema.placeholder.description')}
            value={formData.comment}
            onChange={(e) => setFormData((prev) => ({ ...prev, comment: e.target.value }))}
            rows={2}
            className="w-full px-3 py-1.5 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    )
  }
)

CreateSchemaForm.displayName = 'CreateSchemaForm'
