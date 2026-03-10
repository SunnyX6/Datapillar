/**
 * Semantic Component Library Sidebar
 * Contains two categories:modifiers and units
 * Support folding/Expand,Default folded
 */

import { useState,useEffect,useCallback,useRef,useLayoutEffect,useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { createPortal } from 'react-dom'
import { Sparkles,Scale,Plus,ChevronLeft,ChevronRight,Loader2,Trash2,ChevronDown,X,Check,Tag,Pencil } from 'lucide-react'
import { iconSizeToken,menuWidthClassMap,panelWidthClassMap } from '@/design-tokens/dimensions'
import { Modal,ModalCancelButton,ModalPrimaryButton,Tooltip } from '@/components/ui'
import {
 fetchUnits,fetchModifiers,createUnit,createModifier,deleteUnit,deleteModifier,updateModifier,updateUnit,fetchValueDomains,type UnitDTO,type MetricModifierDTO
} from '@/services/oneMetaSemanticService'

// constant definition

type TabType = 'MODIFIER' | 'UNIT'

/** Range grouped data */
interface ValueDomainGroup {
 domainCode:string
 domainName:string
 items:Array<{ value:string;label?: string }>
}

interface SymbolItemDef {
 symbol:string
 labelKey:string
}

interface SymbolGroupDef {
 nameKey:string
 color:string
 items:SymbolItemDef[]
}

interface SymbolGroup {
 name:string
 color:string
 items:Array<{ symbol:string;label:string }>
}

const SYMBOL_GROUP_DEFS:SymbolGroupDef[] = [{
 nameKey:'currency',color:'text-amber-500 bg-amber-50 dark:bg-amber-900/30',items:[{ symbol:'¥',labelKey:'rmb' },{ symbol:'$',labelKey:'usd' },{ symbol:'€',labelKey:'euro' },{ symbol:'£',labelKey:'pound' },{ symbol:'₩',labelKey:'krw' },{ symbol:'₹',labelKey:'inr' }]
 },{
 nameKey:'ratio',color:'text-blue-500 bg-blue-50 dark:bg-blue-900/30',items:[{ symbol:'%',labelKey:'percentage' },{ symbol:'‰',labelKey:'permille' },{ symbol:'‱',labelKey:'permyriad' }]
 },{
 nameKey:'temperature',color:'text-rose-500 bg-rose-50 dark:bg-rose-900/30',items:[{ symbol:'℃',labelKey:'celsius' },{ symbol:'℉',labelKey:'fahrenheit' },{ symbol:'K',labelKey:'kelvin' }]
 },{
 nameKey:'length',color:'text-cyan-500 bg-cyan-50 dark:bg-cyan-900/30',items:[{ symbol:'m',labelKey:'meter' },{ symbol:'㎞',labelKey:'kilometer' },{ symbol:'㎝',labelKey:'centimeter' },{ symbol:'㎜',labelKey:'millimeter' }]
 },{
 nameKey:'areaVolume',color:'text-purple-500 bg-purple-50 dark:bg-purple-900/30',items:[{ symbol:'㎡',labelKey:'squareMeter' },{ symbol:'㎥',labelKey:'cubicMeter' }]
 },{
 nameKey:'weight',color:'text-orange-500 bg-orange-50 dark:bg-orange-900/30',items:[{ symbol:'㎏',labelKey:'kilogram' },{ symbol:'g',labelKey:'gram' },{ symbol:'㎎',labelKey:'milligram' },{ symbol:'t',labelKey:'ton' }]
 },{
 nameKey:'time',color:'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/30',items:[{ symbol:'s',labelKey:'second' },{ symbol:'min',labelKey:'minute' },{ symbol:'h',labelKey:'hour' },{ symbol:'d',labelKey:'day' }]
 }]

const buildSymbolGroups = (t:(key:string) => string):SymbolGroup[] => SYMBOL_GROUP_DEFS.map((group) => ({
 name:t(`componentLibrary.symbolGroups.names.${group.nameKey}`),
 color:group.color,
 items:group.items.map((item) => ({
 symbol:item.symbol,
 label:t(`componentLibrary.symbolGroups.labels.${item.labelKey}`)
 }))
}))

const normalizeText = (value:string) => value.trim().toLowerCase()

const findSymbolByName = (name:string,symbolGroups:SymbolGroup[]) => {
 const normalized = normalizeText(name)
 if (!normalized) return null
 for (const group of symbolGroups) {
 const item = group.items.find((i) => normalizeText(i.label?? '') === normalized)
 if (item) return item.symbol
 }
 return null
}

const filterSymbolGroups = (query:string,symbolGroups:SymbolGroup[]) => {
 const normalized = normalizeText(query)
 if (!normalized) return symbolGroups
 return symbolGroups.map((group) => ({...group,items:group.items.filter((item) => {
 const labelText = normalizeText(item.label?? '')
 return labelText.includes(normalized) || item.symbol.includes(query.trim())
 })
 })).filter((group) => group.items.length > 0)
}

const findSymbolMeta = (symbol:string,symbolGroups:SymbolGroup[]) => {
 const normalized = symbol.trim()
 if (!normalized) return null
 for (const group of symbolGroups) {
 const item = group.items.find((i) => i.symbol === normalized)
 if (item) return {...item,color:group.color }
 }
 return null
}

/** Create unit modal box */
function CreateUnitModal({
 isOpen,onClose,onCreated
}:{
 isOpen:boolean
 onClose:() => void
 onCreated:(unit:UnitDTO) => void
}) {
 const { t } = useTranslation('oneSemantics')
 const [form,setForm] = useState({ code:'',name:'',symbol:'',comment:'' })
 const [saving,setSaving] = useState(false)
 const [nameDropdownOpen,setNameDropdownOpen] = useState(false)
 const nameTriggerRef = useRef<HTMLInputElement>(null)
 const nameDropdownRef = useRef<HTMLDivElement>(null)
 const [nameDropdownPos,setNameDropdownPos] = useState<{ top:number;left:number;width:number } | null>(null)
 const [nameFilterActive,setNameFilterActive] = useState(false)
 const symbolGroups = useMemo(() => buildSymbolGroups(t),[t])
 const nameFilterQuery = nameFilterActive?form.name:''
 const filteredNameGroups = filterSymbolGroups(nameFilterQuery,symbolGroups)
 const symbolMeta = findSymbolMeta(form.symbol,symbolGroups)
 const hasSymbol =!!form.symbol.trim()

 // Click outside to close
 useEffect(() => {
 if (!nameDropdownOpen) return
 const handleClickOutside = (e:MouseEvent) => {
 const target = e.target as Node
 if (nameTriggerRef.current?.contains(target)) return
 if (nameDropdownRef.current?.contains(target)) return
 setNameDropdownOpen(false)
 setNameFilterActive(false)
 }
 document.addEventListener('mousedown',handleClickOutside)
 return () => document.removeEventListener('mousedown',handleClickOutside)
 },[nameDropdownOpen])

 // Calculate drop down position
 useLayoutEffect(() => {
 if (!nameDropdownOpen) return
 const updatePosition = () => {
 const btn = nameTriggerRef.current
 if (!btn) return
 const rect = btn.getBoundingClientRect()
 const dropdownHeight = 340
 const spaceBelow = window.innerHeight - rect.bottom - 20
 const spaceAbove = rect.top - 20

 // If there is not enough space below and there is more space above,expand upward
 if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
 setNameDropdownPos({
 top:rect.top - Math.min(dropdownHeight,spaceAbove) - 4,left:rect.left,width:rect.width
 })
 } else {
 setNameDropdownPos({ top:rect.bottom + 4,left:rect.left,width:rect.width })
 }
 }
 updatePosition()
 window.addEventListener('resize',updatePosition)
 window.addEventListener('scroll',updatePosition,true)
 return () => {
 window.removeEventListener('resize',updatePosition)
 window.removeEventListener('scroll',updatePosition,true)
 }
 },[nameDropdownOpen])

 const handleSave = async () => {
 if (!form.code.trim() ||!form.name.trim() || saving) return
 setSaving(true)
 try {
 const unit = await createUnit({
 code:form.code.trim().toUpperCase(),name:form.name.trim(),symbol:form.symbol.trim() || undefined,comment:form.comment.trim() || undefined
 })
 onCreated(unit)
 setForm({ code:'',name:'',symbol:'',comment:'' })
 onClose()
 } catch {
 // Creation failed
 } finally {
 setSaving(false)
 }
 }

 const handleNameChange = (name:string) => {
 const matchedSymbol = findSymbolByName(name,symbolGroups)
 setNameFilterActive(true)
 setNameDropdownOpen(true)
 setForm((prev) => ({...prev,name,symbol:matchedSymbol?? prev.symbol
 }))
 }

 const handleSelectName = (symbol:string,name?: string) => {
 const nextName = name?? symbol
 setForm((prev) => ({...prev,name:nextName,symbol
 }))
 setNameDropdownOpen(false)
 setNameFilterActive(false)
 }

 return (<Modal
 isOpen={isOpen}
 onClose={onClose}
 title={t('componentLibrary.modal.createUnit')}
 size="mini"
 footerRight={
 <>
 <ModalCancelButton onClick={onClose} disabled={saving} />
 <ModalPrimaryButton
 onClick={handleSave}
 disabled={!form.code.trim() ||!form.name.trim()}
 loading={saving}
 variant="amber"
 >
 {t('componentLibrary.actions.save')}
 </ModalPrimaryButton>
 </>
 }
 >
 <div className="space-y-4">
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.codeRequired')}</label>
 <input
 type="text"
 value={form.code}
 onChange={(e) => setForm({...form,code:e.target.value.toUpperCase() })}
 placeholder={t('componentLibrary.placeholder.codeCny')}
 className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.nameRequired')}</label>
 <div className="relative">
 {hasSymbol && (<span
 className={`absolute left-3 top-1/2 -translate-y-1/2 size-6 flex items-center justify-center rounded text-body-sm font-bold ${
 symbolMeta?symbolMeta.color:'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
 }`}
 >
 {form.symbol}
 </span>)}
 <input
 ref={nameTriggerRef}
 type="text"
 value={form.name}
 onChange={(e) => handleNameChange(e.target.value)}
 onFocus={() => {
 setNameDropdownOpen(true)
 setNameFilterActive(false)
 }}
 placeholder={t('componentLibrary.placeholder.nameRmb')}
 className={`w-full ${hasSymbol?'pl-12 pr-10':'px-4 pr-10'} py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600`}
 />
 <button
 type="button"
 onClick={() => {
 setNameFilterActive(false)
 setNameDropdownOpen((open) =>!open)
 }}
 className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-500 transition-colors"
 aria-label={t('componentLibrary.aria.openUnitNameList')}
 >
 <ChevronDown size={14} className={`transition-transform ${nameDropdownOpen?'rotate-180':''}`} />
 </button>
 </div>
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.symbol')}</label>
 <input
 type="text"
 value={form.symbol}
 onChange={(e) => setForm({...form,symbol:e.target.value })}
 placeholder={t('componentLibrary.placeholder.symbolOptional')}
 className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.description')}</label>
 <input
 type="text"
 value={form.comment}
 onChange={(e) => setForm({...form,comment:e.target.value })}
 placeholder={t('componentLibrary.placeholder.descriptionOptional')}
 className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 </div>

 {/* Name selection drop down */}
 {nameDropdownOpen && nameDropdownPos && createPortal(<div
 ref={nameDropdownRef}
 style={{
 '--symbol-dropdown-top':`${nameDropdownPos.top}px`,'--symbol-dropdown-left':`${nameDropdownPos.left}px`,'--symbol-dropdown-width':`${nameDropdownPos.width}px`
 } as React.CSSProperties}
 className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl overflow-hidden top-[var(--symbol-dropdown-top)] left-[var(--symbol-dropdown-left)] w-[var(--symbol-dropdown-width)]"
 >
 {/* grouped list */}
 <div className="max-h-64 overflow-y-auto p-2 custom-scrollbar">
 {filteredNameGroups.length === 0?(<div className="py-6 text-center text-caption text-slate-400">{t('componentLibrary.dropdown.noMatchingName')}</div>):(filteredNameGroups.map((group) => (<div key={group.name} className="mb-2 last:mb-0">
 <div className="px-2 py-1 text-micro font-semibold text-slate-400 uppercase">{group.name}</div>
 <div className="grid grid-cols-3 gap-1">
 {group.items.map((item) => (<button
 key={item.symbol}
 type="button"
 onClick={() => handleSelectName(item.symbol,item.label)}
 className={`flex items-center gap-2 px-2 py-1.5 rounded-lg transition-all ${
 form.name === item.label?'bg-amber-100 dark:bg-amber-900/50 ring-1 ring-amber-400':'hover:bg-slate-50 dark:hover:bg-slate-800'
 }`}
 >
 <span className={`w-5 h-5 flex items-center justify-center rounded text-xs font-bold ${group.color}`}>
 {item.symbol}
 </span>
 <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.label}</span>
 </button>))}
 </div>
 </div>)))}
 </div>
 </div>,document.body)}
 </Modal>)
}

