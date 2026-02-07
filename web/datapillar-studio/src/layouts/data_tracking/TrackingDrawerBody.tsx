import { useMemo, useState } from 'react'
import {
  AlertCircle,
  Fingerprint,
  Globe,
  MapPin,
  MousePointerClick,
  Search,
  Server,
  Smartphone,
  User,
  X
} from 'lucide-react'
import { iconSizeToken, panelWidthClassMap } from '@/design-tokens/dimensions'
import { EVENT_SCHEMAS } from './data'
import { STATUS_STYLES } from './styles'
import type { TrackingPlatform } from './types'

type ContextProperty = {
  id: string
  name: string
}

const platformOptions: Array<{ id: TrackingPlatform; label: string; icon: typeof Globe }> = [
  { id: 'Web', label: 'Web', icon: Globe },
  { id: 'App', label: 'App', icon: Smartphone },
  { id: 'Server', label: 'Server', icon: Server }
]

export function TrackingDrawerBody() {
  const [platform, setPlatform] = useState<TrackingPlatform>('Web')
  const [viewPath, setViewPath] = useState('')
  const [triggerDescription, setTriggerDescription] = useState('')
  const [selectedSchemaId, setSelectedSchemaId] = useState(EVENT_SCHEMAS[0]?.id ?? '')
  const [contextProperties, setContextProperties] = useState<ContextProperty[]>([
    { id: 'ctx_network', name: 'network_type' },
    { id: 'ctx_referrer', name: 'referrer' }
  ])
  const [newContextProperty, setNewContextProperty] = useState('')

  const selectedSchema = useMemo(
    () => EVENT_SCHEMAS.find((schema) => schema.id === selectedSchemaId),
    [selectedSchemaId]
  )

  const handleAddContextProperty = () => {
    const trimmed = newContextProperty.trim()
    if (!trimmed) return
    setContextProperties((prev) => [...prev, { id: `ctx_${Date.now()}`, name: trimmed }])
    setNewContextProperty('')
  }

  const handleRemoveContextProperty = (propertyId: string) => {
    setContextProperties((prev) => prev.filter((item) => item.id !== propertyId))
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="px-6 py-4 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
        <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider mb-3">
          <User size={iconSizeToken.small} className="text-slate-400" />
          1. 主体 (Who)
        </div>
        <div className="flex items-center gap-3 bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 rounded-xl p-4">
          <div className="size-9 rounded-full bg-slate-200 dark:bg-slate-700 flex items-center justify-center text-slate-500">
            <Fingerprint size={iconSizeToken.normal} />
          </div>
          <div>
            <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">用户身份（SDK 自动采集）</div>
            <div className="text-micro text-slate-400">user_id / device_id / session_id</div>
          </div>
          <span className={`ml-auto text-nano font-semibold px-2 py-1 rounded-full border ${STATUS_STYLES.tested.className}`}>
            Always Present
          </span>
        </div>
      </div>

      <div className="flex-1 overflow-hidden flex">
        <div className={`${panelWidthClassMap.normal} border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 overflow-y-auto custom-scrollbar`}>
          <div className="p-6 space-y-5">
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider">
                <MapPin size={iconSizeToken.small} className="text-slate-400" />
                2. 位置 (Where)
              </div>
              <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg">
                {platformOptions.map((option) => {
                  const Icon = option.icon
                  const isActive = platform === option.id
                  return (
                    <button
                      key={option.id}
                      type="button"
                      onClick={() => setPlatform(option.id)}
                      className={`flex-1 flex items-center justify-center gap-1 py-1.5 text-micro font-bold rounded-md transition-all ${
                        isActive
                          ? 'bg-white text-emerald-700 shadow-sm dark:bg-slate-900 dark:text-emerald-300'
                          : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                      }`}
                    >
                      <Icon size={iconSizeToken.tiny} />
                      {option.label}
                    </button>
                  )
                })}
              </div>
              <input
                value={viewPath}
                onChange={(event) => setViewPath(event.target.value)}
                placeholder={platform === 'Web' ? '/checkout/success' : 'OrderSuccessViewController'}
                className="w-full px-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none font-mono"
              />
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider">
                <MousePointerClick size={iconSizeToken.small} className="text-slate-400" />
                3. 触发 (When)
              </div>
              <textarea
                value={triggerDescription}
                onChange={(event) => setTriggerDescription(event.target.value)}
                placeholder="描述触发条件，如：支付网关回跳后触发。"
                className="w-full min-h-[92px] px-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none resize-none"
              />
            </div>

            <div className="space-y-3">
              <div className="flex items-center gap-2 text-caption font-semibold text-slate-500 uppercase tracking-wider">
                <AlertCircle size={iconSizeToken.small} className="text-slate-400" />
                4. 事件模型 (What)
              </div>
              <div className="relative">
                <Search size={iconSizeToken.small} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <select
                  value={selectedSchemaId}
                  onChange={(event) => setSelectedSchemaId(event.target.value)}
                  className="w-full pl-9 pr-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none"
                >
                  {EVENT_SCHEMAS.map((schema) => (
                    <option key={schema.id} value={schema.id}>
                      {schema.name} · {schema.key}
                    </option>
                  ))}
                </select>
              </div>
              {selectedSchema && (
                <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/60 p-3">
                  <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">{selectedSchema.name}</div>
                  <div className="text-micro text-slate-400 font-mono">{selectedSchema.key}</div>
                  <div className="text-caption text-slate-500 dark:text-slate-400 mt-2 line-clamp-2">
                    {selectedSchema.description}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            <div className="text-caption font-semibold text-slate-500 uppercase tracking-wider">上下文属性</div>
            <div className="text-micro text-slate-400 mt-1">补充埋点上下文字段，用于分析与过滤。</div>
          </div>
          <div className="flex-1 overflow-y-auto custom-scrollbar p-6 space-y-4">
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 divide-y divide-slate-100 dark:divide-slate-800">
              {contextProperties.map((property) => (
                <div key={property.id} className="px-5 py-3 flex items-center justify-between">
                  <div className="text-body-sm font-semibold text-slate-900 dark:text-slate-100">{property.name}</div>
                  <button
                    type="button"
                    onClick={() => handleRemoveContextProperty(property.id)}
                    className="p-2 rounded-lg text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-500/10 transition-colors"
                  >
                    <X size={iconSizeToken.small} />
                  </button>
                </div>
              ))}
            </div>

            <div className="rounded-2xl border border-dashed border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/40 p-4 flex items-center gap-3">
              <input
                value={newContextProperty}
                onChange={(event) => setNewContextProperty(event.target.value)}
                placeholder="新增上下文字段"
                className="flex-1 px-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg text-body-sm text-slate-900 dark:text-slate-100 focus:border-brand-500 dark:focus:border-brand-400 outline-none"
              />
              <button
                type="button"
                onClick={handleAddContextProperty}
                className="px-4 py-2 rounded-lg bg-brand-600 text-white text-caption font-semibold hover:bg-brand-700 transition-colors"
              >
                添加
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
