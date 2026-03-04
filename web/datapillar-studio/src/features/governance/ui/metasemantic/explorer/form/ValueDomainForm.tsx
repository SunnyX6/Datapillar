/**
 * Create a value field form
 */

import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Modal, ModalCancelButton, ModalPrimaryButton, Select } from '@/components/ui'
import { DataTypeSelector, type DataTypeValue } from '@/components/ui'
import type { ValueDomainType, ValueDomainLevel } from '@/services/oneMetaSemanticService'

/** Range type options */
const DOMAIN_TYPE_OPTIONS: { value: ValueDomainType; label: string; desc: string }[] = [
  { value: 'ENUM', label: 'enumeration type (ENUM)', desc: 'Define a discrete set of optional values' },
  { value: 'RANGE', label: 'Interval (RANGE)', desc: 'Define numerical range，Such as [0, 100]' },
  { value: 'REGEX', label: 'pattern (REGEX)', desc: 'Define regular expression constraints' }
]

/** Range level options */
const DOMAIN_LEVEL_OPTIONS: { value: ValueDomainLevel; label: string }[] = [
  { value: 'BUSINESS', label: 'business level (BUSINESS)' },
  { value: 'BUILTIN', label: 'Built-in level (BUILTIN)' }
]

/** enum value items */
export interface EnumItem {
  key: string
  value: string
}

/** Create value field form data */
export interface ValueDomainFormData {
  domainCode: string
  domainName: string
  domainType: ValueDomainType
  domainLevel: ValueDomainLevel
  /** ENUM multiple types key-value */
  enumItems: EnumItem[]
  /** RANGE/REGEX single value of type */
  itemValue: string
  comment: string
  /** data type */
  dataType: DataTypeValue
}

const emptyForm: ValueDomainFormData = {
  domainCode: '',
  domainName: '',
  domainType: 'ENUM',
  domainLevel: 'BUSINESS',
  enumItems: [{ key: '', value: '' }],
  itemValue: '',
  comment: '',
  dataType: { type: 'STRING' }
}

interface ValueDomainFormModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (form: ValueDomainFormData) => void
  saving: boolean
}

export function ValueDomainFormModal({ isOpen, onClose, onSave, saving }: ValueDomainFormModalProps) {
  const [form, setForm] = useState<ValueDomainFormData>(emptyForm)

  const handleClose = () => {
    setForm(emptyForm)
    onClose()
  }

  // Add enumeration item
  const addEnumItem = () => {
    setForm({ ...form, enumItems: [...form.enumItems, { key: '', value: '' }] })
  }

  // Delete enumeration item
  const removeEnumItem = (index: number) => {
    if (form.enumItems.length <= 1) return
    setForm({ ...form, enumItems: form.enumItems.filter((_, i) => i !== index) })
  }

  // Update enumeration item
  const updateEnumItem = (index: number, field: 'key' | 'value', val: string) => {
    const newItems = [...form.enumItems]
    newItems[index] = { ...newItems[index], [field]: val }
    setForm({ ...form, enumItems: newItems })
  }

  // Verify
  const isValid = (() => {
    if (!form.domainCode.trim() || !form.domainName.trim()) return false
    if (form.domainType === 'ENUM') {
      return form.enumItems.some((item) => item.key.trim())
    }
    return form.itemValue.trim().length > 0
  })()

  const handleSave = () => {
    if (!isValid || saving) return
    onSave(form)
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      size="sm"
      title="Create a value range"
      footerRight={
        <>
          <ModalCancelButton onClick={handleClose} disabled={saving} />
          <ModalPrimaryButton onClick={handleSave} disabled={!isValid} loading={saving}>
            Confirm creation
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* Value field name + encoding - two column layout */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Value field name <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={form.domainName}
              onChange={(e) => setForm({ ...form, domainName: e.target.value })}
              placeholder="Order status"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              range encoding <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={form.domainCode}
              onChange={(e) => setForm({ ...form, domainCode: e.target.value.toUpperCase() })}
              placeholder="ORDER_STATUS"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 font-mono uppercase border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
        </div>

        {/* Range type + range level - two column layout */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Range type <span className="text-rose-500">*</span>
            </label>
            <Select
              value={form.domainType}
              onChange={(value) => setForm({ ...form, domainType: value as ValueDomainType })}
              options={DOMAIN_TYPE_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="Select a range type"
              size="sm"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              range level <span className="text-rose-500">*</span>
            </label>
            <Select
              value={form.domainLevel}
              onChange={(value) => setForm({ ...form, domainLevel: value as ValueDomainLevel })}
              options={DOMAIN_LEVEL_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="Select range level"
              size="sm"
            />
          </div>
        </div>
        <p className="text-micro text-slate-400 -mt-2">
          {DOMAIN_TYPE_OPTIONS.find((opt) => opt.value === form.domainType)?.desc}
        </p>

        {/* data type - Narrow and wide */}
        <div className="w-1/3">
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            data type <span className="text-rose-500">*</span>
          </label>
          <DataTypeSelector
            value={form.dataType}
            onChange={(dataType) => setForm({ ...form, dataType })}
            size="small"
            triggerClassName="w-full h-[38px] bg-white dark:bg-slate-900"
          />
        </div>

        {/* value item - Display different inputs based on type */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {form.domainType === 'ENUM' ? 'List of enumeration values' : form.domainType === 'RANGE' ? 'interval expression' : 'regular expression'} <span className="text-rose-500">*</span>
          </label>

          {form.domainType === 'ENUM' ? (
            // ENUM: Can be added dynamically Key-Value list
            <div className="space-y-2">
              {/* Header */}
              <div className="grid grid-cols-[1fr_1fr_32px] gap-2 text-micro text-slate-400 px-1">
                <span>value (Key)</span>
                <span>label (Value)</span>
                <span></span>
              </div>
              {/* list item */}
              {form.enumItems.map((item, index) => (
                <div key={index} className="grid grid-cols-[1fr_1fr_32px] gap-2 items-center">
                  <input
                    type="text"
                    value={item.key}
                    onChange={(e) => updateEnumItem(index, 'key', e.target.value)}
                    placeholder="PAID"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <input
                    type="text"
                    value={item.value}
                    onChange={(e) => updateEnumItem(index, 'value', e.target.value)}
                    placeholder="paid"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <button
                    type="button"
                    onClick={() => removeEnumItem(index)}
                    disabled={form.enumItems.length <= 1}
                    className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              {/* Add button */}
              <button
                type="button"
                onClick={addEnumItem}
                className="w-full py-2 border border-dashed border-slate-300 dark:border-slate-600 rounded-lg text-caption text-slate-500 hover:text-blue-600 hover:border-blue-400 dark:hover:border-blue-500 transition-all flex items-center justify-center gap-1"
              >
                <Plus size={14} /> Add enumeration value
              </button>
            </div>
          ) : (
            // RANGE/REGEX: Single input box
            <div>
              <input
                type="text"
                value={form.itemValue}
                onChange={(e) => setForm({ ...form, itemValue: e.target.value })}
                placeholder={form.domainType === 'RANGE' ? '[0, 100]' : '^[1-9]\\d{5}(18|19|20)\\d{2}...'}
                className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
              />
              <p className="text-micro text-slate-400 mt-1">
                {form.domainType === 'RANGE' ? 'Enter a numerical range，Such as: [0, 100] or (0, 1]' : 'Enter regular expression'}
              </p>
            </div>
          )}
        </div>

        {/* Remarks */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            Remarks
          </label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="Value range description（Optional）"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    </Modal>
  )
}