/** Create a modifier modal */
function CreateModifierModal({
 isOpen,onClose,onCreated
}:{
 isOpen:boolean
 onClose:() => void
 onCreated:(modifier:MetricModifierDTO) => void
}) {
 const { t } = useTranslation('oneSemantics')
 const [form,setForm] = useState({ code:'',name:'',comment:'',modifierType:'' })
 const [saving,setSaving] = useState(false)
 const [valueDomains,setValueDomains] = useState<ValueDomainGroup[]>([])
 const [selectedDomain,setSelectedDomain] = useState<string>('')
 const [loadingDomains,setLoadingDomains] = useState(false)

 // drop down state
 const [dropdownOpen,setDropdownOpen] = useState(false)
 const [hoveredDomain,setHoveredDomain] = useState<string | null>(null)
 const [isHoveringCard,setIsHoveringCard] = useState(false)
 const triggerRef = useRef<HTMLButtonElement>(null)
 const dropdownRef = useRef<HTMLDivElement>(null)
 const itemCardRef = useRef<HTMLDivElement>(null)
 const hoverItemRef = useRef<HTMLDivElement | null>(null)
 const leaveTimerRef = useRef<number | null>(null)
 const [dropdownPos,setDropdownPos] = useState<{ top:number;left:number;width:number } | null>(null)
 const [itemCardPos,setItemCardPos] = useState<{ top:number;left:number } | null>(null)

 // Load range list
 useEffect(() => {
 if (!isOpen) return
 setLoadingDomains(true)
 fetchValueDomains(0,100).then((res) => {
 const groups:ValueDomainGroup[] = res.items.map((domain) => ({
 domainCode:domain.domainCode,domainName:domain.domainName,items:domain.items.map((item) => ({
 value:item.value,label:item.label || item.value
 }))
 }))
 setValueDomains(groups)
 }).catch(() => {}).finally(() => setLoadingDomains(false))
 },[isOpen])

 // Click outside to close
 useEffect(() => {
 if (!dropdownOpen) return
 const handleClickOutside = (e:MouseEvent) => {
 const target = e.target as Node
 if (triggerRef.current?.contains(target)) return
 if (dropdownRef.current?.contains(target)) return
 if (itemCardRef.current?.contains(target)) return
 setDropdownOpen(false)
 setHoveredDomain(null)
 setIsHoveringCard(false)
 }
 document.addEventListener('mousedown',handleClickOutside)
 return () => document.removeEventListener('mousedown',handleClickOutside)
 },[dropdownOpen])

 // Cleanup timer
 useEffect(() => {
 return () => {
 if (leaveTimerRef.current) clearTimeout(leaveTimerRef.current)
 }
 },[])

 // Calculate drop down position
 useLayoutEffect(() => {
 if (!dropdownOpen) return
 const updatePosition = () => {
 const btn = triggerRef.current
 if (!btn) return
 const rect = btn.getBoundingClientRect()
 const dropdownHeight = 280
 const spaceBelow = window.innerHeight - rect.bottom - 20
 const spaceAbove = rect.top - 20

 if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
 setDropdownPos({ top:rect.top - Math.min(dropdownHeight,spaceAbove) - 4,left:rect.left,width:rect.width })
 } else {
 setDropdownPos({ top:rect.bottom + 4,left:rect.left,width:rect.width })
 }
 }
 updatePosition()
 window.addEventListener('resize',updatePosition)
 window.addEventListener('scroll',updatePosition,true)
 return () => {
 window.removeEventListener('resize',updatePosition)
 window.removeEventListener('scroll',updatePosition,true)
 }
 },[dropdownOpen])

 // Calculate enumeration item card position
 useLayoutEffect(() => {
 const updatePos = () => {
 if (!hoveredDomain ||!hoverItemRef.current) {
 if (!isHoveringCard) setItemCardPos(null)
 return
 }
 const domain = valueDomains.find((g) => g.domainCode === hoveredDomain)
 if (!domain?.items.length) {
 setItemCardPos(null)
 return
 }
 const itemRect = hoverItemRef.current.getBoundingClientRect()
 const cardWidth = 200
 const cardHeight = Math.min(domain.items.length * 44 + 12,220)

 let top = itemRect.top
 let left = itemRect.right + 8

 if (top + cardHeight > window.innerHeight - 20) {
 top = window.innerHeight - cardHeight - 20
 }
 if (left + cardWidth > window.innerWidth - 20) {
 left = itemRect.left - cardWidth - 8
 }
 setItemCardPos({ top,left })
 }
 const rafId = requestAnimationFrame(updatePos)
 return () => cancelAnimationFrame(rafId)
 },[hoveredDomain,isHoveringCard,valueDomains])

 // Get display text(code (Name))
 const getDisplayText = () => {
 if (!selectedDomain ||!form.modifierType) return ''
 const domain = valueDomains.find((g) => g.domainCode === selectedDomain)
 const item = domain?.items.find((i) => i.value === form.modifierType)
 if (!item) return ''
 return item.label?`${item.value} (${item.label})`:item.value
 }

 // Mouse event handling
 const handleItemMouseEnter = (domainCode:string) => {
 if (leaveTimerRef.current) {
 clearTimeout(leaveTimerRef.current)
 leaveTimerRef.current = null
 }
 setHoveredDomain(domainCode)
 }

 const handleItemMouseLeave = () => {
 leaveTimerRef.current = window.setTimeout(() => {
 if (!isHoveringCard) setHoveredDomain(null)
 leaveTimerRef.current = null
 },150)
 }

 const handleCardMouseEnter = () => {
 if (leaveTimerRef.current) {
 clearTimeout(leaveTimerRef.current)
 leaveTimerRef.current = null
 }
 setIsHoveringCard(true)
 }

 const handleCardMouseLeave = () => {
 setIsHoveringCard(false)
 setHoveredDomain(null)
 }

 // Select enumeration item
 const handleSelectItem = (domainCode:string,itemValue:string) => {
 setSelectedDomain(domainCode)
 setForm({...form,modifierType:itemValue })
 setDropdownOpen(false)
 setHoveredDomain(null)
 setIsHoveringCard(false)
 }

 // Clear selection
 const handleClear = (e:React.MouseEvent) => {
 e.stopPropagation()
 setSelectedDomain('')
 setForm({...form,modifierType:'' })
 }

 const handleSave = async () => {
 if (!form.code.trim() ||!form.name.trim() || saving) return
 setSaving(true)
 try {
 const modifier = await createModifier({
 code:form.code.trim().toUpperCase(),name:form.name.trim(),comment:form.comment.trim() || undefined,modifierType:form.modifierType || undefined
 })

 onCreated(modifier)
 setForm({ code:'',name:'',comment:'',modifierType:'' })
 setSelectedDomain('')
 onClose()
 } catch {
 // Creation failed
 } finally {
 setSaving(false)
 }
 }

 const activeHoveredDomain = hoveredDomain || (isHoveringCard?selectedDomain:null)
 const showItemCard = dropdownOpen && itemCardPos && activeHoveredDomain &&
 valueDomains.find((g) => g.domainCode === activeHoveredDomain)?.items.length

 return (<Modal
 isOpen={isOpen}
 onClose={onClose}
 title={t('componentLibrary.modal.createModifier')}
 size="sm"
 footerRight={
 <>
 <ModalCancelButton onClick={onClose} disabled={saving} />
 <ModalPrimaryButton
 onClick={handleSave}
 disabled={!form.code.trim() ||!form.name.trim()}
 loading={saving}
 >
 {t('componentLibrary.actions.save')}
 </ModalPrimaryButton>
 </>
 }
 >
 <div className="space-y-4">
 {/* Name */}
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.nameRequired')}</label>
 <input
 type="text"
 value={form.name}
 onChange={(e) => setForm({...form,name:e.target.value })}
 placeholder={t('componentLibrary.placeholder.nameYoy')}
 className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>

 {/* encoding + modifier type juxtapose */}
 <div className="grid grid-cols-2 gap-3">
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.codeRequired')}</label>
 <input
 type="text"
 value={form.code}
 onChange={(e) => setForm({...form,code:e.target.value.toUpperCase() })}
 placeholder={t('componentLibrary.placeholder.codeYoy')}
 className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.modifierType')}</label>
 <button
 ref={triggerRef}
 type="button"
 disabled={loadingDomains}
 onClick={() => setDropdownOpen(!dropdownOpen)}
 className={`w-full flex items-center gap-2 px-3 py-2 bg-white dark:bg-slate-900 border rounded-xl transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
 dropdownOpen?'border-blue-400 dark:border-blue-500 ring-2 ring-blue-100 dark:ring-blue-900/50':'border-slate-200 dark:border-slate-700 hover:border-blue-300 dark:hover:border-blue-600'
 }`}
 >
 <Tag size={14} className={getDisplayText()?'text-blue-500':'text-slate-400'} />
 <span className={`flex-1 text-left text-body-sm truncate ${getDisplayText()?'text-slate-800 dark:text-slate-100 font-medium':'text-slate-400'}`}>
 {getDisplayText() || t('componentLibrary.placeholder.selectType')}
 </span>
 {getDisplayText() && (<button
 onClick={handleClear}
 className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-md transition-colors"
 >
 <X size={12} className="text-slate-400 hover:text-slate-600" />
 </button>)}
 <ChevronDown size={12} className={`text-slate-400 transition-transform duration-200 ${dropdownOpen?'rotate-180':''}`} />
 </button>
 </div>
 </div>

 {/* Description */}
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.description')}</label>
 <input
 type="text"
 value={form.comment}
 onChange={(e) => setForm({...form,comment:e.target.value })}
 placeholder={t('componentLibrary.placeholder.descriptionOptional')}
 className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 </div>

 {/* Value range drop-down panel - Portal */}
 {dropdownOpen && dropdownPos && createPortal(<div
 ref={dropdownRef}
 style={{ top:dropdownPos.top,left:dropdownPos.left }}
 className="fixed z-[1000000] min-w-48 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl animate-in fade-in-0 zoom-in-95 duration-150"
 >
 <div
 className="max-h-72 overflow-y-auto overscroll-contain p-1.5"
 style={{ scrollbarWidth:'thin' }}
 onWheel={(e) => e.stopPropagation()}
 >
 {valueDomains.length === 0?(<div className="px-4 py-6 text-center">
 <Tag size={24} className="mx-auto mb-2 text-slate-300 dark:text-slate-600" />
 <p className="text-body-sm text-slate-500">{t('componentLibrary.dropdown.noValueDomain')}</p>
 </div>):(valueDomains.map((domain) => {
 const isSelected = selectedDomain === domain.domainCode
 const isHovered = hoveredDomain === domain.domainCode
 return (<div
 key={domain.domainCode}
 ref={isHovered?hoverItemRef:null}
 onMouseEnter={() => handleItemMouseEnter(domain.domainCode)}
 onMouseLeave={handleItemMouseLeave}
 className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${
 isHovered?'bg-blue-50 dark:bg-blue-900/30':isSelected?'bg-slate-50 dark:bg-slate-800':'hover:bg-slate-50 dark:hover:bg-slate-800'
 }`}
 >
 <Tag size={iconSizeToken.small} className={isHovered?'text-blue-500':'text-slate-400'} />
 <span className={`flex-1 text-body-sm font-medium ${
 isHovered?'text-blue-600 dark:text-blue-400':'text-slate-700 dark:text-slate-300'
 }`}>
 {domain.domainName}
 </span>
 <div className="flex items-center gap-1">
 <span className="text-micro text-slate-400">{domain.items.length}</span>
 <ChevronRight size={iconSizeToken.tiny} className={isHovered?'text-blue-400':'text-slate-400'} />
 </div>
 </div>)
 }))}
 </div>
 </div>,document.body)}

 {/* Enumeration card - Portal */}
 {showItemCard && createPortal(<div
 ref={itemCardRef}
 style={{ top:itemCardPos.top,left:itemCardPos.left }}
 className={`fixed z-[1000001] ${menuWidthClassMap.xlarge} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl animate-in fade-in-0 slide-in-from-left-2 duration-150`}
 onMouseEnter={handleCardMouseEnter}
 onMouseLeave={handleCardMouseLeave}
 >
 <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800">
 <p className="text-micro font-medium text-slate-500 dark:text-slate-400">{t('componentLibrary.dropdown.selectType')}</p>
 </div>
 <div
 className="max-h-52 overflow-y-auto overscroll-contain p-1.5"
 style={{ scrollbarWidth:'thin' }}
 onWheel={(e) => e.stopPropagation()}
 >
 {(activeHoveredDomain?valueDomains.find((g) => g.domainCode === activeHoveredDomain)?.items || []:[]).map((item) => {
 const isSelected = selectedDomain === activeHoveredDomain && form.modifierType === item.value
 const displayText = item.label?`${item.value} (${item.label})`:item.value
 return (<button
 key={item.value}
 type="button"
 onClick={() => handleSelectItem(activeHoveredDomain!, item.value)}
 className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-xl text-left transition-all ${
 isSelected?'bg-blue-50 dark:bg-blue-900/30':'hover:bg-slate-50 dark:hover:bg-slate-800'
 }`}
 >
 <span className={`flex-1 text-body-sm font-medium ${
 isSelected?'text-blue-600 dark:text-blue-400':'text-slate-700 dark:text-slate-300'
 }`}>
 {displayText}
 </span>
 {isSelected && <Check size={14} className="text-blue-500" />}
 </button>)
 })}
 </div>
 </div>,document.body)}
 </Modal>)
}

