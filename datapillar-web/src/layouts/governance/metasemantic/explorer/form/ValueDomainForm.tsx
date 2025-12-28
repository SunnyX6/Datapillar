/**
 * 创建值域表单
 */

import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import { Modal, ModalCancelButton, ModalPrimaryButton, Select } from '@/components/ui'
import { DataTypeSelector, type DataTypeValue } from '@/components/ui'
import type { ValueDomainType, ValueDomainLevel } from '@/services/oneMetaSemanticService'

/** 值域类型选项 */
const DOMAIN_TYPE_OPTIONS: { value: ValueDomainType; label: string; desc: string }[] = [
  { value: 'ENUM', label: '枚举型 (ENUM)', desc: '定义一组离散的可选值' },
  { value: 'RANGE', label: '区间型 (RANGE)', desc: '定义数值范围，如 [0, 100]' },
  { value: 'REGEX', label: '模式型 (REGEX)', desc: '定义正则表达式约束' }
]

/** 值域级别选项 */
const DOMAIN_LEVEL_OPTIONS: { value: ValueDomainLevel; label: string }[] = [
  { value: 'BUSINESS', label: '业务级 (BUSINESS)' },
  { value: 'BUILTIN', label: '内置级 (BUILTIN)' }
]

/** 枚举值项 */
export interface EnumItem {
  key: string
  value: string
}

/** 创建值域表单数据 */
export interface ValueDomainFormData {
  domainCode: string
  domainName: string
  domainType: ValueDomainType
  domainLevel: ValueDomainLevel
  /** ENUM 类型的多个 key-value */
  enumItems: EnumItem[]
  /** RANGE/REGEX 类型的单值 */
  itemValue: string
  comment: string
  /** 数据类型 */
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

  // 添加枚举项
  const addEnumItem = () => {
    setForm({ ...form, enumItems: [...form.enumItems, { key: '', value: '' }] })
  }

  // 删除枚举项
  const removeEnumItem = (index: number) => {
    if (form.enumItems.length <= 1) return
    setForm({ ...form, enumItems: form.enumItems.filter((_, i) => i !== index) })
  }

  // 更新枚举项
  const updateEnumItem = (index: number, field: 'key' | 'value', val: string) => {
    const newItems = [...form.enumItems]
    newItems[index] = { ...newItems[index], [field]: val }
    setForm({ ...form, enumItems: newItems })
  }

  // 校验
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
      title="创建值域"
      footerRight={
        <>
          <ModalCancelButton onClick={handleClose} disabled={saving} />
          <ModalPrimaryButton onClick={handleSave} disabled={!isValid} loading={saving}>
            确认创建
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        {/* 值域名称 + 编码 - 两栏布局 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域名称 <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={form.domainName}
              onChange={(e) => setForm({ ...form, domainName: e.target.value })}
              placeholder="订单状态"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域编码 <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={form.domainCode}
              onChange={(e) => setForm({ ...form, domainCode: e.target.value.toUpperCase() })}
              placeholder="ORDER_STATUS"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 font-mono uppercase border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
        </div>

        {/* 值域类型 + 值域级别 - 两栏布局 */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域类型 <span className="text-rose-500">*</span>
            </label>
            <Select
              value={form.domainType}
              onChange={(value) => setForm({ ...form, domainType: value as ValueDomainType })}
              options={DOMAIN_TYPE_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="选择值域类型"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              值域级别 <span className="text-rose-500">*</span>
            </label>
            <Select
              value={form.domainLevel}
              onChange={(value) => setForm({ ...form, domainLevel: value as ValueDomainLevel })}
              options={DOMAIN_LEVEL_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="选择值域级别"
            />
          </div>
        </div>
        <p className="text-micro text-slate-400 -mt-2">
          {DOMAIN_TYPE_OPTIONS.find((opt) => opt.value === form.domainType)?.desc}
        </p>

        {/* 数据类型 - 窄宽 */}
        <div className="w-1/3">
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            数据类型 <span className="text-rose-500">*</span>
          </label>
          <DataTypeSelector
            value={form.dataType}
            onChange={(dataType) => setForm({ ...form, dataType })}
            size="small"
            triggerClassName="w-full h-[38px]"
          />
        </div>

        {/* 值项 - 根据类型显示不同输入 */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {form.domainType === 'ENUM' ? '枚举值列表' : form.domainType === 'RANGE' ? '区间表达式' : '正则表达式'} <span className="text-rose-500">*</span>
          </label>

          {form.domainType === 'ENUM' ? (
            // ENUM: 可动态添加的 Key-Value 列表
            <div className="space-y-2">
              {/* 表头 */}
              <div className="grid grid-cols-[1fr_1fr_32px] gap-2 text-micro text-slate-400 px-1">
                <span>值 (Key)</span>
                <span>标签 (Value)</span>
                <span></span>
              </div>
              {/* 列表项 */}
              {form.enumItems.map((item, index) => (
                <div key={index} className="grid grid-cols-[1fr_1fr_32px] gap-2 items-center">
                  <input
                    type="text"
                    value={item.key}
                    onChange={(e) => updateEnumItem(index, 'key', e.target.value)}
                    placeholder="PAID"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                  />
                  <input
                    type="text"
                    value={item.value}
                    onChange={(e) => updateEnumItem(index, 'value', e.target.value)}
                    placeholder="已支付"
                    className="w-full px-3 py-1.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
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
              {/* 添加按钮 */}
              <button
                type="button"
                onClick={addEnumItem}
                className="w-full py-2 border border-dashed border-slate-300 dark:border-slate-600 rounded-lg text-caption text-slate-500 hover:text-blue-600 hover:border-blue-400 dark:hover:border-blue-500 transition-all flex items-center justify-center gap-1"
              >
                <Plus size={14} /> 添加枚举值
              </button>
            </div>
          ) : (
            // RANGE/REGEX: 单输入框
            <div>
              <input
                type="text"
                value={form.itemValue}
                onChange={(e) => setForm({ ...form, itemValue: e.target.value })}
                placeholder={form.domainType === 'RANGE' ? '[0, 100]' : '^[1-9]\\d{5}(18|19|20)\\d{2}...'}
                className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 font-mono border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
              />
              <p className="text-micro text-slate-400 mt-1">
                {form.domainType === 'RANGE' ? '输入数值区间，如: [0, 100] 或 (0, 1]' : '输入正则表达式'}
              </p>
            </div>
          )}
        </div>

        {/* 备注 */}
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            备注
          </label>
          <input
            type="text"
            value={form.comment}
            onChange={(e) => setForm({ ...form, comment: e.target.value })}
            placeholder="值域说明（可选）"
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>
      </div>
    </Modal>
  )
}
