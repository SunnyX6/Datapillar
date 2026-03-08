/**
 * Table basic information editing form
 * Used to edit table names,Description and table parameters
 */

import { useState,forwardRef,useImperativeHandle } from 'react'
import { useTranslation } from 'react-i18next'
import { Sparkles,Trash2 } from 'lucide-react'
import { toast } from 'sonner'

/** Table parameter type */
interface TableProperty {
 id:string
 key:string
 value:string
}

/** form data interface */
export interface TableBasicFormData {
 name:string
 comment:string
 properties:Record<string,string>
}

/** form action handle */
export interface TableBasicFormHandle {
 getData:() => TableBasicFormData
 validate:() => boolean
}

interface TableBasicFormProps {
 /** initial data */
 initialData:{
 name:string
 comment?: string
 properties?: Record<string,string>
 }
}

export const TableBasicForm = forwardRef<TableBasicFormHandle,TableBasicFormProps>(({ initialData },ref) => {
 const { t } = useTranslation('oneMeta')
 const [tableName,setTableName] = useState(initialData.name)
 const [tableComment,setTableComment] = useState(initialData.comment || '')
 const [properties,setProperties] = useState<TableProperty[]>(() => {
 if (initialData.properties) {
 return Object.entries(initialData.properties).map(([key,value],index) => ({
 id:`prop_${index}`,key,value
 }))
 }
 return []
 })

 // Methods exposed to the parent component
 useImperativeHandle(ref,() => ({
 getData:() => ({
 name:tableName,comment:tableComment,properties:properties.reduce((acc,prop) => {
 if (prop.key) acc[prop.key] = prop.value
 return acc
 },{} as Record<string,string>)
 }),validate:() => {
 if (!tableName.trim()) {
 toast.error(t('metadataForm.tableBasic.toast.enterName'))
 return false
 }
 return true
 }
 }), [tableName, tableComment, properties, t])

 // Table parameter operations
 const handleAddProperty = () => {
 setProperties((prev) => [...prev,{ id:`prop_${Date.now()}`,key:'',value:'' }])
 }

 const handleDeleteProperty = (id:string) => {
 setProperties((prev) => prev.filter((p) => p.id!== id))
 }

 const handlePropertyChange = (id:string,field:'key' | 'value',value:string) => {
 setProperties((prev) => prev.map((p) => (p.id === id?{...p,[field]:value }:p)))
 }

 return (<div className="space-y-4">
 {/* Smart reminder card - put on top */}
 <div className="p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800 rounded-lg">
 <div className="flex items-center gap-1.5 text-blue-600 dark:text-blue-400 mb-1.5">
 <Sparkles size={14} />
 <span className="text-xs font-semibold">{t('metadataForm.tableBasic.tip.title')}</span>
 </div>
 <p className="text-xs text-blue-600/80 dark:text-blue-400/80 leading-relaxed">
 {t('metadataForm.tableBasic.tip.content')}</p>
 </div>

 {/* table name */}
 <div className="space-y-1.5">
 <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
 {t('metadataForm.tableBasic.field.name')} <span className="text-red-500">*</span>
 </label>
 <input
 type="text"
 value={tableName}
 onChange={(e) => setTableName(e.target.value)}
 placeholder={t('metadataForm.tableBasic.placeholder.name')}
 className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 {tableName!== initialData.name && (<p className="text-xs text-amber-600 dark:text-amber-400">
 ⚠️ {t('metadataForm.tableBasic.warning.renameImpact')}
 </p>)}
 </div>

 {/* Description information */}
 <div className="space-y-1.5">
 <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
 {t('metadataForm.tableBasic.field.description')}
 </label>
 <textarea
 value={tableComment}
 onChange={(e) => setTableComment(e.target.value)}
 placeholder={t('metadataForm.tableBasic.placeholder.description')}
 rows={3}
 className="w-full px-3 py-2 text-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>

 {/* Table parameters */}
 <div className="space-y-1.5">
 <div className="flex items-center justify-between">
 <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">{t('metadataForm.tableBasic.field.properties')}</label>
 <button
 type="button"
 onClick={handleAddProperty}
 className="text-xs text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
 >
 + {t('metadataForm.tableBasic.action.add')}
 </button>
 </div>
 {properties.length === 0?(<div className="py-3 text-center text-xs text-slate-400 border border-dashed border-slate-200 dark:border-slate-700 rounded-lg">
 {t('metadataForm.tableBasic.empty.noProperties')}
 </div>):(<div className="space-y-1.5 max-h-40 overflow-y-auto custom-scrollbar">
 {properties.map((prop) => (<div key={prop.id} className="flex items-center gap-1">
 <input
 type="text"
 placeholder={t('metadataForm.tableBasic.placeholder.propertyName')}
 value={prop.key}
 onChange={(e) => handlePropertyChange(prop.id,'key',e.target.value)}
 className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
 />
 <span className="text-slate-300 flex-shrink-0">=</span>
 <input
 type="text"
 placeholder={t('metadataForm.tableBasic.placeholder.propertyValue')}
 value={prop.value}
 onChange={(e) => handlePropertyChange(prop.id,'value',e.target.value)}
 className="w-[45%] min-w-0 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-md focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500 truncate"
 />
 <button
 type="button"
 onClick={() => handleDeleteProperty(prop.id)}
 className="p-1 text-slate-300 hover:text-red-500 rounded transition-colors flex-shrink-0"
 >
 <Trash2 size={12} />
 </button>
 </div>))}
 </div>)}
 </div>
 </div>)
 })

TableBasicForm.displayName = 'TableBasicForm'
