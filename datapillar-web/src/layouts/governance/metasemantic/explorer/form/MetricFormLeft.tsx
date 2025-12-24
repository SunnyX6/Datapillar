import { Code, X } from 'lucide-react'
import type { RefObject, Dispatch, SetStateAction } from 'react'
import type { MetricFormData } from './types'

interface MetricFormLeftProps {
  form: MetricFormData
  setForm: Dispatch<SetStateAction<MetricFormData>>
  dataTypes: string[]
  units: Array<{ symbol: string; name: string; code: string }>
  modifiers: Array<{ symbol: string; name: string; code: string }>
  updateDerivedCode: (baseCode: string, modifiers: string[]) => void
  addModifier: (modifierCode: string) => void
  removeModifier: (modifierCode: string) => void
  formulaRef: RefObject<HTMLTextAreaElement>
}

export function MetricFormLeft({
  form,
  setForm,
  dataTypes,
  units,
  modifiers,
  updateDerivedCode,
  addModifier,
  removeModifier,
  formulaRef
}: MetricFormLeftProps) {
  return (
    <div className="col-span-5 flex flex-col gap-4 h-full">
      <div className="flex-1 min-h-0 flex flex-col gap-4">
        <div className="space-y-1.5">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">指标名称 *</label>
          <input
            type="text"
            placeholder="例如：累计订单金额"
            className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm placeholder:text-slate-400 focus:outline-none focus:border-blue-500 transition-all"
            value={form.name}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
          />
        </div>

        {/* 指标编码区域 - 根据类型显示不同内容 */}
        {form.type === 'DERIVED' ? (
          <>
            {/* 派生指标：业务口径 + 级联修饰符下拉 */}
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">业务口径 *</label>
              <input
                type="text"
                placeholder="ORDER_AMOUNT"
                className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono uppercase placeholder:text-slate-400 placeholder:normal-case focus:outline-none focus:border-blue-500 transition-all"
                value={form.baseCode}
                onChange={(e) => updateDerivedCode(e.target.value.toUpperCase(), form.modifiers)}
              />
            </div>

            {/* 修饰符级联下拉 */}
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">可选修饰符（最多3个）</label>
              <div className="flex items-center gap-2">
                {form.modifiers.map((mod, idx) => {
                  const availableModifiers = modifiers.filter((m) =>
                    m.code === mod || !form.modifiers.includes(m.code)
                  )
                  const isLastOne = form.modifiers.length === 3 && idx === 2
                  return (
                    <div key={idx} className="flex items-center gap-1">
                      <select
                        value={mod}
                        onChange={(e) => {
                          const newModifiers = [...form.modifiers]
                          newModifiers[idx] = e.target.value
                          updateDerivedCode(form.baseCode, newModifiers)
                        }}
                        className="bg-white dark:bg-slate-800 border border-blue-200 dark:border-blue-700 rounded-lg px-2 py-1.5 text-caption focus:outline-none focus:border-blue-500 transition-all"
                      >
                        {availableModifiers.map((m) => (
                          <option key={m.code} value={m.code}>{m.name}({m.code})</option>
                        ))}
                      </select>
                      {!isLastOne && (
                        <button
                          type="button"
                          onClick={() => removeModifier(mod)}
                          className="p-0.5 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded transition-colors"
                          title="删除"
                        >
                          <X size={12} />
                        </button>
                      )}
                    </div>
                  )
                })}

                {form.modifiers.length < 3 && (
                  <select
                    value=""
                    onChange={(e) => {
                      if (e.target.value) {
                        addModifier(e.target.value)
                      }
                    }}
                    className="bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-2 py-1.5 text-caption text-slate-400 focus:outline-none focus:border-blue-500 transition-all"
                  >
                    <option value="" className="text-slate-400">请选择</option>
                    {modifiers.filter((m) => !form.modifiers.includes(m.code)).map((m) => (
                      <option key={m.code} value={m.code} className="text-slate-800 dark:text-slate-200">{m.name}({m.code})</option>
                    ))}
                  </select>
                )}
              </div>
            </div>

            {/* 生成编码预览 */}
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">生成编码</label>
              <div className="w-full bg-slate-50 dark:bg-slate-800/50 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
                {form.code || '...'}
              </div>
            </div>
          </>
        ) : (
          /* 原子指标/复合指标：直接输入编码 */
          <div className="space-y-1.5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">指标编码 *</label>
            <input
              type="text"
              placeholder="ORDER_AMOUNT"
              className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono uppercase placeholder:text-slate-400 placeholder:normal-case focus:outline-none focus:border-blue-500 transition-all"
              value={form.code}
              onChange={(e) => setForm((prev) => ({ ...prev, code: e.target.value.toUpperCase() }))}
            />
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">数据类型</label>
            <select
              value={form.dataType}
              onChange={(e) => setForm((prev) => ({ ...prev, dataType: e.target.value }))}
              className={`w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono focus:outline-none focus:border-blue-500 ${!form.dataType ? 'text-slate-400' : ''}`}
            >
              <option value="" className="text-slate-400">请选择</option>
              {dataTypes.map((type) => (
                <option key={type} value={type} className="text-slate-800 dark:text-slate-200">{type}</option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">单位</label>
            <select
              value={form.unit}
              onChange={(e) => setForm((prev) => ({ ...prev, unit: e.target.value }))}
              className={`w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm focus:outline-none focus:border-blue-500 ${!form.unit ? 'text-slate-400' : ''}`}
            >
              <option value="" className="text-slate-400">请选择单位</option>
              {units.map((unit) => (
                <option key={unit.code} value={unit.code} className="text-slate-800 dark:text-slate-200">{unit.symbol} {unit.name}</option>
              ))}
            </select>
          </div>
        </div>

        {form.dataType === 'DECIMAL' && (
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">精度（总位数）</label>
              <input
                type="number"
                min={1}
                max={38}
                value={form.precision}
                onChange={(e) => setForm((prev) => ({
                  ...prev,
                  precision: Math.max(1, Math.min(38, parseInt(e.target.value) || 10))
                }))}
                className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono focus:outline-none focus:border-blue-500"
              />
            </div>
            <div className="space-y-1.5">
              <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">小数位数</label>
              <input
                type="number"
                min={0}
                max={form.precision}
                value={form.scale}
                onChange={(e) => setForm((prev) => ({
                  ...prev,
                  scale: Math.max(0, Math.min(prev.precision, parseInt(e.target.value) || 0))
                }))}
                className="w-full bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm font-mono focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>
        )}

        <div className="flex flex-col gap-1.5 flex-1 min-h-0">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide">业务描述</label>
          <textarea
            placeholder="描述该指标的业务含义..."
            className="w-full flex-1 min-h-[60px] bg-white dark:bg-slate-800 border-2 border-slate-100 dark:border-slate-700 rounded-xl px-4 py-2.5 text-body-sm placeholder:text-slate-400 focus:outline-none focus:border-blue-500 transition-all resize-none"
            value={form.comment}
            onChange={(e) => setForm((prev) => ({ ...prev, comment: e.target.value }))}
          />
        </div>

        {/* 公式表达式 */}
        <div className="flex flex-col gap-1.5 flex-1 min-h-0">
          <label className="text-micro font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-2">
            <Code size={14} className="text-emerald-500" /> 公式表达式 *
          </label>
          <div className="relative flex-1 min-h-[60px]">
            <Code size={14} className="absolute top-3.5 left-4 text-emerald-500/50 pointer-events-none" />
            <textarea
              ref={formulaRef}
              placeholder={
                form.type === 'ATOMIC'
                  ? "SUM(orders.amount) WHERE status = 'paid'"
                  : form.type === 'DERIVED'
                    ? "{SALES_AMOUNT} WHERE region = '北京'"
                    : '({SALES_AMOUNT} - {COST}) / {SALES_AMOUNT} * 100'
              }
              className="w-full h-full bg-slate-900 text-emerald-400 font-mono border-2 border-slate-100 dark:border-slate-700 rounded-xl pl-10 pr-4 py-3 text-body-sm focus:outline-none focus:border-emerald-500 shadow-lg resize-none"
              value={form.formula}
              onChange={(e) => setForm((prev) => ({ ...prev, formula: e.target.value }))}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
