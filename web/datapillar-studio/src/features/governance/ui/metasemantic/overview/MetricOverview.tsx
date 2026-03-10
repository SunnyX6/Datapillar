import { useState,useEffect,useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { createPortal } from 'react-dom'
import { Target,X,Info,Code,GitBranch,Layers,Share2,History,Loader2,Check,RotateCcw } from 'lucide-react'
import { Badge } from '../components'
import type { Metric } from '../types'
import { drawerWidthClassMap,iconSizeToken } from '@/design-tokens/dimensions'
import { fetchMetricVersion,fetchMetricVersionNumbers,switchMetricVersion as apiSwitchMetricVersion } from '@/services/oneMetaSemanticService'
import { formatTime } from '@/utils'

/** Metric type color mapping */
const TYPE_VARIANTS:Record<string,'blue' | 'purple' | 'warning'> = {
 ATOMIC:'blue',DERIVED:'purple',COMPOSITE:'warning'
}

const skeletonBaseClassName = 'inline-block animate-pulse rounded bg-slate-200/70 dark:bg-slate-700/40'

function SkeletonBlock({ className }:{ className:string }) {
 return <span aria-hidden="true" className={`${skeletonBaseClassName} ${className}`} />
}

interface MetricVersionDetail {
 name?: string
 code?: string
 type?: string
 dataType?: string
 comment?: string
 calculationFormula?: string
 unit?: string
 unitName?: string
 unitSymbol?: string
 refCatalogName?: string
 refSchemaName?: string
 refTableName?: string
 refColumnName?: string
 audit?: {
 creator?: string
 createTime?: string
 }
}

interface MetricOverviewProps {
 metric:Metric
 onClose:() => void
 onVersionSwitch?: (updatedMetric:Metric) => void
}

export function MetricOverview({ metric,onClose,onVersionSwitch }:MetricOverviewProps) {
 const { t } = useTranslation('oneSemantics')
 const [versionDetail,setVersionDetail] = useState<MetricVersionDetail | null>(null)
 const [loading,setLoading] = useState(true)
 const [versions,setVersions] = useState<number[]>([])
 const [selectedVersion,setSelectedVersion] = useState(metric.currentVersion)
 const [showVersionPanel,setShowVersionPanel] = useState(false)
 const [loadingVersions,setLoadingVersions] = useState(false)
 const [switching,setSwitching] = useState(false)

 // when metric When changing,Reset version status
 useEffect(() => {
 setSelectedVersion(metric.currentVersion)
 setVersions([])
 setShowVersionPanel(false)
 },[metric.code,metric.currentVersion])

 // use ref Track whether there is data,avoid versionDetail Add dependency array
 const hasDataRef = useRef(!!versionDetail)

 // Load version details
 useEffect(() => {
 let cancelled = false
 // Only shown on first load loading,Keep old data to avoid jitter when switching versions
 if (!hasDataRef.current) {
 setLoading(true)
 }
 fetchMetricVersion(metric.code,selectedVersion).then((data) => {
 if (!cancelled) {
 setVersionDetail({
 name:data.name,code:data.code,type:data.type,dataType:data.dataType,comment:data.comment,calculationFormula:data.calculationFormula,unit:data.unit,unitName:data.unitName,unitSymbol:data.unitSymbol,refCatalogName:data.refCatalogName,refSchemaName:data.refSchemaName,refTableName:data.refTableName,refColumnName:data.refColumnName,audit:data.audit
 })
 hasDataRef.current = true
 }
 }).catch(() => {
 if (!cancelled) setVersionDetail(null)
 }).finally(() => {
 if (!cancelled) setLoading(false)
 })
 return () => { cancelled = true }
 },[metric.code,selectedVersion])

 // Load version list
 const loadVersions = async () => {
 if (versions.length > 0) {
 setShowVersionPanel(true)
 return
 }
 setLoadingVersions(true)
 try {
 const data = await fetchMetricVersionNumbers(metric.code)
 setVersions(data.sort((a,b) => b - a)) // Descending order
 setShowVersionPanel(true)
 } catch {
 setVersions([])
 } finally {
 setLoadingVersions(false)
 }
 }

 const handleVersionSelect = (version:number) => {
 setSelectedVersion(version)
 setShowVersionPanel(false)
 }

 // Switch current version
 const handleSwitchVersion = async () => {
 if (selectedVersion === metric.currentVersion) return
 setSwitching(true)
 try {
 const versionData = await apiSwitchMetricVersion(metric.code,selectedVersion)
 // Use the returned version data to construct an updated Metric
 const updatedMetric:Metric = {...metric,name:versionData.name,code:versionData.code,type:versionData.type,dataType:versionData.dataType,unit:versionData.unit,unitName:versionData.unitName,comment:versionData.comment,currentVersion:selectedVersion
 }
 onVersionSwitch?.(updatedMetric)
 onClose()
 } catch {
 // The error was passed by the unity client toast show
 } finally {
 setSwitching(false)
 }
 }

 const typeKey = (versionDetail?.type || '').toUpperCase()
 const typeVariant = TYPE_VARIANTS[typeKey] || 'blue'
 const typeLabel = typeKey
 ? t(`metricExplorer.type.${typeKey}`, { defaultValue:typeKey })
 : '-'

 return createPortal(<aside className={`fixed right-0 top-14 bottom-0 z-30 ${drawerWidthClassMap.responsive} bg-white dark:bg-slate-900 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500`}>
 {/* header height aligns with Metric Center list page */}
 <div className="h-12 md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 md:px-6 flex items-center justify-between flex-shrink-0 shadow-sm">
 <div className="flex items-center gap-2">
 <div className="p-1.5 bg-purple-600 text-white rounded-lg shadow-sm">
 <Target size={iconSizeToken.medium} />
 </div>
 <h2 className="text-body-sm font-semibold text-slate-800 dark:text-slate-100">{t('metricOverview.title')}</h2>
 </div>
 <button
 type="button"
 onClick={onClose}
 className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
 aria-label={t('metricOverview.closeAria')}
 >
 <X size={iconSizeToken.large} className="text-slate-400" />
 </button>
 </div>

 {/* content area */}
 <div className="flex-1 min-h-0 overflow-auto p-6 custom-scrollbar">
 {/* Metric name and type */}
 <div className="mb-6">
 <div className="flex items-center gap-2 mb-2">
 <h1 className="text-heading font-semibold text-slate-900 dark:text-slate-100 tracking-tight">
 {loading?<SkeletonBlock className="h-6 w-56" />:versionDetail?.name || '-'}
 </h1>
 {loading?<SkeletonBlock className="h-5 w-16 rounded-full" />:<Badge variant={typeVariant}>{typeLabel}</Badge>}
 </div>
 <div className="flex items-center gap-3 mb-3">
 <span className="text-body-xs font-mono font-semibold text-purple-600 dark:text-purple-400 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-200 dark:border-purple-700 px-2.5 py-0.5 rounded-lg tracking-wide">
 {versionDetail?.code || metric.code}
 </span>
 <span className="text-micro font-mono text-blue-600 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded">
 v{selectedVersion}
 {selectedVersion!== metric.currentVersion && (<span className="text-amber-500 ml-1">({t('metricOverview.version.notCurrent')})</span>)}
 </span>
 {selectedVersion!== metric.currentVersion && (<button
 onClick={handleSwitchVersion}
 disabled={switching}
 className="text-micro font-medium text-white bg-emerald-500 hover:bg-emerald-600 px-2 py-0.5 rounded transition-colors disabled:opacity-50 flex items-center gap-1"
 >
 {switching?(<Loader2 size={12} className="animate-spin" />):(<RotateCcw size={12} />)}
 {t('metricOverview.version.switch')}
 </button>)}
 </div>
 <p className="text-slate-500 dark:text-slate-400 text-body-sm leading-relaxed min-h-[20px]">
 {loading?<SkeletonBlock className="h-5 w-full max-w-[26rem]" />:versionDetail?.comment || t('metricOverview.descriptionEmpty')}
 </p>
 </div>

 <div className="space-y-6">
 {/* Technical properties */}
 <section>
 <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
 <Info size={iconSizeToken.small} className="text-blue-500" /> {t('metricOverview.section.technical')}
 </div>
 <div className="grid grid-cols-2 gap-3">
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.metricType')}</div>
 <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
 {loading?<SkeletonBlock className="h-5 w-16" />:typeLabel}
 </div>
 </div>
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.dataType')}</div>
 <div className="text-body-sm font-mono font-semibold text-cyan-600 dark:text-cyan-400">
 {loading?<SkeletonBlock className="h-5 w-24" />:versionDetail?.dataType || '-'}
 </div>
 </div>
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.currentVersion')}</div>
 <div className="text-body-sm font-mono font-semibold text-slate-700 dark:text-slate-300">v{metric.currentVersion}</div>
 </div>
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.unit')}</div>
 <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
 {loading?<SkeletonBlock className="h-5 w-20" />:(versionDetail?.unitName?(<>
 {versionDetail.unitSymbol && <span className="text-amber-500 mr-1">{versionDetail.unitSymbol}</span>}
 {versionDetail.unitName}
 </>):'-')}
 </div>
 </div>
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.creator')}</div>
 <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
 {loading?<SkeletonBlock className="h-5 w-20" />:versionDetail?.audit?.creator || '-'}
 </div>
 </div>
 <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700">
 <div className="text-micro font-semibold text-slate-400 uppercase mb-0.5">{t('metricOverview.field.createTime')}</div>
 <div className="text-body-sm font-semibold text-slate-700 dark:text-slate-300">
 {loading?<SkeletonBlock className="h-5 w-28" />:formatTime(versionDetail?.audit?.createTime)}
 </div>
 </div>
 </div>
 </section>

 {/* formula expression */}
 <section>
 <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
 <Code size={iconSizeToken.small} className="text-blue-500" /> {t('metricOverview.section.formula')}
 </div>
 {loading?(<div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-700">
 <SkeletonBlock className="h-4 w-11/12" />
 <SkeletonBlock className="h-4 w-9/12 mt-2" />
 <SkeletonBlock className="h-4 w-10/12 mt-2" />
 </div>):versionDetail?.calculationFormula?(<div className="p-4 rounded-xl bg-slate-900 dark:bg-slate-950 border border-slate-800 shadow-inner">
 <code className="text-caption text-emerald-400 font-mono leading-relaxed whitespace-pre-wrap break-all">
 {versionDetail.calculationFormula}
 </code>
 </div>):(<div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-center text-slate-400 text-caption">
 {t('metricOverview.formulaEmpty')}
 </div>)}
 </section>

 {/* bloodline preview */}
 <section>
 <div className="text-micro font-semibold text-slate-400 uppercase tracking-wider mb-3 flex items-center gap-1.5">
 <GitBranch size={iconSizeToken.small} className="text-blue-500" /> {t('metricOverview.section.lineage')}
 </div>
 <div className="h-32 rounded-xl border-2 border-dashed border-slate-100 dark:border-slate-800 flex items-center justify-center bg-slate-50/50 dark:bg-slate-800/50">
 <div className="flex flex-col items-center gap-1.5 text-slate-300 dark:text-slate-600">
 <Layers size={iconSizeToken.huge} />
 <span className="text-caption font-medium">{t('metricOverview.lineageLoading')}</span>
 </div>
 </div>
 </section>
 </div>
 </div>

 {/* Bottom operation */}
 <div className="p-5 border-t border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 flex gap-3 flex-shrink-0 relative">
 <button className="flex-1 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 py-2.5 rounded-xl font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-all flex items-center justify-center gap-1.5 shadow-sm text-body-sm">
 <Share2 size={iconSizeToken.medium} /> {t('metricOverview.actions.share')}
 </button>
 <div className="flex-1 relative">
 <button
 onClick={loadVersions}
 disabled={loadingVersions}
 className="w-full bg-blue-600 text-white py-2.5 rounded-xl font-medium hover:bg-blue-700 shadow-lg transition-all flex items-center justify-center gap-1.5 text-body-sm disabled:opacity-50"
 >
 {loadingVersions?(<Loader2 size={iconSizeToken.medium} className="animate-spin" />):(<History size={iconSizeToken.medium} />)}
 {t('metricOverview.version.historyButton')}
 </button>

 {/* Version selection popup */}
 {showVersionPanel && (<div className="absolute bottom-full mb-2 left-0 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg overflow-hidden z-10">
 <div className="p-2 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
 <span className="text-micro font-semibold text-slate-500">{t('metricOverview.version.panelTitle')}</span>
 <button
 onClick={() => setShowVersionPanel(false)}
 className="p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded text-slate-400"
 >
 <X size={12} />
 </button>
 </div>
 <div className="max-h-48 overflow-y-auto custom-scrollbar">
 {versions.length === 0?(<div className="p-4 text-center text-slate-400 text-caption">{t('metricOverview.version.empty')}</div>):(versions.map((v) => (<button
 key={v}
 onClick={() => handleVersionSelect(v)}
 className={`w-full flex items-center justify-between px-3 py-2 text-caption transition-colors ${
 v === selectedVersion?'bg-blue-50 dark:bg-blue-900/30 text-blue-600':'hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300'
 }`}
 >
 <span className="font-mono">v{v}</span>
 <span className="flex items-center gap-1">
 {v === metric.currentVersion && (<span className="text-micro text-emerald-500 font-medium">{t('metricOverview.version.current')}</span>)}
 {v === selectedVersion && <Check size={14} className="text-blue-600" />}
 </span>
 </button>)))}
 </div>
 </div>)}
 </div>
 </div>
 </aside>,document.body)
}
