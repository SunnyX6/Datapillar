import { useMemo, useState } from 'react'
import { Combine, Hash, Plus, Terminal, Type, X } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { EVENT_SCHEMAS } from './data'
import type { SchemaKind } from './types'
import { DEFAULT_DOMAIN_ACCENT, DOMAIN_ACCENTS } from './styles'

type LogicOperator = 'AND' | 'OR'

type PropertyItem = {
  id: string
  name: string
}

const atomicSchemas = EVENT_SCHEMAS.filter((schema) => schema.kind === 'ATOMIC')

export function SchemaDrawerBody() {
  const [schemaKind, setSchemaKind] = useState<SchemaKind>('ATOMIC')
  const [schemaKey, setSchemaKey] = useState('')
  const [schemaName, setSchemaName] = useState('')
  const [logicOperator, setLogicOperator] = useState<LogicOperator>('AND')
  const [selectedSchemaIds, setSelectedSchemaIds] = useState<string[]>([])
  const [properties, setProperties] = useState<PropertyItem[]>([
    { id: 'prop_order_id', name: 'order_id' },
    { id: 'prop_amount', name: 'amount' }
  ])
  const [newProperty, setNewProperty] = useState('')

  const selectedSchemas = useMemo(
    () => atomicSchemas.filter((schema) => selectedSchemaIds.includes(schema.id)),
    [selectedSchemaIds]
  )

  const handleToggleSchema = (schemaId: string) => {
    setSelectedSchemaIds((prev) =>
      prev.includes(schemaId) ? prev.filter((id) => id !== schemaId) : [...prev, schemaId]
    )
  }

  const handleAddProperty = () => {
    const trimmed = newProperty.trim()
    if (!trimmed) return
    setProperties((prev) => [...prev, { id: `prop_${Date.now()}`, name: trimmed }])
    setNewProperty('')
  }

  const handleRemoveProperty = (propertyId: string) => {
    setProperties((prev) => prev.filter((item) => item.id !== propertyId))
  }

  return (
    <div className="flex h-full overflow-hidden">
      <div className="w-[360px] border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-y-auto custom-scrollbar">
        <div className="p-6 space-y-5">
          <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm">
            <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider mb-4">
              <Hash size={iconSizeToken.small} className="text-slate-400" />
              事件标识
            </div>

            <div className="bg-slate-100 dark:bg-slate-800 p-1 rounded-lg flex mb-4">
              <button
                type="button"
                onClick={() => setSchemaKind('ATOMIC')}
                className={`flex-1 px-3 py-1.5 text-micro font-bold rounded-md transition-all ${
                  schemaKind === 'ATOMIC'
                    ? 'bg-white text-purple-700 shadow-sm dark:bg-slate-900 dark:text-purple-300'
                    : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                }`}
              >
                Atomic
              </button>
              <button
                type="button"
                onClick={() => setSchemaKind('COMPOSITE')}
                className={`flex-1 px-3 py-1.5 text-micro font-bold rounded-md transition-all ${
                  schemaKind === 'COMPOSITE'
                    ? 'bg-white text-amber-700 shadow-sm dark:bg-slate-900 dark:text-amber-300'
                    : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                }`}
              >
                Composite
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-nano font-semibold text-slate-500 uppercase mb-1.5">事件 Key</label>
                <div className="relative">
                  <Terminal size={iconSizeToken.small} className="absolute left-3 top-2.5 text-slate-400" />
                  <input
                    value={schemaKey}
                    onChange={(event) => setSchemaKey(event.target.value)}
                    placeholder={schemaKind === 'ATOMIC' ? 'e.g. order_paid_success' : 'e.g. high_value_conversion'}
                    className="w-full pl-9 pr-3 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-colors font-mono"
                  />
                </div>
              </div>
              <div>
                <label className="block text-nano font-semibold text-slate-500 uppercase mb-1.5">显示名称</label>
                <div className="relative">
                  <Type size={iconSizeToken.small} className="absolute left-3 top-2.5 text-slate-400" />
                  <input
                    value={schemaName}
                    onChange={(event) => setSchemaName(event.target.value)}
                    placeholder="e.g. 订单支付成功"
                    className="w-full pl-9 pr-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-colors"
                  />
                </div>
              </div>
            </div>
          </div>

          {schemaKind === 'COMPOSITE' && (
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider">
                  <Combine size={iconSizeToken.small} className="text-amber-500" />
                  组合逻辑
                </div>
                <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-md">
                  {(['AND', 'OR'] as LogicOperator[]).map((op) => (
                    <button
                      key={op}
                      type="button"
                      onClick={() => setLogicOperator(op)}
                      className={`px-2 py-1 text-nano font-bold rounded-md transition-colors ${
                        logicOperator === op
                          ? 'bg-white text-amber-700 dark:bg-slate-900 dark:text-amber-300'
                          : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                      }`}
                    >
                      {op}
                    </button>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <div className="text-nano font-semibold text-slate-400 uppercase tracking-wider">选择事件</div>
                <div className="space-y-2">
                  {atomicSchemas.map((schema) => {
                    const accent = DOMAIN_ACCENTS[schema.domain] ?? DEFAULT_DOMAIN_ACCENT
                    const isSelected = selectedSchemaIds.includes(schema.id)
                    return (
                      <button
                        key={schema.id}
                        type="button"
                        onClick={() => handleToggleSchema(schema.id)}
                        className={`w-full flex items-center justify-between px-3 py-2 rounded-lg border transition-colors ${
                          isSelected
                            ? 'border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-500/10'
                            : 'border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900 hover:border-slate-300'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <span className={`size-2 rounded-full ${accent.bar}`} />
                          <span className="text-body-sm font-medium text-slate-800 dark:text-slate-100">{schema.name}</span>
                        </div>
                        <span className={`text-nano ${isSelected ? 'text-amber-600 dark:text-amber-300' : 'text-slate-400'}`}>
                          {isSelected ? '已加入' : '加入'}
                        </span>
                      </button>
                    )
                  })}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex-1 flex flex-col overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex items-center justify-between">
          <div>
            <div className="text-caption font-semibold text-slate-500 uppercase tracking-wider">
              {schemaKind === 'ATOMIC' ? '属性定义' : '组合规则预览'}
            </div>
            <div className="text-micro text-slate-400 mt-1">
              {schemaKind === 'ATOMIC' ? '定义标准属性与上下文字段。' : '展示组合事件的触发逻辑。'}
            </div>
          </div>
          {schemaKind === 'ATOMIC' && (
            <div className="flex items-center gap-2">
              <input
                value={newProperty}
                onChange={(event) => setNewProperty(event.target.value)}
                placeholder="新增属性"
                className="px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none"
              />
              <button
                type="button"
                onClick={handleAddProperty}
                className="px-3 py-1.5 rounded-lg bg-brand-600 text-white text-caption font-semibold hover:bg-brand-700 transition-colors"
              >
                <Plus size={iconSizeToken.small} className="mr-1" />
                添加
              </button>
            </div>
          )}
        </div>

        <div className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-4">
          {schemaKind === 'COMPOSITE' ? (
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 space-y-3">
              <div className="text-caption font-semibold text-slate-500 uppercase tracking-wider">触发逻辑</div>
              {selectedSchemas.length === 0 ? (
                <div className="text-body-sm text-slate-400">请选择至少一个原子事件。</div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {selectedSchemas.map((schema, index) => (
                    <div key={schema.id} className="flex items-center gap-2">
                      <span className="px-2 py-1 rounded-full bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-200 text-caption font-semibold">
                        {schema.name}
                      </span>
                      {index < selectedSchemas.length - 1 && (
                        <span className="text-nano text-slate-400 font-semibold">{logicOperator}</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 divide-y divide-slate-100 dark:divide-slate-800">
              {properties.map((property) => (
                <div key={property.id} className="flex items-center justify-between px-5 py-3">
                  <div>
                    <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">{property.name}</div>
                    <div className="text-micro text-slate-400 font-mono">STRING</div>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveProperty(property.id)}
                    className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors"
                  >
                    <X size={iconSizeToken.small} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