/** Edit modifier modal */
function EditModifierModal({
 isOpen,modifier,onClose,onUpdated
}:{
 isOpen:boolean
 modifier:MetricModifierDTO | null
 onClose:() => void
 onUpdated:(updated:MetricModifierDTO) => void
}) {
 const { t } = useTranslation('oneSemantics')
 const [form,setForm] = useState({ name:'',comment:'' })
 const [saving,setSaving] = useState(false)

 useEffect(() => {
 if (isOpen && modifier) {
 setForm({
 name:modifier.name,comment:modifier.comment || ''
 })
 }
 },[isOpen,modifier])

 const handleSave = async () => {
 if (!modifier || saving ||!form.name.trim()) return
 setSaving(true)
 try {
 const updated = await updateModifier(modifier.code,{
 name:form.name.trim(),comment:form.comment.trim() || undefined
 })
 onUpdated(updated)
 onClose()
 } catch {
 // Update failed
 } finally {
 setSaving(false)
 }
 }

 if (!modifier) return null

 return (<Modal
 isOpen={isOpen}
 onClose={onClose}
 title={t('componentLibrary.modal.editModifier')}
 size="sm"
 footerRight={
 <>
 <ModalCancelButton onClick={onClose} disabled={saving} />
 <ModalPrimaryButton
 onClick={handleSave}
 disabled={!form.name.trim()}
 loading={saving}
 >
 {t('componentLibrary.actions.save')}
 </ModalPrimaryButton>
 </>
 }
 >
 <div className="space-y-4">
 {/* Name */}
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.nameRequired')}</label>
 <input
 type="text"
 value={form.name}
 onChange={(e) => setForm({...form,name:e.target.value })}
 placeholder={t('componentLibrary.placeholder.nameYoy')}
 className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>

 {/* encoding + modifier type juxtapose */}
 <div className="grid grid-cols-2 gap-3">
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.code')}</label>
 <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400 font-mono">
 {modifier.code}
 </div>
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.modifierType')}</label>
 <div className="px-3 py-2 text-body-sm bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400">
 {modifier.modifierType || '-'}
 </div>
 </div>
 </div>

 {/* Description */}
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.description')}</label>
 <input
 type="text"
 value={form.comment}
 onChange={(e) => setForm({...form,comment:e.target.value })}
 placeholder={t('componentLibrary.placeholder.descriptionOptional')}
 className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 </div>
 </Modal>)
}

