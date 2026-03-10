/**
 * Metric registration form - Guided design
 *
 * Step 1: Select Metric type (small modal)
 * Step 2: Configure Metric details (large modal)
 */

import { useState,useEffect,useCallback,useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Target,Zap,Layers,ArrowRight,ChevronLeft,CheckCircle2,Loader2,Sparkles,AlertTriangle } from 'lucide-react'
import { Modal,ModalCancelButton,ModalPrimaryButton } from '@/components/ui'
import { parseDataTypeString } from '@/components/ui'
import { fetchMetricVersion,fetchUnits,fetchModifiers,fetchWordRoots,fetchMetrics,type UnitDTO,type MetricModifierDTO,type WordRootDTO } from '@/services/oneMetaSemanticService'
import { aiFillMetric } from '@/services/metricAIService'
import type { MetricType,Metric,AIRecommendation } from '@/features/governance/ui/metasemantic/types'
import { MetricFormLeft } from './MetricFormLeft'
import { MetricFormRight } from './MetricFormRight'
import type { MetricFormData,MeasureColumn,FilterColumn } from './types'
export type { MetricFormData } from './types'

const DATA_TYPES = ['INTEGER','LONG','DOUBLE','DECIMAL']

const emptyForm:MetricFormData = {
 name:'',code:'',customSuffix:'',wordRoots:[],aggregation:'',modifiers:[],baseCode:'',type:'ATOMIC',dataType:'',precision:10,scale:2,comment:'',unit:'',formula:'',measureColumns:[],filterColumns:[],compositeMetrics:[]
}

