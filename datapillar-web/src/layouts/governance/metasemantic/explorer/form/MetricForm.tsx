/**
 * 指标注册表单 - 向导式设计
 *
 * Step 1: 选择指标类型 (小模态框)
 * Step 2: 配置指标详情 (大模态框)
 */

import { useState, useEffect, useCallback, useRef } from 'react'
import { Target, Zap, Layers, ArrowRight, ChevronLeft, CheckCircle2, Loader2, Sparkles, AlertTriangle } from 'lucide-react'
import { Modal, ModalCancelButton, ModalPrimaryButton } from '@/components/Modal'
import { fetchMetricVersion, fetchWordRoots } from '@/services/oneMetaService'
import { aiFillMetric, aiCheckMetric, type AICheckResponse } from '@/services/metricAIService'
import type { MetricType, Metric } from '../types'
import { MetricFormLeft } from './MetricFormLeft'
import { MetricFormRight } from './MetricFormRight'
import type { MetricFormData, MeasureColumn, FilterColumn } from './types'
export type { MetricFormData } from './types'

/** 指标类型配置 */
const METRIC_TYPE_CONFIG: Record<MetricType, { label: string; desc: string; icon: typeof Target; color: string; bg: string }> = {
  ATOMIC: {
    label: '原子指标',
    desc: '直接来源于物理表的基础事实，是所有计算的基石。',
    icon: Target,
    color: 'text-purple-600',
    bg: 'bg-purple-50'
  },
  DERIVED: {
    label: '派生指标',
    desc: '在原子指标基础上叠加维度过滤或时间周期。',
    icon: Zap,
    color: 'text-blue-600',
    bg: 'bg-blue-50'
  },
  COMPOSITE: {
    label: '复合指标',
    desc: '通过多个指标间的算术运算得出，如比率、效率等。',
    icon: Layers,
    color: 'text-emerald-600',
    bg: 'bg-emerald-50'
  }
}

const DATA_TYPES = ['INTEGER', 'LONG', 'DOUBLE', 'DECIMAL']

/** 单位数据 - 与侧边栏保持一致 */
const UNITS = [
  { symbol: '¥', name: '人民币', code: 'CURRENCY' },
  { symbol: '%', name: '百分比', code: 'RATIO' },
  { symbol: '人', name: '人数', code: 'COUNT' },
  { symbol: 's', name: '秒', code: 'TIME' },
  { symbol: '个', name: '个数', code: 'PIECE' },
  { symbol: '次', name: '次数', code: 'TIMES' }
]

/** 修饰符数据 - 与侧边栏保持一致 */
const MODIFIERS = [
  { symbol: 'Σ', name: '累计', code: 'CUM' },
  { symbol: 'Δ', name: '同比', code: 'YOY' },
  { symbol: '环', name: '环比', code: 'MOM' },
  { symbol: '均', name: '平均', code: 'AVG' },
  { symbol: '最', name: '最大', code: 'MAX' },
  { symbol: '小', name: '最小', code: 'MIN' }
]

const emptyForm: MetricFormData = {
  name: '',
  code: '',
  baseCode: '',
  modifiers: [],
  type: 'ATOMIC',
  dataType: '',
  precision: 10,
  scale: 2,
  comment: '',
  unit: '',
  formula: '',
  measureColumns: [],
  filterColumns: []
}