/** Edit unit modal box */
function EditUnitModal({
 isOpen,unit,onClose,onUpdated
}:{
 isOpen:boolean
 unit:UnitDTO | null
 onClose:() => void
 onUpdated:(updated:UnitDTO) => void
}) {
 const { t } = useTranslation('oneSemantics')
 const [form,setForm] = useState({ name:'',symbol:'',comment:'' })
 const [saving,setSaving] = useState(false)
 const [nameDropdownOpen,setNameDropdownOpen] = useState(false)
 const nameTriggerRef = useRef<HTMLInputElement>(null)
 const nameDropdownRef = useRef<HTMLDivElement>(null)
 const [nameDropdownPos,setNameDropdownPos] = useState<{ top:number;left:number;width:number } | null>(null)
 const [nameFilterActive,setNameFilterActive] = useState(false)
 const symbolGroups = useMemo(() => buildSymbolGroups(t),[t])
 const nameFilterQuery = nameFilterActive?form.name:''
 const filteredNameGroups = filterSymbolGroups(nameFilterQuery,symbolGroups)
 const symbolMeta = findSymbolMeta(form.symbol,symbolGroups)
 const hasSymbol =!!form.symbol.trim()

 useEffect(() => {
 if (isOpen && unit) {
 setForm({
 name:unit.name,symbol:unit.symbol || '',comment:unit.comment || ''
 })
 setNameFilterActive(false)
 }
 },[isOpen,unit])

 // Click outside the close symbol dropdown
 useEffect(() => {
 if (!nameDropdownOpen) return
 const handleClickOutside = (e:MouseEvent) => {
 const target = e.target as Node
 if (nameTriggerRef.current?.contains(target)) return
 if (nameDropdownRef.current?.contains(target)) return
 setNameDropdownOpen(false)
 setNameFilterActive(false)
 }
 document.addEventListener('mousedown',handleClickOutside)
 return () => document.removeEventListener('mousedown',handleClickOutside)
 },[nameDropdownOpen])

 // Calculate drop down position
 useLayoutEffect(() => {
 if (!nameDropdownOpen) return
 const updatePosition = () => {
 const btn = nameTriggerRef.current
 if (!btn) return
 const rect = btn.getBoundingClientRect()
 const dropdownHeight = 340
 const spaceBelow = window.innerHeight - rect.bottom - 20
 const spaceAbove = rect.top - 20

 if (spaceBelow < dropdownHeight && spaceAbove > spaceBelow) {
 setNameDropdownPos({
 top:rect.top - Math.min(dropdownHeight,spaceAbove) - 4,left:rect.left,width:rect.width
 })
 } else {
 setNameDropdownPos({ top:rect.bottom + 4,left:rect.left,width:rect.width })
 }
 }
 updatePosition()
 window.addEventListener('resize',updatePosition)
 window.addEventListener('scroll',updatePosition,true)
 return () => {
 window.removeEventListener('resize',updatePosition)
 window.removeEventListener('scroll',updatePosition,true)
 }
 },[nameDropdownOpen])

 const handleSave = async () => {
 if (!unit || saving ||!form.name.trim()) return
 setSaving(true)
 try {
 const updated = await updateUnit(unit.code,{
 name:form.name.trim(),symbol:form.symbol.trim() || undefined,comment:form.comment.trim() || undefined
 })
 onUpdated(updated)
 onClose()
 } catch {
 // Update failed
 } finally {
 setSaving(false)
 }
 }

 const handleNameChange = (name:string) => {
 const matchedSymbol = findSymbolByName(name,symbolGroups)
 setNameFilterActive(true)
 setNameDropdownOpen(true)
 setForm((prev) => ({...prev,name,symbol:matchedSymbol?? prev.symbol
 }))
 }

 const handleSelectName = (symbol:string,name?: string) => {
 const nextName = name?? symbol
 setForm((prev) => ({...prev,name:nextName,symbol
 }))
 setNameDropdownOpen(false)
 setNameFilterActive(false)
 }

 if (!unit) return null

 return (<Modal
 isOpen={isOpen}
 onClose={onClose}
 title={t('componentLibrary.modal.editUnit')}
 size="mini"
 footerRight={
 <>
 <ModalCancelButton onClick={onClose} disabled={saving} />
 <ModalPrimaryButton
 onClick={handleSave}
 disabled={!form.name.trim()}
 loading={saving}
 variant="amber"
 >
 {t('componentLibrary.actions.save')}
 </ModalPrimaryButton>
 </>
 }
 >
 <div className="space-y-4">
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.code')}</label>
 <div className="px-4 py-2.5 text-body-sm bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-500 dark:text-slate-400 font-mono">
 {unit.code}
 </div>
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.nameRequired')}</label>
 <div className="relative">
 {hasSymbol && (<span
 className={`absolute left-3 top-1/2 -translate-y-1/2 size-6 flex items-center justify-center rounded text-body-sm font-bold ${
 symbolMeta?symbolMeta.color:'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
 }`}
 >
 {form.symbol}
 </span>)}
 <input
 ref={nameTriggerRef}
 type="text"
 value={form.name}
 onChange={(e) => handleNameChange(e.target.value)}
 onFocus={() => {
 setNameDropdownOpen(true)
 setNameFilterActive(false)
 }}
 placeholder={t('componentLibrary.placeholder.nameRmb')}
 className={`w-full ${hasSymbol?'pl-12 pr-10':'px-4 pr-10'} py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600`}
 />
 <button
 type="button"
 onClick={() => {
 setNameFilterActive(false)
 setNameDropdownOpen((open) =>!open)
 }}
 className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-500 transition-colors"
 aria-label={t('componentLibrary.aria.openUnitNameList')}
 >
 <ChevronDown size={14} className={`transition-transform ${nameDropdownOpen?'rotate-180':''}`} />
 </button>
 </div>
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.symbol')}</label>
 <input
 type="text"
 value={form.symbol}
 onChange={(e) => setForm({...form,symbol:e.target.value })}
 placeholder={t('componentLibrary.placeholder.symbolOptional')}
 className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 <div>
 <label className="block text-body-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('componentLibrary.field.description')}</label>
 <input
 type="text"
 value={form.comment}
 onChange={(e) => setForm({...form,comment:e.target.value })}
 placeholder={t('componentLibrary.placeholder.descriptionOptional')}
 className="w-full px-4 py-2.5 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-slate-400 dark:placeholder:text-slate-600"
 />
 </div>
 </div>

 {/* Name selection drop down */}
 {nameDropdownOpen && nameDropdownPos && createPortal(<div
 ref={nameDropdownRef}
 style={{
 '--symbol-dropdown-top':`${nameDropdownPos.top}px`,'--symbol-dropdown-left':`${nameDropdownPos.left}px`,'--symbol-dropdown-width':`${nameDropdownPos.width}px`
 } as React.CSSProperties}
 className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-2xl shadow-2xl overflow-hidden top-[var(--symbol-dropdown-top)] left-[var(--symbol-dropdown-left)] w-[var(--symbol-dropdown-width)]"
 >
 {/* grouped list */}
 <div className="max-h-64 overflow-y-auto p-2 custom-scrollbar">
 {filteredNameGroups.length === 0?(<div className="py-6 text-center text-caption text-slate-400">{t('componentLibrary.dropdown.noMatchingName')}</div>):(filteredNameGroups.map((group) => (<div key={group.name} className="mb-2 last:mb-0">
 <div className="px-2 py-1 text-micro font-semibold text-slate-400 uppercase">{group.name}</div>
 <div className="grid grid-cols-3 gap-1">
 {group.items.map((item) => (<button
 key={item.symbol}
 type="button"
 onClick={() => handleSelectName(item.symbol,item.label)}
 className={`flex items-center gap-2 px-2 py-1.5 rounded-lg transition-all ${
 form.name === item.label?'bg-amber-100 dark:bg-amber-900/50 ring-1 ring-amber-400':'hover:bg-slate-50 dark:hover:bg-slate-800'
 }`}
 >
 <span className={`w-5 h-5 flex items-center justify-center rounded text-xs font-bold ${group.color}`}>
 {item.symbol}
 </span>
 <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.label}</span>
 </button>))}
 </div>
 </div>)))}
 </div>
 </div>,document.body)}
 </Modal>)
}

