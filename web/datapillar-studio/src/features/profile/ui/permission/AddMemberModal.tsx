import { useMemo,useState } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
 ArrowRight,Building2,Check,CheckCircle2,ChevronLeft,Globe,Key,LayoutGrid,Loader2,Lock,RefreshCw,Search,UserPlus,X
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { ThirdPartyIcons } from '@/components'
import { Button,Modal } from '@/components/ui'
import { tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { RoleDefinition,UserItem } from '../../utils/permissionTypes'
import { PermissionAvatar } from './PermissionAvatar'

interface AddMemberModalProps {
 isOpen:boolean
 onClose:() => void
 role:RoleDefinition
 users:UserItem[]
 onAddUser:(userId:string) => void
}

type ConfigProvider = 'DingTalk' | 'Lark'

const ORG_FILTER_MAP:Record<string,string> = {
 product:'Product Design Center',risk:'Compliance and Risk Control Department',biz:'Commercialization team',rd:'Platform R&D Department',sre:'Operation and Maintenance Department'
}

const ROLE_DEPARTMENT_MAP:Record<string,string> = {
 role_owner:'Product Design Center',role_lead:'Platform R&D Department',role_developer:'Platform R&D Department',role_auditor:'Compliance and Risk Control Department',role_sre:'Operation and Maintenance Department'
}

export function AddMemberModal({ isOpen,onClose,role,users,onAddUser }:AddMemberModalProps) {
 const { t } = useTranslation('permission')
 const [activeTab,setActiveTab] = useState<'platform' | 'import'>('platform')
 const [configProvider,setConfigProvider] = useState<ConfigProvider | null>(null)
 const [isSyncing,setIsSyncing] = useState(false)
 const [activeOrgId,setActiveOrgId] = useState('all')
 const [searchQuery,setSearchQuery] = useState('')
 const [selectedUserIds,setSelectedUserIds] = useState<string[]>([])

 const availableUsers = useMemo(() => users.filter((user) => user.roleId!== role.id),[users,role.id])
 const departmentCounts = useMemo(() => {
 return availableUsers.reduce<Record<string,number>>((acc,user) => {
 const department = ROLE_DEPARTMENT_MAP[user.roleId]?? 'Others'
 acc[department] = (acc[department]?? 0) + 1
 return acc
 },{})
 },[availableUsers])
 const orgUnits = useMemo<Array<{ id:string;name:string;count:number;icon:LucideIcon }>>(() => [{ id:'all',name:t('addMember.orgUnits.all'),count:availableUsers.length,icon:LayoutGrid },{ id:'product',name:t('addMember.orgUnits.product'),count:departmentCounts['Product Design Center']?? 0,icon:Building2 },{ id:'risk',name:t('addMember.orgUnits.risk'),count:departmentCounts['Compliance and Risk Control Department']?? 0,icon:Building2 },{ id:'biz',name:t('addMember.orgUnits.biz'),count:departmentCounts['Commercialization team']?? 0,icon:Building2 },{ id:'rd',name:t('addMember.orgUnits.rd'),count:departmentCounts['Platform R&D Department']?? 0,icon:Building2 },{ id:'sre',name:t('addMember.orgUnits.sre'),count:departmentCounts['Operation and Maintenance Department']?? 0,icon:Building2 }],[availableUsers.length,departmentCounts,t])
 const [allUnit,...departmentUnits] = orgUnits
 const filteredUsers = useMemo(() => {
 const scopedUsers =
 activeOrgId === 'all'?availableUsers:availableUsers.filter((user) => ROLE_DEPARTMENT_MAP[user.roleId] === ORG_FILTER_MAP[activeOrgId])
 const keyword = searchQuery.trim().toLowerCase()
 if (!keyword) return scopedUsers
 return scopedUsers.filter((user) => {
 return user.name.toLowerCase().includes(keyword) || user.email.toLowerCase().includes(keyword)
 })
 },[activeOrgId,availableUsers,searchQuery])
 const selectedCount = selectedUserIds.length
 const isAllSelected = filteredUsers.length > 0 && filteredUsers.every((user) => selectedUserIds.includes(user.id))

 if (!isOpen) return null

 const toggleUserSelection = (userId:string) => {
 setSelectedUserIds((prev) => (prev.includes(userId)?prev.filter((id) => id!== userId):[...prev,userId]))
 }

 const toggleSelectAll = () => {
 if (isAllSelected) {
 const filteredIds = new Set(filteredUsers.map((user) => user.id))
 setSelectedUserIds((prev) => prev.filter((id) =>!filteredIds.has(id)))
 return
 }
 const filteredIds = new Set(filteredUsers.map((user) => user.id))
 setSelectedUserIds((prev) => Array.from(new Set([...prev,...filteredIds])))
 }

 const handleConfirmAdd = () => {
 if (selectedUserIds.length === 0) return
 selectedUserIds.forEach((userId) => onAddUser(userId))
 setSelectedUserIds([])
 onClose()
 }

 const handleSync = () => {
 setIsSyncing(true)
 window.setTimeout(() => {
 setIsSyncing(false)
 },1500)
 }

 const renderConfigForm = () => (<div className="p-5">
 <button
 type="button"
 className="flex items-center gap-2 mb-4 text-slate-500 dark:text-slate-400 cursor-pointer hover:text-slate-900 dark:hover:text-slate-200 transition-colors"
 onClick={() => setConfigProvider(null)}
 >
 <ChevronLeft size={16} />
 <span className={cn(TYPOGRAPHY.bodySm,'font-medium')}>{t('addMember.config.returnToSourceList')}</span>
 </button>

 <div className="flex items-center gap-4 mb-6">
 <div className="w-16 h-16 bg-slate-50 dark:bg-slate-800 rounded-2xl flex items-center justify-center border border-slate-100 dark:border-slate-700 shadow-sm">
 {configProvider === 'DingTalk'?<ThirdPartyIcons.DingTalk />:<ThirdPartyIcons.Lark />}
 </div>
 <div>
 <h3 className={cn(TYPOGRAPHY.subtitle,'font-bold text-slate-900 dark:text-white')}>
 {t('addMember.config.title', { provider: configProvider === 'DingTalk' ? t('addMember.provider.dingtalk') : t('addMember.provider.feishu') })}
 </h3>
 <p className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400 mt-1')}>
 {t('addMember.config.subtitle', { provider: configProvider === 'DingTalk' ? t('addMember.provider.dingtalk') : t('addMember.provider.feishu') })}</p>
 </div>
 </div>

 <div className="space-y-4">
 <div>
 <label className={cn(TYPOGRAPHY.caption,'block font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5')}>
 {configProvider === 'DingTalk'?t('addMember.config.agentId'):t('addMember.config.appId')} <span className="text-red-500">*</span>
 </label>
 <div className="relative">
 <Globe className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" size={16} />
 <input
 type="text"
 className={cn(TYPOGRAPHY.bodySm,'w-full pl-10 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-all text-slate-900 dark:text-slate-100')}
 placeholder={configProvider === 'DingTalk'?t('addMember.config.agentIdPlaceholder'):t('addMember.config.appIdPlaceholder')}
 />
 </div>
 </div>

 <div>
 <label className={cn(TYPOGRAPHY.caption,'block font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5')}>
 {t('addMember.config.appKey')} <span className="text-red-500">*</span>
 </label>
 <div className="relative">
 <Key className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" size={16} />
 <input
 type="text"
 className={cn(TYPOGRAPHY.bodySm,'w-full pl-10 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-all text-slate-900 dark:text-slate-100')}
 placeholder={t('addMember.config.appKeyPlaceholder')}
 />
 </div>
 </div>

 <div>
 <label className={cn(TYPOGRAPHY.caption,'block font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider mb-1.5')}>
 {t('addMember.config.appSecret')} <span className="text-red-500">*</span>
 </label>
 <div className="relative">
 <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" size={16} />
 <input
 type="password"
 className={cn(TYPOGRAPHY.bodySm,'w-full pl-10 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-all text-slate-900 dark:text-slate-100')}
 placeholder={t('addMember.config.appSecretPlaceholder')}
 />
 </div>
 </div>

 <div className="pt-3 flex gap-3">
 <Button className="w-full" size="normal" onClick={() => setConfigProvider(null)}>
 {t('addMember.config.saveAndConnect')}
 </Button>
 </div>

 <div className="flex justify-center mt-4">
 <a href="#" className={cn(TYPOGRAPHY.caption,'text-brand-600 dark:text-brand-300 hover:underline')}>
 {t('addMember.config.howToGetInfo')}</a>
 </div>
 </div>
 </div>)

 return (<Modal
 isOpen={isOpen}
 onClose={onClose}
 size={configProvider?'sm':'lg'}
 title={t('addMember.title')}
 subtitle={(<span className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400 flex items-center gap-2')}>
 {t('addMember.addTo')}
 <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
 <UserPlus size={12} />
 {role.name}
 </span>
 </span>)}
 >
 <div
 className={cn('flex flex-col min-h-0 -mx-8 -my-4 overflow-hidden',configProvider?'min-h-0':'h-[520px]')}
 >
 {!configProvider && (<div className="bg-white dark:bg-slate-900 border-b border-slate-100 dark:border-slate-800 px-6 pt-4 pb-0 shrink-0">
 <div className="flex gap-6">
 <button
 type="button"
 onClick={() => setActiveTab('platform')}
 className={cn(TYPOGRAPHY.bodySm,`pb-3 font-medium border-b-2 transition-all ${
 activeTab === 'platform'?'border-brand-600 text-brand-600':'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
 }`)}
 >
 {t('addMember.tabs.orgStructure')}
 </button>
 <button
 type="button"
 onClick={() => setActiveTab('import')}
 className={cn(TYPOGRAPHY.bodySm,`pb-3 font-medium border-b-2 transition-all flex items-center gap-1.5 ${
 activeTab === 'import'?'border-brand-600 text-brand-600':'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
 }`)}
 >
 {t('addMember.tabs.thirdPartySync')}
 <span className={cn(TYPOGRAPHY.micro,'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-300 px-1.5 py-0.5 rounded-full font-bold')}>
 {t('addMember.tabs.auto')}
 </span>
 </button>
 </div>
 </div>)}

 <div className="flex-1 min-h-0 bg-slate-50 dark:bg-slate-950/35 overflow-hidden">
 {configProvider?(<div className="flex-1 min-h-0 overflow-y-auto">{renderConfigForm()}</div>):(<>
 {activeTab === 'platform' && (<div className="flex w-full h-full min-h-0 bg-white dark:bg-slate-900/90">
 <div className="w-60 bg-slate-50/50 dark:bg-slate-900/70 border-r border-slate-100 dark:border-slate-800 flex flex-col shrink-0 min-h-0">
 <div className="px-4 pt-6 pb-3 border-b border-slate-200/60 dark:border-slate-800/80">
 <h4 className={cn(TYPOGRAPHY.bodyXs,'font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider px-2 mb-2')}>{t('addMember.departmentFilter')}</h4>
 </div>
 <div className="flex-1 overflow-y-auto px-3 pt-2 pb-3 space-y-0.5 custom-scrollbar min-h-0">
 <button
 type="button"
 onClick={() => setActiveOrgId(allUnit.id)}
 className={cn(TYPOGRAPHY.bodyXs,`w-full flex items-center justify-between px-3 py-2 rounded-lg font-medium transition-all ${
 activeOrgId === allUnit.id?'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-300 shadow-sm ring-1 ring-slate-200 dark:ring-slate-700':'text-slate-600 dark:text-slate-300 hover:bg-slate-100/50 dark:hover:bg-slate-800/60 hover:text-slate-900 dark:hover:text-slate-100'
 }`)}
 >
 <div className="flex items-center gap-2.5">
 <div
 className={`p-1 rounded ${
 activeOrgId === allUnit.id?'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300':'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'
 }`}
 >
 <allUnit.icon size={14} />
 </div>
 <span>{allUnit.name}</span>
 </div>
 <span
 className={cn(TYPOGRAPHY.micro,`px-1.5 py-0.5 rounded-md font-bold ${
 activeOrgId === allUnit.id?'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300':'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
 }`)}
 >
 {allUnit.count}
 </span>
 </button>

 <div className="my-2 border-t border-slate-200/50 dark:border-slate-800 mx-2"></div>

 {departmentUnits.map((unit) => {
 const isActive = activeOrgId === unit.id
 const Icon = unit.icon
 return (<button
 key={unit.id}
 type="button"
 onClick={() => setActiveOrgId(unit.id)}
 className={cn(TYPOGRAPHY.bodyXs,`w-full flex items-center justify-between px-3 py-2 rounded-lg font-medium transition-all ${
 isActive?'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-300 shadow-sm ring-1 ring-slate-200 dark:ring-slate-700':'text-slate-600 dark:text-slate-300 hover:bg-slate-100/50 dark:hover:bg-slate-800/60 hover:text-slate-900 dark:hover:text-slate-100'
 }`)}
 >
 <div className="flex items-center gap-2.5 truncate">
 <div
 className={`p-1 rounded ${
 isActive?'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300':'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'
 }`}
 >
 <Icon size={14} />
 </div>
 <span className={`truncate ${tableColumnWidthClassMap.xl}`} title={unit.name}>
 {unit.name}
 </span>
 </div>
 <span
 className={cn(TYPOGRAPHY.micro,`px-1.5 py-0.5 rounded-md font-bold ${
 isActive?'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300':'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
 }`)}
 >
 {unit.count}
 </span>
 </button>)
 })}
 </div>
 </div>

 <div className="flex-1 flex flex-col min-w-0 bg-white dark:bg-slate-900/90 relative min-h-0">
 <div className="border-b border-slate-100 dark:border-slate-800/80 sticky top-0 bg-white/95 dark:bg-slate-900/90 backdrop-blur z-[5]">
 <div className="px-1.5 pt-5 pb-4">
 <div className="relative group">
 <Search
 className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 group-focus-within:text-brand-500 dark:group-focus-within:text-brand-400 transition-colors"
 size={16}
 />
 <input
 type="text"
 value={searchQuery}
 onChange={(event) => setSearchQuery(event.target.value)}
 placeholder={t('addMember.searchPlaceholder')}
 className={cn(TYPOGRAPHY.bodyXs,'w-full pl-10 pr-4 py-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg focus:bg-white dark:focus:bg-slate-900 focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 dark:focus:border-brand-400 outline-none transition-all text-slate-900 dark:text-slate-100')}
 />
 </div>
 </div>
 <div
 className={cn(TYPOGRAPHY.bodyXs,'flex items-center px-1.5 py-2 bg-slate-50/50 dark:bg-slate-800/60 font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider')}
 >
 <div className="w-8 flex justify-center">
 {filteredUsers.length > 0 && (<div
 className={`w-3.5 h-3.5 rounded border flex items-center justify-center cursor-pointer transition-colors ${
 isAllSelected?'bg-brand-600 border-brand-600':'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 hover:border-slate-400 dark:hover:border-slate-500'
 }`}
 onClick={toggleSelectAll}
 >
 {isAllSelected && <Check size={8} className="text-white" />}
 </div>)}
 </div>
 <div className="flex-1 pl-1.5">{t('addMember.columns.nameDepartment')}</div>
 <div className="w-1/3 text-center px-1.5">{t('addMember.columns.email')}</div>
 </div>
 </div>

 <div className="flex-1 overflow-y-auto pb-20 min-h-0">
 {filteredUsers.length === 0?(<div className="h-full flex flex-col items-center justify-center text-slate-400 dark:text-slate-500 pb-20">
 <div className="w-16 h-16 bg-slate-50 dark:bg-slate-800 rounded-full flex items-center justify-center mb-4">
 <Search size={24} className="opacity-40" />
 </div>
 <p className={cn(TYPOGRAPHY.bodyXs,'font-medium text-slate-500 dark:text-slate-400')}>{t('addMember.noMatchingMember')}</p>
 </div>):(<div className="divide-y divide-slate-50 dark:divide-slate-800">
 {filteredUsers.map((user) => {
 const department = ROLE_DEPARTMENT_MAP[user.roleId]?? t('addMember.orgUnits.rd')
 const isSelected = selectedUserIds.includes(user.id)
 return (<div
 key={user.id}
 onClick={() => toggleUserSelection(user.id)}
 className={`group flex items-center px-1.5 py-3 cursor-pointer transition-colors duration-150 ${
 isSelected?'bg-brand-50/60 dark:bg-brand-500/10':'hover:bg-slate-50 dark:hover:bg-slate-800/70'
 }`}
 >
 <div className="w-8 flex justify-center shrink-0">
 <div
 onClick={(event) => {
 event.stopPropagation()
 toggleUserSelection(user.id)
 }}
 className={`w-3.5 h-3.5 rounded border flex items-center justify-center transition-all duration-200 ${
 isSelected?'bg-brand-600 border-brand-600 scale-100':'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 group-hover:border-slate-400 dark:group-hover:border-slate-500'
 }`}
 >
 <Check
 size={8}
 className={`text-white transition-transform ${isSelected?'scale-100':'scale-0'}`}
 />
 </div>
 </div>

 <div className="flex-1 flex items-center gap-3 min-w-0 pl-1.5">
 <PermissionAvatar
 name={user.name}
 src={user.avatarUrl}
 size="sm"
 className={`${isSelected?'ring-2 ring-brand-200 dark:ring-brand-500/40':''} transition-all w-9 h-9`}
 />
 <div className="min-w-0">
 <div className="flex items-center gap-2">
 <span
 className={cn(TYPOGRAPHY.bodyXs,`font-medium truncate ${isSelected?'text-brand-900 dark:text-brand-200':'text-slate-900 dark:text-slate-100'}`)}
 >
 {user.name}
 </span>
 </div>
 {activeOrgId === 'all' && (<div className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400 mt-0.5 flex items-center gap-1.5')}>
 <Building2 size={10} />
 <span className="truncate">{department}</span>
 </div>)}
 </div>
 </div>

 <div
 className={cn(TYPOGRAPHY.caption,'w-1/3 text-center text-slate-400 dark:text-slate-500 truncate font-mono px-1.5 group-hover:text-slate-500 dark:group-hover:text-slate-400 transition-colors')}
 >
 {user.email}
 </div>
 </div>)
 })}
 </div>)}
 </div>

 {selectedCount > 0 && (<div className="absolute bottom-6 left-1/2 -translate-x-1/2 w-[90%] z-30">
 <div className="bg-slate-900 dark:bg-slate-800 text-white px-3 py-1.5 rounded-2xl shadow-2xl shadow-slate-900/20 dark:shadow-slate-900/40 flex items-center justify-between border border-slate-800 dark:border-slate-700 animate-in slide-in-from-bottom-4 duration-300">
 <div className="flex items-center gap-3 px-2 min-w-0 flex-1">
 <div className="flex -space-x-2 overflow-hidden py-0.5 pl-1">
 {selectedUserIds.slice(0,5).map((userId) => {
 const user = availableUsers.find((item) => item.id === userId)
 if (!user) return null
 return (<div
 key={userId}
 className="relative group/avatar cursor-pointer hover:z-10 hover:-translate-y-1 transition-all"
 >
 <PermissionAvatar
 name={user.name}
 src={user.avatarUrl}
 size="sm"
 className="ring-2 ring-slate-900 w-6 h-6"
 />
 <div
 onClick={(event) => {
 event.stopPropagation()
 toggleUserSelection(userId)
 }}
 className="absolute inset-0 bg-black/50 rounded-full opacity-0 group-hover/avatar:opacity-100 flex items-center justify-center backdrop-blur-[1px]"
 >
 <X size={12} className="text-white" />
 </div>
 </div>)
 })}
 {selectedCount > 5 && (<div className={`${TYPOGRAPHY.micro} w-6 h-6 rounded-full bg-slate-800 ring-2 ring-slate-900 flex items-center justify-center font-bold`}>
 +{selectedCount - 5}
 </div>)}
 </div>
 <div className="h-8 w-px bg-slate-700/50 mx-1 hidden @md:block"></div>
 <div className={cn(TYPOGRAPHY.caption,'font-medium whitespace-nowrap')}>{t('addMember.selectedCount', { count: selectedCount })}</div>
 </div>

 <div className="flex items-center gap-3 pl-4">
 <button
 type="button"
 onClick={() => setSelectedUserIds([])}
 className="text-xs text-slate-400 dark:text-slate-300 hover:text-white transition-colors"
 >
 {t('addMember.actions.clear')}
 </button>
 <Button
 size="small"
 variant="primary"
 className="bg-brand-500 hover:bg-brand-400 text-white border-0 shadow-lg shadow-brand-500/25 px-5"
 onClick={handleConfirmAdd}
 >
 {t('addMember.actions.confirmAdd')}
 </Button>
 </div>
 </div>
 </div>)}
 </div>
 </div>)}

 {activeTab === 'import' && (<div className="h-full flex flex-col min-h-0">
 <div className="flex-1 min-h-0 overflow-y-auto">
 <div className="px-8 py-8">
 <div className="text-center mb-8">
 <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-brand-50 dark:bg-brand-500/10 text-brand-600 dark:text-brand-300 mb-4 ring-1 ring-brand-100 dark:ring-brand-500/30 shadow-sm">
 <RefreshCw size={22} />
 </div>
 <h3 className={cn(TYPOGRAPHY.subtitle,'font-bold text-slate-900 dark:text-white tracking-tight')}>{t('addMember.import.title')}</h3>
 <p className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400 mt-2 max-w-sm mx-auto leading-relaxed')}>
 {t('addMember.import.subtitle')}</p>
 </div>

 <div className="space-y-2">
 <div className="group relative bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-2 flex items-center gap-4 transition-all hover:border-indigo-300 dark:hover:border-indigo-400/50 hover:shadow-lg hover:shadow-indigo-500/5 dark:hover:shadow-indigo-500/20">
 <div className="w-14 h-14 rounded-lg bg-indigo-50/30 dark:bg-indigo-500/10 border border-indigo-100/50 dark:border-indigo-500/30 flex items-center justify-center shrink-0">
 <ThirdPartyIcons.WeCom />
 </div>
 <div className="flex-1 min-w-0 py-0.5">
 <div className="flex items-center gap-2 mb-1">
 <h4 className={cn(TYPOGRAPHY.bodyXs,'font-bold text-slate-900 dark:text-slate-100')}>{t('addMember.import.wecom.name')}</h4>
 <div
 className={cn(TYPOGRAPHY.micro,'flex items-center gap-1 bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 px-1.5 py-0.5 rounded-full font-bold border border-emerald-100/50 dark:border-emerald-500/30')}
 >
 <CheckCircle2 size={10} className="fill-emerald-100 stroke-emerald-600 dark:fill-emerald-500/20 dark:stroke-emerald-300" />
 <span>{t('addMember.import.status.connected')}</span>
 </div>
 </div>
 <div className={cn(TYPOGRAPHY.micro,'flex items-center gap-2 text-slate-500 dark:text-slate-400')}>
 <span className="truncate">{t('addMember.import.wecom.syncTime')}</span>
 <span className="w-0.5 h-2.5 bg-slate-200 dark:bg-slate-700 rounded-full" />
 <span>{t('addMember.import.wecom.memberCount')}</span>
 </div>
 </div>
 <div className="pr-2">
 <Button
 size="small"
 variant="outline"
 className={cn(TYPOGRAPHY.bodyXs,'h-8 px-3 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 group-hover:border-indigo-200 dark:group-hover:border-indigo-400/50 group-hover:text-indigo-600 dark:group-hover:text-indigo-300 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-500/10 transition-all')}
 onClick={handleSync}
 >
 {isSyncing?<Loader2 size={12} className="animate-spin" />:<RefreshCw size={12} />}
 {isSyncing?t('addMember.import.actions.syncing'):t('addMember.import.actions.sync')}
 </Button>
 </div>
 </div>

 <div className="group relative bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-2 flex items-center gap-4 transition-all hover:border-blue-300 dark:hover:border-blue-400/50 hover:shadow-lg hover:shadow-blue-500/5 dark:hover:shadow-blue-500/20">
 <div className="w-14 h-14 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 flex items-center justify-center shrink-0 grayscale group-hover:grayscale-0 transition-all duration-300">
 <ThirdPartyIcons.DingTalk />
 </div>
 <div className="flex-1 min-w-0 py-0.5">
 <div className="flex items-center gap-2 mb-1">
 <h4 className={cn(TYPOGRAPHY.bodyXs,'font-bold text-slate-900 dark:text-slate-100 group-hover:text-blue-600 dark:group-hover:text-blue-300 transition-colors')}>
 {t('addMember.provider.dingtalk')}
 </h4>
 <span className={cn(TYPOGRAPHY.micro,'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 font-bold px-1.5 py-0.5 rounded-full border border-slate-200 dark:border-slate-700')}>
 {t('addMember.import.status.notConfigured')}
 </span>
 </div>
 <p className={cn(TYPOGRAPHY.micro,'text-slate-400 dark:text-slate-500 group-hover:text-slate-500 dark:group-hover:text-slate-400 transition-colors truncate')}>
 {t('addMember.import.dingtalkDesc')}
 </p>
 </div>
 <div className="pr-2">
 <Button
 size="small"
 variant="outline"
 className={cn(TYPOGRAPHY.bodyXs,'h-8 px-3 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800')}
 onClick={() => setConfigProvider('DingTalk')}
 >
 {t('addMember.import.actions.goToConfig')}
 <ArrowRight size={12} />
 </Button>
 </div>
 </div>

 <div className="group relative bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-2 flex items-center gap-4 transition-all hover:border-teal-300 dark:hover:border-teal-400/50 hover:shadow-lg hover:shadow-teal-500/5 dark:hover:shadow-teal-500/20">
 <div className="w-14 h-14 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 flex items-center justify-center shrink-0 grayscale group-hover:grayscale-0 transition-all duration-300">
 <ThirdPartyIcons.Lark />
 </div>
 <div className="flex-1 min-w-0 py-0.5">
 <div className="flex items-center gap-2 mb-1">
 <h4 className={cn(TYPOGRAPHY.bodyXs,'font-bold text-slate-900 dark:text-slate-100 group-hover:text-teal-600 dark:group-hover:text-teal-300 transition-colors')}>
 {t('addMember.provider.feishu')}
 </h4>
 <span className={cn(TYPOGRAPHY.micro,'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 font-bold px-1.5 py-0.5 rounded-full border border-slate-200 dark:border-slate-700')}>
 {t('addMember.import.status.notConfigured')}
 </span>
 </div>
 <p className={cn(TYPOGRAPHY.micro,'text-slate-400 dark:text-slate-500 group-hover:text-slate-500 dark:group-hover:text-slate-400 transition-colors truncate')}>
 {t('addMember.import.feishuDesc')}
 </p>
 </div>
 <div className="pr-2">
 <Button
 size="small"
 variant="outline"
 className={cn(TYPOGRAPHY.bodyXs,'h-8 px-3 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800')}
 onClick={() => setConfigProvider('Lark')}
 >
 {t('addMember.import.actions.goToConfig')}
 <ArrowRight size={12} />
 </Button>
 </div>
 </div>
 </div>
 </div>
 </div>

 <div className="bg-slate-50 dark:bg-slate-900/70 p-4 border-t border-slate-100 dark:border-slate-800/80 text-center">
 <p className={cn(TYPOGRAPHY.micro,'text-slate-400 dark:text-slate-500')}>
 {t('addMember.assistance.prefix')} <a href="#" className="text-brand-600 dark:text-brand-300 hover:underline">{t('addMember.assistance.guide')}</a> {t('addMember.assistance.or')}{' '}
 <a href="#" className="text-brand-600 dark:text-brand-300 hover:underline">{t('addMember.assistance.support')}</a>
 </p>
 </div>
 </div>)}
 </>)}
 </div>
 </div>
 </Modal>)
}