interface MetricFormProps {
 isOpen:boolean
 onClose:() => void
 onSave:(form:MetricFormData) => void
 saving:boolean
 /** edit mode: Pass in the Metric to be edited */
 editMetric?: Metric | null
}
/** Metric registration modal */
export function MetricFormModal({ isOpen,onClose,onSave,saving,editMetric }:MetricFormProps) {
 const { t } = useTranslation('oneSemantics')
 const isEditMode =!!editMetric
 const [step,setStep] = useState(isEditMode?2:1)
 const [form,setForm] = useState<MetricFormData>(emptyForm)
 const [loadingVersion,setLoadingVersion] = useState(false)
 const formulaRef = useRef<HTMLTextAreaElement>(null)

 // AI Related status
 const [aiInput,setAiInput] = useState('')
 const [aiLoading,setAiLoading] = useState(false)
 const [aiError,setAiError] = useState('')
 const [aiMessage,setAiMessage] = useState<{ success:boolean;text:string;recommendations?: AIRecommendation[] } | null>(null)
 const [aiSuggestedMeasures,setAiSuggestedMeasures] = useState<string[]>([])
 const [aiSuggestedFilters,setAiSuggestedFilters] = useState<string[]>([])

 // unit,modifier,root data(Lazy loading)
 const [units,setUnits] = useState<UnitDTO[]>([])
 const [modifiers,setModifiers] = useState<MetricModifierDTO[]>([])
 const [wordRoots,setWordRoots] = useState<WordRootDTO[]>([])
 const [atomicMetrics,setAtomicMetrics] = useState<Array<{ code:string;name:string;comment?: string }>>([])
 const [modifierTotal,setModifierTotal] = useState(0)
 const [atomicTotal,setAtomicTotal] = useState(0)
 const metricTypeConfig = useCallback(():Record<MetricType,{ label:string;desc:string;icon:typeof Target;color:string;bg:string }> => ({
 ATOMIC:{
 label:t('metricForm.type.atomic.label'),
 desc:t('metricForm.type.atomic.desc'),
 icon:Target,color:'text-purple-600',bg:'bg-purple-50'
 },DERIVED:{
 label:t('metricForm.type.derived.label'),
 desc:t('metricForm.type.derived.desc'),
 icon:Zap,color:'text-blue-600',bg:'bg-blue-50'
 },COMPOSITE:{
 label:t('metricForm.type.composite.label'),
 desc:t('metricForm.type.composite.desc'),
 icon:Layers,color:'text-emerald-600',bg:'bg-emerald-50'
 }
 }),[t])

 // Loading units(Lazy loading when clicking the dropdown)
 const loadUnits = useCallback(async () => {
 if (units.length > 0) return units
 const data = await fetchUnits(0,100).catch(() => ({ items:[] }))
 setUnits(data.items)
 return data.items
 },[units])

 // Load modifier(Lazy loading when clicking the dropdown)
 const loadModifiers = useCallback(async () => {
 if (modifiers.length > 0) return modifiers
 const data = await fetchModifiers(0,100).catch(() => ({ items:[],total:0 }))
 setModifiers(data.items)
 setModifierTotal(data.total || data.items.length)
 return data.items
 },[modifiers])

 // load root(Lazy loading when clicking the dropdown)
 const loadWordRoots = useCallback(async () => {
 if (wordRoots.length > 0) return wordRoots
 const data = await fetchWordRoots(0,200).catch(() => ({ items:[] }))
 setWordRoots(data.items)
 return data.items
 },[wordRoots])

 // Load a list of Atomic Metrics (for Derived Metrics)
 const loadAtomicMetrics = useCallback(async () => {
 if (atomicMetrics.length > 0) return atomicMetrics
 const data = await fetchMetrics(0,500).catch(() => ({ items:[],total:0 }))
 const atomics = data.items.filter((m) => m.type.toUpperCase() === 'ATOMIC').map((m) => ({ code:m.code,name:m.name,comment:m.comment }))
 setAtomicMetrics(atomics)
 setAtomicTotal(data.total || atomics.length)
 return atomics
 },[atomicMetrics])

 // Used to prevent repeated requests after the edit is saved successfully.
 const loadedMetricCodeRef = useRef<string | null>(null)

 // edit mode: load Metric version details (only loaded when first opened)
 useEffect(() => {
 if (!isOpen) {
 // Reset when pop-up window is closed
 loadedMetricCodeRef.current = null
 return
 }

 if (isEditMode && editMetric) {
 // If data for this Metric has already been loaded, skip repeated requests
 if (loadedMetricCodeRef.current === editMetric.code) {
 return
 }

 setStep(2)
 setLoadingVersion(true)
 loadedMetricCodeRef.current = editMetric.code

 const metricType = (editMetric.type?.toUpperCase() || 'ATOMIC') as MetricType
 fetchMetricVersion(editMetric.code,editMetric.currentVersion).then((versionData) => {
 let measureColumns:MeasureColumn[] = []
 let filterColumns:FilterColumn[] = []
 try {
 if (versionData.measureColumns) {
 measureColumns =
 typeof versionData.measureColumns === 'string'?JSON.parse(versionData.measureColumns):versionData.measureColumns
 }
 if (versionData.filterColumns) {
 const parsed =
 typeof versionData.filterColumns === 'string'?JSON.parse(versionData.filterColumns):versionData.filterColumns
 filterColumns = parsed.map((col:{ name:string;type:string;comment?: string;values?: string[] }) => ({
 name:col.name,type:col.type,comment:col.comment,values:(col.values || []).map((v:string) => ({ key:v,label:v }))
 }))
 }
 } catch {
 // JSON Parsing failed,keep empty array
 }

 const parsedDataType = parseDataTypeString(editMetric.dataType)

 // Process parentMetricCodes: Derived Metric uses first parent as baseCode, Composite Metric uses compositeMetrics
 const parentCodes = versionData.parentMetricCodes || []
 let baseCode = ''
 let compositeMetrics:Array<{ code:string;name:string }> = []
 if (metricType === 'DERIVED' && parentCodes.length > 0) {
 baseCode = parentCodes[0]
 } else if (metricType === 'COMPOSITE' && parentCodes.length > 0) {
 compositeMetrics = parentCodes.map((code) => ({ code,name:code }))
 }

 setForm({
 name:editMetric.name,code:editMetric.code,customSuffix:'',wordRoots:[],aggregation:'',baseCode,modifiers:[],type:metricType,dataType:parsedDataType.type || '',precision:parsedDataType.precision?? 10,scale:parsedDataType.scale?? 2,comment:editMetric.comment || '',unit:versionData.unit || '',unitName:versionData.unitName || '',unitSymbol:versionData.unitSymbol || '',formula:versionData.calculationFormula || '',measureColumns,filterColumns,refTableId:versionData.refTableId,refCatalogName:versionData.refCatalogName,refSchemaName:versionData.refSchemaName,refTableName:versionData.refTableName,compositeMetrics
 })
 }).catch(() => {
 const parsedDataType = parseDataTypeString(editMetric.dataType)
 setForm({
 name:editMetric.name,code:editMetric.code,customSuffix:'',wordRoots:[],aggregation:'',baseCode:'',modifiers:[],type:(editMetric.type?.toUpperCase() || 'ATOMIC') as MetricType,dataType:parsedDataType.type || '',precision:parsedDataType.precision?? 10,scale:parsedDataType.scale?? 2,comment:editMetric.comment || '',unit:'',unitName:'',unitSymbol:'',formula:'',measureColumns:[],filterColumns:[]
 })
 }).finally(() => setLoadingVersion(false))
 } else {
 setStep(1)
 setForm(emptyForm)
 }
 },[isOpen,editMetric,isEditMode])

 const handleClose = () => {
 setStep(1)
 setForm(emptyForm)
 setAiInput('')
 setAiError('')
 setAiMessage(null)
 setAiSuggestedMeasures([])
 setAiSuggestedFilters([])
 onClose()
 }

 const handleSelectType = (type:MetricType) => {
 setForm({...emptyForm,type })
 setAiInput('')
 setAiError('')
 setAiMessage(null)
 setAiSuggestedMeasures([])
 setAiSuggestedFilters([])
 setStep(2)
 }

 const handleBack = () => {
 setStep(1)
 setForm(emptyForm)
 setAiInput('')
 setAiError('')
 setAiMessage(null)
 setAiSuggestedMeasures([])
 setAiSuggestedFilters([])
 }

 const handleSave = async () => {
 if (!form.name.trim() ||!form.code.trim() ||!form.formula.trim()) return
 onSave(form)
 }

 // AI Fill out the form
 const handleAiFill = async () => {
 if (!aiInput.trim() || aiLoading) return
 setAiError('')
 setAiMessage(null)

 // Verify: different Metric types require different prerequisite selections
 if (form.type === 'ATOMIC' && form.measureColumns.length === 0) {
 setAiError(t('metricForm.ai.error.selectMeasureFirst'))
 return
 }
 if (form.type === 'DERIVED' &&!form.baseCode) {
 setAiError(t('metricForm.ai.error.selectBaseMetricFirst'))
 return
 }
 if (form.type === 'COMPOSITE' && (!form.compositeMetrics || form.compositeMetrics.length < 2)) {
 setAiError(t('metricForm.ai.error.selectCompositeMetricsFirst'))
 return
 }

 setAiLoading(true)

 try {
 // Lazy loading of unit data
 await loadUnits()

 const payload:Record<string,unknown> = {}

 if (form.type === 'ATOMIC') {
 payload.measureColumns = form.measureColumns
 payload.filterColumns = form.filterColumns
 // Pass table reference information,Let the backend query the context of the table
 payload.refCatalog = form.refCatalog
 payload.refSchema = form.refSchema
 payload.refTable = form.refTable
 } else if (form.type === 'DERIVED') {
 // Base Metric: code + name + description
 const baseMetricInfo = atomicMetrics.find((m) => m.code === form.baseCode)
 payload.baseMetric = {
 code:form.baseCode,name:baseMetricInfo?.name || form.baseCode,description:baseMetricInfo?.comment
 }
 // modifier:code + name
 payload.modifiers = form.modifiers.map((code) => {
 const mod = modifiers.find((m) => m.code === code)
 return { code,name:mod?.name || code }
 })
 // Filter column and table references(Optional)
 payload.filterColumns = form.filterColumns
 payload.refCatalog = form.refCatalog
 payload.refSchema = form.refSchema
 payload.refTable = form.refTable
 } else {
 // Composite Metric: user-selected list of Metrics (code + name + description)
 payload.metrics = (form.compositeMetrics || []).map((m) => ({
 code:m.code,name:m.name,description:m.comment
 }))
 }

 // call AI service
 const result = await aiFillMetric({
 userInput:aiInput,context:{
 metricType:form.type,payload,formOptions:{
 dataTypes:DATA_TYPES,units:form.unit?[form.unit]:undefined,wordRoots:form.wordRoots.length > 0?form.wordRoots.map((code) => {
 const root = wordRoots.find((r) => r.code === code)
 return { code,name:root?.name || code }
 }):undefined,modifiers:form.modifiers.length > 0?form.modifiers.map((code) => {
 const mod = modifiers.find((m) => m.code === code)
 return { code,name:mod?.name || code }
 }):undefined
 }
 }
 })

 // Process AI Return results
 if (!result.success) {
 setAiMessage({ success:false,text:result.message,recommendations:result.recommendations })
 return
 }

 // success:Display message and update form
 setAiMessage({ success:true,text:result.message })

 // settings AI Suggested columns
 setAiSuggestedMeasures(result.measureColumns || [])
 setAiSuggestedFilters(result.filterColumns || [])

 // Application AI Fill in the results
 setForm((prev) => {
 const newWordRoots = result.wordRoots || prev.wordRoots
 const newModifiers = result.modifiersSelected || prev.modifiers
 const newAggregation = result.aggregation || prev.aggregation

 let code:string
 if (prev.type === 'DERIVED') {
 const parts = [prev.baseCode,...newModifiers,prev.customSuffix].filter(Boolean)
 code = parts.join('_').toUpperCase()
 } else {
 const parts = [...newWordRoots,newAggregation,prev.customSuffix].filter(Boolean)
 code = parts.join('_').toUpperCase()
 }

 // According to unit code from units list search name and symbol
 const unitCode = result.unit || prev.unit
 const unitInfo = units.find((u) => u.code === unitCode)

 return {...prev,name:result.name || prev.name,wordRoots:newWordRoots,aggregation:newAggregation,modifiers:newModifiers,code,dataType:result.dataType || prev.dataType,unit:unitCode,unitName:unitInfo?.name || prev.unitName,unitSymbol:unitInfo?.symbol || prev.unitSymbol,formula:result.calculationFormula || prev.formula,comment:result.comment || prev.comment
 }
 })
 } catch {
 setAiError(t('metricForm.ai.error.fillFailed'))
 } finally {
 setAiLoading(false)
 }
 }

 // Atomic Metric: toggle measure-column selection state
 const handleMeasureColumnToggle = (col:MeasureColumn,selected:boolean) => {
 if (selected) {
 setForm({...form,measureColumns:[...form.measureColumns,col]
 })
 } else {
 setForm({...form,measureColumns:form.measureColumns.filter((c) => c.name!== col.name)
 })
 }
 }

 // Atomic Metric: toggle filter-column selection state
 const handleFilterColumnToggle = (col:MeasureColumn,selected:boolean,filterCol?: FilterColumn) => {
 if (selected && filterCol) {
 // Add or update filter columns
 const existing = form.filterColumns.find((c) => c.name === col.name)
 if (existing) {
 // Update existing filter columns
 setForm({...form,filterColumns:form.filterColumns.map((c) => c.name === col.name?filterCol:c)
 })
 } else {
 // Add new filter column
 setForm({...form,filterColumns:[...form.filterColumns,filterCol]
 })
 }
 } else {
 // Remove filter column
 setForm({...form,filterColumns:form.filterColumns.filter((c) => c.name!== col.name)
 })
 }
 }

 // Auto-generate code: Atomic/Composite Metric = WordRoots + aggregation + customSuffix
 const updateCode = useCallback((customSuffix:string,selectedWordRoots:string[],aggregation:string) => {
 const parts = [...selectedWordRoots,aggregation,customSuffix].filter(Boolean)
 const code = parts.join('_').toUpperCase()
 setForm((prev) => ({...prev,customSuffix,wordRoots:selectedWordRoots,aggregation,code
 }))
 },[])

 // Add WordRoot
 const addWordRoot = (rootCode:string) => {
 if (form.wordRoots.includes(rootCode)) return
 const newWordRoots = [...form.wordRoots,rootCode]
 updateCode(form.customSuffix,newWordRoots,form.aggregation)
 }

 // remove root
 const removeWordRoot = (rootCode:string) => {
 const newWordRoots = form.wordRoots.filter((r) => r!== rootCode)
 updateCode(form.customSuffix,newWordRoots,form.aggregation)
 }

 // Update aggregate function
 const updateAggregation = (aggregation:string) => {
 updateCode(form.customSuffix,form.wordRoots,aggregation)
 }

 // Update custom suffix
 const updateCustomSuffix = (customSuffix:string) => {
 updateCode(customSuffix,form.wordRoots,form.aggregation)
 }

 // Derived Metric: update base code and modifiers
 const updateDerivedCode = (baseCode:string,selectedModifiers:string[],customSuffix?: string) => {
 // Derived Metric code = baseCode + modifiers + customSuffix
 setForm((prev) => {
 const suffix = customSuffix?? prev.customSuffix
 const parts = [baseCode,...selectedModifiers,suffix].filter(Boolean)
 const code = parts.join('_').toUpperCase()
 return {...prev,baseCode,modifiers:selectedModifiers,customSuffix:suffix,code
 }
 })
 }

 // Derived Metric: update custom suffix
 const updateDerivedCustomSuffix = (customSuffix:string) => {
 updateDerivedCode(form.baseCode,form.modifiers,customSuffix)
 }

 const isValid = form.name.trim() && form.code.trim() && form.formula.trim()
 const metricTypeConfigMap = metricTypeConfig()
 const typeConfig = metricTypeConfigMap[form.type] || metricTypeConfigMap.ATOMIC

 // Step 1: select Metric type
 if (step === 1) {
 return (<Modal
 isOpen={isOpen}
 onClose={handleClose}
 size="sm"
 title={t('metricForm.modal.step1.title')}
 subtitle={<span className="text-xs text-slate-400">{t('metricForm.modal.step1.subtitle')}</span>}
 >
 <div className="flex flex-col min-h-0">
 <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar pr-1">
 <div className="space-y-3">
 {(['ATOMIC','DERIVED','COMPOSITE'] as MetricType[]).map((type) => {
 const config = metricTypeConfigMap[type]
 return (<button
 key={type}
 onClick={() => handleSelectType(type)}
 className="w-full flex items-start gap-4 p-5 rounded-2xl border-2 border-slate-100 dark:border-slate-800 hover:border-blue-200 dark:hover:border-blue-700 hover:bg-white dark:hover:bg-slate-800 hover:shadow-lg transition-all text-left group"
 >
 <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${config.bg} ${config.color} shadow-sm group-hover:scale-110 transition-transform`}>
 <config.icon size={24} />
 </div>
 <div className="flex-1">
 <div className="flex items-center mb-1">
 <span className="text-body-sm font-bold text-slate-900 dark:text-white">{config.label}</span>
 </div>
 <p className="text-caption text-slate-500">{config.desc}</p>
 </div>
 <div className="self-center w-7 h-7 rounded-full bg-slate-50 dark:bg-slate-800 flex items-center justify-center text-slate-300 group-hover:bg-slate-900 dark:group-hover:bg-white group-hover:text-white dark:group-hover:text-slate-900 transition-all">
 <ArrowRight size={14} />
 </div>
 </button>)
 })}
 </div>
 </div>
 </div>
 </Modal>)
 }

 // Step 2: configure Metric details
 return (<Modal
 isOpen={isOpen}
 onClose={handleClose}
 size="xl"
 title={
 <div className="flex items-center gap-3">
 {!isEditMode && (<button
 type="button"
 onClick={handleBack}
 className="w-8 h-8 inline-flex items-center justify-center hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all"
 >
 <ChevronLeft size={18} />
 </button>)}
 <span>{isEditMode?t('metricForm.modal.step2.titleEdit'):t('metricForm.modal.step2.titleCreate')}</span>
 <span className={`text-xs font-medium px-2 py-0.5 rounded ${typeConfig.bg} ${typeConfig.color}`}>
 {typeConfig.label}
 </span>
 </div>
 }
 footer={
 <>
 <ModalCancelButton onClick={handleClose} disabled={saving || loadingVersion} />
 <ModalPrimaryButton onClick={handleSave} disabled={!isValid || loadingVersion} loading={saving}>
 <CheckCircle2 size={16} /> {isEditMode?t('metricForm.modal.step2.saveChanges'):t('metricForm.modal.step2.confirmPublish')}
 </ModalPrimaryButton>
 </>
 }
 >
 <div className="flex flex-col gap-6">
 {/* AI Help write */}
 <div className="p-4 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-2xl border border-purple-100 dark:border-purple-800/50 shrink-0">
 <div className="flex items-center gap-2 mb-2">
 <Sparkles size={16} className="text-purple-500" />
 <span className="text-xs font-semibold text-purple-700 dark:text-purple-300">{t('metricForm.ai.title')}</span>
 </div>
 <div className="flex gap-2">
 <input
 type="text"
 placeholder={
 form.type === 'ATOMIC'?t('metricForm.ai.placeholder.atomic'):form.type === 'DERIVED'?t('metricForm.ai.placeholder.derived'):t('metricForm.ai.placeholder.composite')
 }
 className="flex-1 bg-white dark:bg-slate-900 border border-purple-200 dark:border-purple-700 rounded-xl px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-600 focus:outline-none focus:border-purple-500 transition-all"
 value={aiInput}
 onChange={(e) => {
 setAiInput(e.target.value)
 setAiError('')
 }}
 onKeyDown={(e) => e.key === 'Enter' &&!e.nativeEvent.isComposing && handleAiFill()}
 disabled={aiLoading}
 />
 <button
 onClick={handleAiFill}
 disabled={!aiInput.trim() || aiLoading}
 className="px-4 py-2.5 bg-purple-600 hover:bg-purple-700 disabled:bg-slate-300 dark:disabled:bg-slate-700 text-white rounded-xl text-body-sm font-medium flex items-center gap-2 transition-all"
 >
 {aiLoading?<Loader2 size={16} className="animate-spin" />:<Sparkles size={16} />}
 {aiLoading?t('metricForm.ai.generating'):t('metricForm.ai.smartFill')}
 </button>
 </div>
 {aiError && (<div className="mt-2 text-xs text-red-600 dark:text-red-400 flex items-center gap-1">
 <AlertTriangle size={14} />
 {aiError}
 </div>)}
 {aiMessage && (<div className={`mt-2 p-3 rounded-xl border ${
 aiMessage.success?'bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-700':'bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-700'
 }`}>
 <div className="flex items-start gap-2">
 {aiMessage.success?(<Sparkles size={14} className="text-emerald-500 mt-0.5 shrink-0" />):(<AlertTriangle size={14} className="text-amber-500 mt-0.5 shrink-0" />)}
 <span className={`text-xs ${
 aiMessage.success?'text-emerald-700 dark:text-emerald-300':'text-amber-700 dark:text-amber-300'
 }`}>{aiMessage.text}</span>
 </div>
 {/* Show recommendation list on failure */}
 {!aiMessage.success && aiMessage.recommendations && aiMessage.recommendations.length > 0 && (<div className="mt-3 pt-3 border-t border-amber-200 dark:border-amber-700">
 <div className="text-xs font-medium text-amber-700 dark:text-amber-300 mb-2">
 {(() => {
 const hasMetric = aiMessage.recommendations.some((r) => r.msgType === 'metric')
 const hasTable = aiMessage.recommendations.some((r) => r.msgType === 'table')
 if (hasMetric && hasTable) return t('metricForm.ai.recommendation.metricsAndTables')
 if (hasMetric) return t('metricForm.ai.recommendation.metricsOnly')
 return t('metricForm.ai.recommendation.tablesAndColumns')
 })()}
 </div>
 <div className="space-y-2">
 {aiMessage.recommendations.map((rec,idx) => (rec.msgType === 'table'?(<div key={idx} className="space-y-1">
 <div className="flex items-center gap-2 text-xs">
 <span className="px-1.5 py-0.5 rounded text-micro font-medium bg-purple-100 text-purple-700 dark:bg-purple-900/50 dark:text-purple-300">{t('metricForm.ai.recommendation.table')}</span>
 <code className="text-slate-700 dark:text-slate-300 font-mono">{rec.fullPath}</code>
 {rec.description && (<span className="text-slate-500 dark:text-slate-400">- {rec.description}</span>)}
 </div>
 {rec.columns && rec.columns.length > 0 && (<div className="ml-6 space-y-0.5">
 {rec.columns.map((col,colIdx) => (<div key={colIdx} className="flex items-center gap-2 text-xs">
 <span className="px-1.5 py-0.5 rounded text-micro font-medium bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300">{t('metricForm.ai.recommendation.column')}</span>
 <code className="text-slate-600 dark:text-slate-400 font-mono text-legal">{col.name}</code>
 {col.dataType && (<span className="text-slate-400 dark:text-slate-500 text-micro">({col.dataType})</span>)}
 {col.description && (<span className="text-slate-500 dark:text-slate-400">- {col.description}</span>)}
 </div>))}
 </div>)}
 </div>):(<div key={idx} className="flex items-center gap-2 text-xs">
 <span className="px-1.5 py-0.5 rounded text-micro font-medium bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-300">{t('metricForm.ai.recommendation.metric')}</span>
 <code className="text-slate-700 dark:text-slate-300 font-mono">{rec.code}</code>
 <span className="text-slate-600 dark:text-slate-400">{rec.name}</span>
 {rec.description && (<span className="text-slate-500 dark:text-slate-400">- {rec.description}</span>)}
 </div>)))}
 </div>
 </div>)}
 </div>)}
 </div>

 {/* Loading status */}
 {loadingVersion?(<div className="flex flex-col items-center justify-center py-16">
 <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
 <div className="text-slate-400 text-xs mt-3">{t('metricForm.loadingVersion')}</div>
 </div>):(<div className="flex-1 min-h-0">
 {/* form content - two column layout */}
 <div className="grid grid-cols-12 gap-6 min-h-[520px] items-stretch">
 <MetricFormLeft
 form={form}
 setForm={setForm}
 isEditMode={isEditMode}
 units={units.map((u) => ({ symbol:u.symbol || u.code[0],name:u.name,code:u.code }))}
 modifiers={modifiers.map((m) => ({ symbol:m.name[0],name:m.name,code:m.code }))}
 wordRoots={wordRoots.map((r) => ({ code:r.code,name:r.name }))}
 atomicMetrics={atomicMetrics}
 modifierTotal={modifierTotal}
 atomicTotal={atomicTotal}
 loadUnits={loadUnits}
 loadModifiers={loadModifiers}
 loadWordRoots={loadWordRoots}
 loadAtomicMetrics={loadAtomicMetrics}
 updateDerivedCode={updateDerivedCode}
 updateDerivedCustomSuffix={updateDerivedCustomSuffix}
 addWordRoot={addWordRoot}
 removeWordRoot={removeWordRoot}
 updateCustomSuffix={updateCustomSuffix}
 updateAggregation={updateAggregation}
 formulaRef={formulaRef}
 />
 <MetricFormRight
 form={form}
 onMeasureToggle={handleMeasureColumnToggle}
 onFilterToggle={handleFilterColumnToggle}
 onTableSelect={(catalog,schema,table) => {
 setForm((prev) => ({...prev,refCatalog:catalog,refSchema:schema,refTable:table
 }))
 }}
 onMetricsChange={(metrics) => {
 setForm((prev) => ({...prev,compositeMetrics:metrics
 }))
 }}
 aiSuggestedMeasures={aiSuggestedMeasures}
 aiSuggestedFilters={aiSuggestedFilters}
 />
 </div>
 </div>)}
 </div>
 </Modal>)
}