export function ComponentLibrarySidebar() {
 const { t } = useTranslation('oneSemantics')
 const [collapsed,setCollapsed] = useState(true)
 const [activeTab,setActiveTab] = useState<TabType>('UNIT')

 // Data status
 const [units,setUnits] = useState<UnitDTO[]>([])
 const [modifiers,setModifiers] = useState<MetricModifierDTO[]>([])
 const [loading,setLoading] = useState(false)

 // Modal state
 const [showUnitModal,setShowUnitModal] = useState(false)
 const [showModifierModal,setShowModifierModal] = useState(false)
 const [editingModifier,setEditingModifier] = useState<MetricModifierDTO | null>(null)
 const [editingUnit,setEditingUnit] = useState<UnitDTO | null>(null)

 // delete status
 const [deletingCode,setDeletingCode] = useState<string | null>(null)

 // Load data
 const loadData = useCallback(async () => {
 setLoading(true)
 try {
 const [unitsResult,modifiersResult] = await Promise.all([fetchUnits(0,100),fetchModifiers(0,100)])
 setUnits(unitsResult.items)
 setModifiers(modifiersResult.items)
 } catch {
 // Loading failed
 } finally {
 setLoading(false)
 }
 },[])

 useEffect(() => {
 if (!collapsed) {
 loadData()
 }
 },[collapsed,loadData])

 // Delete unit
 const handleDeleteUnit = async (code:string) => {
 if (deletingCode) return
 setDeletingCode(code)
 try {
 await deleteUnit(code)
 setUnits((prev) => prev.filter((u) => u.code!== code))
 } catch {
 // Delete failed
 } finally {
 setDeletingCode(null)
 }
 }

 // Remove modifier
 const handleDeleteModifier = async (code:string) => {
 if (deletingCode) return
 setDeletingCode(code)
 try {
 await deleteModifier(code)
 setModifiers((prev) => prev.filter((m) => m.code!== code))
 } catch {
 // Delete failed
 } finally {
 setDeletingCode(null)
 }
 }

 // update modifier
 const handleModifierUpdated = (updated:MetricModifierDTO) => {
 setModifiers((prev) => prev.map((m) => (m.code === updated.code?updated:m)))
 }

 // Update unit
 const handleUnitUpdated = (updated:UnitDTO) => {
 setUnits((prev) => prev.map((u) => (u.code === updated.code?updated:u)))
 }

 const items = activeTab === 'UNIT'?units:modifiers
 const listTitle = activeTab === 'UNIT'
 ? t('componentLibrary.list.availableUnits')
 : t('componentLibrary.list.availableModifiers')

 // folded state
 if (collapsed) {
 return (<div className="w-12 border-l border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 flex flex-col items-center py-4">
 <div className="flex-1 flex flex-col items-center gap-3">
 <Tooltip content={t('componentLibrary.tab.modifier')} side="left" className="w-full flex justify-center">
 <button
 onClick={() => {
 setActiveTab('MODIFIER')
 setCollapsed(false)
 }}
 className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-blue-500 hover:text-blue-600 transition-colors"
 aria-label={t('componentLibrary.tab.modifier')}
 >
 <Sparkles size={iconSizeToken.medium} />
 </button>
 </Tooltip>
 <Tooltip content={t('componentLibrary.tab.unitLibrary')} side="left" className="w-full flex justify-center">
 <button
 onClick={() => {
 setActiveTab('UNIT')
 setCollapsed(false)
 }}
 className="p-2 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded-lg text-amber-500 hover:text-amber-600 transition-colors"
 aria-label={t('componentLibrary.tab.unitLibrary')}
 >
 <Scale size={iconSizeToken.medium} />
 </button>
 </Tooltip>
 </div>
 <Tooltip content={t('componentLibrary.actions.expand')} side="left" className="w-full flex justify-center">
 <button
 onClick={() => setCollapsed(false)}
 className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
 aria-label={t('componentLibrary.actions.expand')}
 >
 <ChevronLeft size={iconSizeToken.medium} />
 </button>
 </Tooltip>
 </div>)
 }

 return (<div className={`${panelWidthClassMap.medium} border-l border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 flex flex-col overflow-hidden`}>
 {/* Title */}
 <div className="px-3 pt-3 pb-1.5">
 <h3 className="text-caption font-semibold text-slate-800 dark:text-slate-200">
 {t('componentLibrary.title.main')} <span className="text-slate-400 font-normal text-micro">({t('componentLibrary.title.suffix')})</span>
 </h3>
 </div>

 {/* Tab switch */}
 <div className="px-3 py-1.5">
 <div className="flex bg-slate-100 dark:bg-slate-900/60 p-0.5 rounded-full">
 <button
 onClick={() => setActiveTab('MODIFIER')}
 className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
 activeTab === 'MODIFIER'?'bg-white dark:bg-slate-900 text-blue-500 shadow-sm':'text-slate-500 hover:text-slate-700'
 }`}
 >
 <Sparkles size={12} />
 {t('componentLibrary.tab.modifier')}
 </button>
 <button
 onClick={() => setActiveTab('UNIT')}
 className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
 activeTab === 'UNIT'?'bg-amber-500 text-white shadow-sm':'text-slate-500 hover:text-slate-700'
 }`}
 >
 <Scale size={12} />
 {t('componentLibrary.tab.unitLibrary')}
 </button>
 </div>
 </div>

 {/* list title */}
 <div className="px-3 py-1.5 flex items-center justify-between">
 <span className="text-micro text-slate-500">
 {listTitle} ({items.length})
 </span>
 <button
 onClick={() => (activeTab === 'UNIT'?setShowUnitModal(true):setShowModifierModal(true))}
 className="p-0.5 text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-colors"
 >
 <Plus size={14} />
 </button>
 </div>

 {/* list */}
 <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1.5 custom-scrollbar">
 {loading?(<div className="flex items-center justify-center py-8">
 <Loader2 className="w-5 h-5 animate-spin text-slate-400" />
 </div>):items.length === 0?(<div className="text-center py-8 text-micro text-slate-400">{t('componentLibrary.list.noData')}</div>):activeTab === 'UNIT'?(units.map((item) => (<div
 key={item.code}
 className="group flex items-center gap-2.5 p-2.5 bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-700 rounded-lg hover:border-amber-200 dark:hover:border-amber-700 hover:shadow-sm transition-all"
 >
 <div className="w-8 h-8 rounded-full bg-amber-50 dark:bg-amber-900/30 flex items-center justify-center text-amber-600 dark:text-amber-400 font-bold text-caption flex-shrink-0">
 {item.symbol || item.code[0]}
 </div>
 <div className="min-w-0 flex-1">
 <div className="text-caption font-medium text-slate-800 dark:text-slate-200 truncate">{item.name}</div>
 <div className="text-micro text-amber-600 dark:text-amber-400 font-medium truncate">{item.code}</div>
 </div>
 <div className="flex items-center gap-0.5">
 <button
 onClick={() => setEditingUnit(item)}
 className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-amber-500 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded transition-all"
 title={t('componentLibrary.actions.edit')}
 >
 <Pencil size={12} />
 </button>
 <button
 onClick={() => handleDeleteUnit(item.code)}
 disabled={deletingCode === item.code}
 className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded transition-all disabled:opacity-50"
 title={t('componentLibrary.actions.delete')}
 >
 {deletingCode === item.code?(<Loader2 size={12} className="animate-spin" />):(<Trash2 size={12} />)}
 </button>
 </div>
 </div>))):(modifiers.map((item) => (<div
 key={item.code}
 className="group flex items-center gap-2.5 p-2.5 bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-700 rounded-lg hover:border-blue-200 dark:hover:border-blue-700 hover:shadow-sm transition-all"
 >
 <div className="w-8 h-8 rounded-full bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center text-blue-600 dark:text-blue-400 font-bold text-caption flex-shrink-0">
 {item.name[0]}
 </div>
 <div className="min-w-0 flex-1">
 <div className="text-caption font-medium text-slate-800 dark:text-slate-200 truncate">{item.name}</div>
 <div className="text-micro text-blue-600 dark:text-blue-400 font-medium truncate">{item.code}</div>
 </div>
 <div className="flex items-center gap-0.5">
 <button
 onClick={() => setEditingModifier(item)}
 className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-all"
 title={t('componentLibrary.actions.edit')}
 >
 <Pencil size={12} />
 </button>
 <button
 onClick={() => handleDeleteModifier(item.code)}
 disabled={deletingCode === item.code}
 className="opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded transition-all disabled:opacity-50"
 title={t('componentLibrary.actions.delete')}
 >
 {deletingCode === item.code?(<Loader2 size={12} className="animate-spin" />):(<Trash2 size={12} />)}
 </button>
 </div>
 </div>)))}
 </div>

 {/* bottom area */}
 <div className="px-3 py-2 border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/60 flex items-center justify-between gap-2">
 <p className="text-micro text-slate-400 leading-relaxed flex-1">{t('componentLibrary.footer.tip')}</p>
 <button
 onClick={() => setCollapsed(true)}
 className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-lg text-slate-400 hover:text-slate-600 transition-colors flex-shrink-0"
 title={t('componentLibrary.actions.fold')}
 >
 <ChevronRight size={iconSizeToken.medium} />
 </button>
 </div>

 {/* Create unit modal box */}
 <CreateUnitModal
 isOpen={showUnitModal}
 onClose={() => setShowUnitModal(false)}
 onCreated={(unit) => setUnits((prev) => [...prev,unit])}
 />

 {/* Edit unit modal box */}
 <EditUnitModal
 isOpen={editingUnit!== null}
 unit={editingUnit}
 onClose={() => setEditingUnit(null)}
 onUpdated={handleUnitUpdated}
 />

 {/* Create a modifier modal */}
 <CreateModifierModal
 isOpen={showModifierModal}
 onClose={() => setShowModifierModal(false)}
 onCreated={(modifier) => setModifiers((prev) => [...prev,modifier])}
 />

 {/* Edit modifier modal */}
 <EditModifierModal
 isOpen={editingModifier!== null}
 modifier={editingModifier}
 onClose={() => setEditingModifier(null)}
 onUpdated={handleModifierUpdated}
 />
 </div>)
}