interface MetricFormProps {
  isOpen: boolean
  onClose: () => void
  onSave: (form: MetricFormData) => void
  saving: boolean
  /** 编辑模式：传入要编辑的指标 */
  editMetric?: Metric | null
}
/** 指标注册表单弹窗 */
export function MetricFormModal({ isOpen, onClose, onSave, saving, editMetric }: MetricFormProps) {
  const isEditMode = !!editMetric
  const [step, setStep] = useState(isEditMode ? 2 : 1)
  const [form, setForm] = useState<MetricFormData>(emptyForm)
  const [loadingVersion, setLoadingVersion] = useState(false)
  const formulaRef = useRef<HTMLTextAreaElement>(null)
  const checkResultRef = useRef<HTMLDivElement>(null)

  // AI 相关状态
  const [aiInput, setAiInput] = useState('')
  const [aiLoading, setAiLoading] = useState(false)
  const [aiError, setAiError] = useState('')
  const [checking, setChecking] = useState(false)
  const [checkResult, setCheckResult] = useState<AICheckResponse | null>(null)
  const [wordRoots, setWordRoots] = useState<{ code: string; name: string }[]>([])

  // 加载词根
  useEffect(() => {
    if (!isOpen) return
    fetchWordRoots(0, 100)
      .then((data) => setWordRoots(data.items.map((r) => ({ code: r.code, name: r.nameCn }))))
      .catch(() => setWordRoots([]))
  }, [isOpen])

  // 编辑模式：加载指标版本详情
  useEffect(() => {
    if (!isOpen) return

    if (isEditMode && editMetric) {
      setStep(2)
      setLoadingVersion(true)
      // 确保 type 是大写格式
      const metricType = (editMetric.type?.toUpperCase() || 'ATOMIC') as MetricType
      // 获取当前版本详情以获取公式
      fetchMetricVersion(editMetric.code, editMetric.currentVersion)
        .then((versionData) => {
          setForm({
            name: editMetric.name,
            code: editMetric.code,
            baseCode: '',
            modifiers: [],
            type: metricType,
            dataType: editMetric.dataType || '',
            precision: 10,
            scale: 2,
            comment: editMetric.comment || '',
            unit: versionData.unit || '',
            formula: versionData.calculationFormula || '',
            measureColumns: [],
            filterColumns: []
          })
        })
        .catch(() => {
          // 获取版本详情失败，使用基本信息
          setForm({
            name: editMetric.name,
            code: editMetric.code,
            baseCode: '',
            modifiers: [],
            type: metricType,
            dataType: editMetric.dataType || '',
            precision: 10,
            scale: 2,
            comment: editMetric.comment || '',
            unit: '',
            formula: '',
            measureColumns: [],
            filterColumns: []
          })
        })
        .finally(() => setLoadingVersion(false))
    } else {
      setStep(1)
      setForm(emptyForm)
    }
  }, [isOpen, editMetric, isEditMode])

  const handleClose = () => {
    setStep(1)
    setForm(emptyForm)
    setAiInput('')
    setAiError('')
    setChecking(false)
    setCheckResult(null)
    onClose()
  }

  const handleSelectType = (type: MetricType) => {
    setForm({ ...emptyForm, type })
    setStep(2)
  }

  const handleSave = async () => {
    if (!form.name.trim() || !form.code.trim() || !form.formula.trim()) return

    // 先调用 AI Check
    setChecking(true)
    setCheckResult(null)
    try {
      const result = await aiCheckMetric({
        form: {
          name: form.name,
          code: form.code,
          type: form.type,
          dataType: form.dataType,
          unit: form.unit,
          calculationFormula: form.formula,
          comment: form.comment
        }
      })
      setCheckResult(result)

      // 有 error 级别问题时阻止保存，并滚动到检查结果
      if (result.issues.some((i) => i.severity === 'error')) {
        setTimeout(() => {
          checkResultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }, 100)
        return
      }
    } catch {
      // Check 失败时继续保存
    } finally {
      setChecking(false)
    }

    onSave(form)
  }

  // AI 填写表单
  const handleAiFill = async () => {
    if (!aiInput.trim() || aiLoading) return
    setAiError('')

    // 校验：不同类型指标需要先选择必要的数据
    if (form.type === 'ATOMIC' && form.measureColumns.length === 0) {
      setAiError('请先在右侧选择至少一个度量列')
      return
    }
    if (form.type === 'DERIVED' && !form.baseCode) {
      setAiError('请先在右侧选择基础指标')
      return
    }

    setAiLoading(true)
    setCheckResult(null)

    try {
      const payload: Record<string, unknown> = {}

      if (form.type === 'ATOMIC') {
        payload.measureColumns = form.measureColumns
        payload.filterColumns = form.filterColumns
      } else if (form.type === 'DERIVED') {
        payload.baseMetric = { code: form.baseCode }
        payload.modifiers = form.modifiers
      } else {
        payload.metrics = []
        payload.operation = 'divide'
      }

      const result = await aiFillMetric({
        userInput: aiInput,
        context: {
          metricType: form.type,
          payload,
          formOptions: {
            dataTypes: DATA_TYPES,
            units: UNITS.map((u) => u.code),
            wordRoots: wordRoots,
            modifiers: MODIFIERS.map((m) => ({ code: m.code, name: m.name }))
          }
        }
      })

      // 应用 AI 填写结果
      setForm((prev) => ({
        ...prev,
        name: result.name || prev.name,
        code: result.code || prev.code,
        dataType: result.dataType || prev.dataType,
        unit: result.unit || prev.unit,
        formula: result.calculationFormula || prev.formula,
        comment: result.comment || prev.comment
      }))
    } catch {
      setAiError('AI 填写失败，请重试')
    } finally {
      setAiLoading(false)
    }
  }

  // 原子指标：切换度量列选中状态
  const handleMeasureColumnToggle = (col: MeasureColumn, selected: boolean) => {
    if (selected) {
      setForm({
        ...form,
        measureColumns: [...form.measureColumns, col]
      })
    } else {
      setForm({
        ...form,
        measureColumns: form.measureColumns.filter((c) => c.name !== col.name)
      })
    }
  }

  // 原子指标：切换过滤列选中状态
  const handleFilterColumnToggle = (col: MeasureColumn, selected: boolean, filterCol?: FilterColumn) => {
    if (selected && filterCol) {
      // 添加或更新过滤列
      const existing = form.filterColumns.find((c) => c.name === col.name)
      if (existing) {
        // 更新已有的过滤列
        setForm({
          ...form,
          filterColumns: form.filterColumns.map((c) => c.name === col.name ? filterCol : c)
        })
      } else {
        // 添加新的过滤列
        setForm({
          ...form,
          filterColumns: [...form.filterColumns, filterCol]
        })
      }
    } else {
      // 移除过滤列
      setForm({
        ...form,
        filterColumns: form.filterColumns.filter((c) => c.name !== col.name)
      })
    }
  }

  // 派生/复合指标：仅插入公式
  const insertDerivedFormula = (text: string) => {
    setForm({ ...form, formula: text })
    setTimeout(() => {
      formulaRef.current?.focus()
    }, 0)
  }

  // 派生指标：自动拼接编码 = baseCode + modifiers
  const updateDerivedCode = useCallback((baseCode: string, modifiers: string[]) => {
    const parts = [baseCode, ...modifiers].filter(Boolean)
    const code = parts.join('_').toUpperCase()
    setForm((prev) => ({ ...prev, baseCode, modifiers, code }))
  }, [])

  // 添加修饰符
  const addModifier = (modifierCode: string) => {
    if (form.modifiers.includes(modifierCode)) return
    const newModifiers = [...form.modifiers, modifierCode]
    updateDerivedCode(form.baseCode, newModifiers)
  }

  // 移除修饰符
  const removeModifier = (modifierCode: string) => {
    const newModifiers = form.modifiers.filter((m) => m !== modifierCode)
    updateDerivedCode(form.baseCode, newModifiers)
  }

  const isValid = form.name.trim() && form.code.trim() && form.formula.trim()
  const typeConfig = METRIC_TYPE_CONFIG[form.type] || METRIC_TYPE_CONFIG.ATOMIC

  // Step 1: 选择指标类型
  if (step === 1) {
    return (
      <Modal isOpen={isOpen} onClose={handleClose} size="sm">
        <div className="text-center mb-6">
          <h2 className="text-title font-bold text-slate-900 dark:text-white">指标注册向导</h2>
          <p className="text-body-sm text-slate-500 mt-1">请选择您要创建的指标类型</p>
        </div>

        <div className="space-y-3">
          {(['ATOMIC', 'DERIVED', 'COMPOSITE'] as MetricType[]).map((type) => {
            const config = METRIC_TYPE_CONFIG[type]
            return (
              <button
                key={type}
                onClick={() => handleSelectType(type)}
                className="w-full flex items-start gap-4 p-5 rounded-2xl border-2 border-slate-100 dark:border-slate-800 hover:border-blue-200 dark:hover:border-blue-700 hover:bg-white dark:hover:bg-slate-800 hover:shadow-lg transition-all text-left group"
              >
                <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${config.bg} ${config.color} shadow-sm group-hover:scale-110 transition-transform`}>
                  <config.icon size={24} />
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-slate-900 dark:text-white">{config.label}</span>
                    <div className="w-7 h-7 rounded-full bg-slate-50 dark:bg-slate-800 flex items-center justify-center text-slate-300 group-hover:bg-slate-900 dark:group-hover:bg-white group-hover:text-white dark:group-hover:text-slate-900 transition-all">
                      <ArrowRight size={14} />
                    </div>
                  </div>
                  <p className="text-caption text-slate-500">{config.desc}</p>
                </div>
              </button>
            )
          })}
        </div>
      </Modal>
    )
  }

  // Step 2: 配置指标详情
  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      size="lg"
      title={isEditMode ? '编辑指标' : '配置指标详情'}
      footer={
        <>
          <ModalCancelButton onClick={handleClose} disabled={saving || checking || loadingVersion} />
          <ModalPrimaryButton onClick={handleSave} disabled={!isValid || loadingVersion || checking} loading={saving || checking}>
            {checking ? (
              <>检查中...</>
            ) : (
              <><CheckCircle2 size={16} /> {isEditMode ? '保存修改' : '确认发布'}</>
            )}
          </ModalPrimaryButton>
        </>
      }
    >
      {/* 返回按钮和类型标签 */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          {!isEditMode && (
            <button onClick={() => setStep(1)} className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-xl text-slate-400 transition-all">
              <ChevronLeft size={20} />
            </button>
          )}
          <div className="flex items-center gap-2">
            <span className="text-caption text-slate-400">指标类型:</span>
            <span className={`text-caption font-semibold px-2 py-0.5 rounded ${typeConfig.bg} ${typeConfig.color}`}>
              {typeConfig.label}
            </span>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-6 min-h-0 flex-1 overflow-hidden">
        {/* AI 辅助填写 */}
        <div className="p-4 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-2xl border border-purple-100 dark:border-purple-800/50 shrink-0">
          <div className="flex items-center gap-2 mb-2">
            <Sparkles size={16} className="text-purple-500" />
            <span className="text-caption font-semibold text-purple-700 dark:text-purple-300">AI 辅助填写</span>
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder={
                form.type === 'ATOMIC'
                  ? '选择右侧资产，然后描述您想要创建的指标，例如：单价和数量相乘，过滤掉未支付状态的订单'
                  : form.type === 'DERIVED'
                    ? '选择基础指标和修饰符，描述派生规则，例如：基于订单金额统计北京地区的月度累计值'
                    : '选择多个指标进行运算，例如：销售额减去成本再除以销售额，计算毛利率百分比'
              }
              className="flex-1 bg-white dark:bg-slate-800 border border-purple-200 dark:border-purple-700 rounded-xl px-4 py-2.5 text-body-sm placeholder:text-slate-400 focus:outline-none focus:border-purple-500 transition-all"
              value={aiInput}
              onChange={(e) => {
                setAiInput(e.target.value)
                setAiError('')
              }}
              onKeyDown={(e) => e.key === 'Enter' && handleAiFill()}
              disabled={aiLoading}
            />
            <button
              onClick={handleAiFill}
              disabled={!aiInput.trim() || aiLoading}
              className="px-4 py-2.5 bg-purple-600 hover:bg-purple-700 disabled:bg-slate-300 dark:disabled:bg-slate-700 text-white rounded-xl text-body-sm font-medium flex items-center gap-2 transition-all"
            >
              {aiLoading ? <Loader2 size={16} className="animate-spin" /> : <Sparkles size={16} />}
              {aiLoading ? '生成中...' : '智能填写'}
            </button>
          </div>
          {aiError && (
            <div className="mt-2 text-caption text-red-600 dark:text-red-400 flex items-center gap-1">
              <AlertTriangle size={14} />
              {aiError}
            </div>
          )}
        </div>

        {/* AI 检查结果 */}
        {checkResult && checkResult.issues.length > 0 && (
          <div ref={checkResultRef} className="p-4 bg-amber-50 dark:bg-amber-900/20 rounded-2xl border border-amber-200 dark:border-amber-800/50 shrink-0">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <AlertTriangle size={16} className="text-amber-500" />
                <span className="text-caption font-semibold text-amber-700 dark:text-amber-300">语义检查发现问题</span>
              </div>
              {checkResult.suggestions && Object.keys(checkResult.suggestions).length > 0 && (
                <button
                  onClick={() => {
                    const suggestions = checkResult.suggestions || {}
                    setForm((prev) => ({
                      ...prev,
                      name: suggestions.name || prev.name,
                      code: suggestions.code || prev.code,
                      dataType: suggestions.dataType || prev.dataType,
                      unit: suggestions.unit || prev.unit,
                      formula: suggestions.calculationFormula || prev.formula,
                      comment: suggestions.comment || prev.comment
                    }))
                    setCheckResult(null)
                  }}
                  className="text-caption font-medium text-purple-600 hover:text-purple-700 dark:text-purple-400 dark:hover:text-purple-300 flex items-center gap-1 transition-colors"
                >
                  <Sparkles size={14} />
                  应用建议
                </button>
              )}
            </div>
            <div className="space-y-2">
              {checkResult.issues.map((issue, idx) => {
                const suggestion = checkResult.suggestions?.[issue.field === 'calculationFormula' ? 'calculationFormula' : issue.field]
                return (
                  <div
                    key={idx}
                    className={`p-2 rounded-lg ${
                      issue.severity === 'error'
                        ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'
                        : 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300'
                    }`}
                  >
                    <div className="flex items-start gap-2">
                      <span className={`text-micro font-semibold px-1.5 py-0.5 rounded shrink-0 ${
                        issue.severity === 'error' ? 'bg-red-200 dark:bg-red-800' : 'bg-amber-200 dark:bg-amber-800'
                      }`}>
                        {issue.field}
                      </span>
                      <span className="text-caption">{issue.message}</span>
                    </div>
                    {suggestion && (
                      <div className="mt-2 pl-2 border-l-2 border-purple-300 dark:border-purple-600">
                        <span className="text-micro text-purple-600 dark:text-purple-400">建议: </span>
                        <span className="text-caption text-purple-700 dark:text-purple-300 font-medium">{suggestion}</span>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* 加载状态 */}
        {loadingVersion ? (
          <div className="flex flex-col items-center justify-center py-16">
            <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
            <div className="text-slate-400 text-caption mt-3">加载指标详情...</div>
          </div>
        ) : (
          <div className="flex-1 min-h-0">
            {/* 表单内容 - 两栏布局 */}
            <div className="grid grid-cols-12 gap-6 min-h-[520px] h-full overflow-y-auto custom-scrollbar items-stretch">
              <MetricFormLeft
                form={form}
                setForm={setForm}
                dataTypes={DATA_TYPES}
                units={UNITS}
                modifiers={MODIFIERS}
                updateDerivedCode={updateDerivedCode}
                addModifier={addModifier}
                removeModifier={removeModifier}
                formulaRef={formulaRef}
              />
              <MetricFormRight
                form={form}
                onMeasureToggle={handleMeasureColumnToggle}
                onFilterToggle={handleFilterColumnToggle}
                insertDerivedFormula={insertDerivedFormula}
              />
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
