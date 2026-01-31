import { useEffect, useMemo, useState } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
  ArrowRight,
  Building2,
  Check,
  CheckCircle2,
  ChevronLeft,
  Globe,
  Key,
  LayoutGrid,
  Loader2,
  Lock,
  RefreshCw,
  Search,
  UserPlus,
  Users,
  X
} from 'lucide-react'
import { Button, Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleDefinition, UserItem } from './Permission'
import { PermissionAvatar } from './PermissionAvatar'

interface AddMemberModalProps {
  isOpen: boolean
  onClose: () => void
  role: RoleDefinition
  users: UserItem[]
  onAddUser: (userId: string) => void
}

type ConfigProvider = 'DingTalk' | 'Lark'

const ThirdPartyIcons = {
  WeCom: () => (
    <svg viewBox="0 0 24 24" className="w-8 h-8" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M10.45 13.98c-3.6 0-6.52-2.38-6.52-5.3 0-2.92 2.92-5.3 6.52-5.3 3.6 0 6.53 2.38 6.53 5.3 0 2.92-2.93 5.3-6.53 5.3zM2.87 14.88a7.83 7.83 0 0 1 0-.15c.34-.63 1.05-1.07 2.03-1.32-.4-.7-.64-1.5-.64-2.34 0-3.52 3.5-6.38 7.82-6.38s7.82 2.86 7.82 6.38c0 3.52-3.5 6.38-7.82 6.38-.85 0-1.67-.1-2.43-.3l-2.76 1.7a.5.5 0 0 1-.76-.41v-1.92c-.36-.34-.96-.85-1.54-1.28-.58-.43-1.12-.9-1.72-.36z"
        fill="#4776E6"
      />
      <path
        d="M17.82 13.06c2.56 0 4.63 1.63 4.63 3.64 0 2-2.07 3.63-4.63 3.63-.52 0-1.02-.06-1.5-.18v1.3c0 .28-.32.45-.55.3l-1.66-1.06c-.4.12-.9.2-1.42.34-.23-.28-.33-.63-.33-.98 0-1.37.8-2.6 2.1-3.32a6.3 6.3 0 0 1 3.36-3.67z"
        fill="#4776E6"
        opacity="0.7"
      />
    </svg>
  ),
  DingTalk: () => (
    <svg viewBox="0 0 24 24" className="w-8 h-8" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M11.64 2.06L1.87 5.62c-.8.3-.92 1.4-.2 1.84l7.63 4.7 9.87-4.48c.35-.16.65.25.37.5l-8.08 7.28v5.82c0 .4.48.6.76.32l2.67-2.67 4.54 2.8c.67.4 1.54-.05 1.6-.84l.97-17.84c.04-.84-.96-1.35-1.64-1.1L11.64 2.07z"
        fill="#0089FF"
      />
    </svg>
  ),
  Lark: () => (
    <svg viewBox="0 0 24 24" className="w-8 h-8" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        d="M11.6 2.6c1.67-1.17 3.96-.92 5.33.58 1.13 1.23.97 3.12-.32 4.14l-8.4 6.64c-1.3 1.02-3.2 1-4.32-.23-1.12-1.23-.97-3.12.32-4.14L11.6 2.6z"
        fill="#00D6B9"
      />
      <path
        d="M19.38 8.16c1.13 1.23.97 3.12-.32 4.14l-6.24 4.93c-1.3 1.02-3.2 1-4.33-.23-.5-.55-.73-1.26-.68-1.95l8.77-6.94c.9-.7 2.07-.64 2.8.05z"
        fill="#00D6B9"
        opacity="0.8"
      />
    </svg>
  )
}

export function AddMemberModal({ isOpen, onClose, role, users, onAddUser }: AddMemberModalProps) {
  const [activeTab, setActiveTab] = useState<'platform' | 'import'>('platform')
  const [configProvider, setConfigProvider] = useState<ConfigProvider | null>(null)
  const [isSyncing, setIsSyncing] = useState(false)
  const [activeOrgId, setActiveOrgId] = useState('all')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([])

  const availableUsers = useMemo(() => users.filter((user) => user.roleId !== role.id), [users, role.id])
  const orgFilterMap: Record<string, string> = {
    product: '产品设计中心',
    risk: '合规与风控部',
    biz: '商业化团队',
    rd: '平台研发部',
    sre: '运维保障部'
  }
  const roleDepartmentMap: Record<string, string> = {
    role_owner: '产品设计中心',
    role_lead: '平台研发部',
    role_developer: '平台研发部',
    role_auditor: '合规与风控部',
    role_sre: '运维保障部'
  }
  const departmentCounts = useMemo(() => {
    return availableUsers.reduce<Record<string, number>>((acc, user) => {
      const department = roleDepartmentMap[user.roleId] ?? '其他'
      acc[department] = (acc[department] ?? 0) + 1
      return acc
    }, {})
  }, [availableUsers, roleDepartmentMap])
  const orgUnits = useMemo<Array<{ id: string; name: string; count: number; icon: LucideIcon }>>(
    () => [
      { id: 'all', name: '所有成员', count: availableUsers.length, icon: LayoutGrid },
      { id: 'product', name: '产品设计中心', count: departmentCounts['产品设计中心'] ?? 0, icon: Building2 },
      { id: 'risk', name: '合规与风控部', count: departmentCounts['合规与风控部'] ?? 0, icon: Building2 },
      { id: 'biz', name: '商业化团队', count: departmentCounts['商业化团队'] ?? 0, icon: Building2 },
      { id: 'rd', name: '平台研发部', count: departmentCounts['平台研发部'] ?? 0, icon: Building2 },
      { id: 'sre', name: '运维保障部', count: departmentCounts['运维保障部'] ?? 0, icon: Building2 }
    ],
    [availableUsers.length, departmentCounts]
  )
  const [allUnit, ...departmentUnits] = orgUnits
  const filteredUsers = useMemo(() => {
    const scopedUsers =
      activeOrgId === 'all'
        ? availableUsers
        : availableUsers.filter((user) => roleDepartmentMap[user.roleId] === orgFilterMap[activeOrgId])
    const keyword = searchQuery.trim().toLowerCase()
    if (!keyword) return scopedUsers
    return scopedUsers.filter((user) => {
      return user.name.toLowerCase().includes(keyword) || user.email.toLowerCase().includes(keyword)
    })
  }, [activeOrgId, availableUsers, roleDepartmentMap, searchQuery, orgFilterMap])
  const selectedCount = selectedUserIds.length
  const isAllSelected = filteredUsers.length > 0 && filteredUsers.every((user) => selectedUserIds.includes(user.id))

  useEffect(() => {
    if (!isOpen) {
      setActiveTab('platform')
      setConfigProvider(null)
      setActiveOrgId('all')
      setSearchQuery('')
      setSelectedUserIds([])
    }
  }, [isOpen])

  if (!isOpen) return null

  const toggleUserSelection = (userId: string) => {
    setSelectedUserIds((prev) => (prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]))
  }

  const toggleSelectAll = () => {
    if (isAllSelected) {
      const filteredIds = new Set(filteredUsers.map((user) => user.id))
      setSelectedUserIds((prev) => prev.filter((id) => !filteredIds.has(id)))
      return
    }
    const filteredIds = new Set(filteredUsers.map((user) => user.id))
    setSelectedUserIds((prev) => Array.from(new Set([...prev, ...filteredIds])))
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
    }, 1500)
  }

  const renderConfigForm = () => (
    <div className="p-5">
      <button
        type="button"
        className="flex items-center gap-2 mb-4 text-slate-500 cursor-pointer hover:text-slate-900 transition-colors"
        onClick={() => setConfigProvider(null)}
      >
        <ChevronLeft size={16} />
        <span className={cn(TYPOGRAPHY.bodySm, 'font-medium')}>返回来源列表</span>
      </button>

      <div className="flex items-center gap-4 mb-6">
        <div className="w-16 h-16 bg-slate-50 rounded-2xl flex items-center justify-center border border-slate-100 shadow-sm">
          {configProvider === 'DingTalk' ? <ThirdPartyIcons.DingTalk /> : <ThirdPartyIcons.Lark />}
        </div>
        <div>
          <h3 className={cn(TYPOGRAPHY.subtitle, 'font-bold text-slate-900')}>
            配置 {configProvider === 'DingTalk' ? '钉钉' : '飞书'} 连接
          </h3>
          <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 mt-1')}>
            请输入您的企业 {configProvider === 'DingTalk' ? '钉钉' : '飞书'} 应用凭证以完成授权。
          </p>
        </div>
      </div>

      <div className="space-y-4">
        <div>
          <label className={cn(TYPOGRAPHY.caption, 'block font-semibold text-slate-700 uppercase tracking-wider mb-1.5')}>
            {configProvider === 'DingTalk' ? 'Agent ID' : 'App ID'} <span className="text-red-500">*</span>
          </label>
          <div className="relative">
            <Globe className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              type="text"
              className={cn(
                TYPOGRAPHY.bodySm,
                'w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 outline-none transition-all'
              )}
              placeholder={configProvider === 'DingTalk' ? '例如: 23456789' : '例如: cli_a1b2c3d4e5'}
            />
          </div>
        </div>

        <div>
          <label className={cn(TYPOGRAPHY.caption, 'block font-semibold text-slate-700 uppercase tracking-wider mb-1.5')}>
            App Key <span className="text-red-500">*</span>
          </label>
          <div className="relative">
            <Key className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              type="text"
              className={cn(
                TYPOGRAPHY.bodySm,
                'w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 outline-none transition-all'
              )}
              placeholder="输入应用的 App Key"
            />
          </div>
        </div>

        <div>
          <label className={cn(TYPOGRAPHY.caption, 'block font-semibold text-slate-700 uppercase tracking-wider mb-1.5')}>
            App Secret <span className="text-red-500">*</span>
          </label>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={16} />
            <input
              type="password"
              className={cn(
                TYPOGRAPHY.bodySm,
                'w-full pl-10 pr-4 py-2 bg-white border border-slate-200 rounded-lg focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 outline-none transition-all'
              )}
              placeholder="输入应用的 App Secret"
            />
          </div>
        </div>

        <div className="pt-3 flex gap-3">
          <Button className="w-full" size="normal" onClick={() => setConfigProvider(null)}>
            保存并连接
          </Button>
        </div>

        <div className="flex justify-center mt-4">
          <a href="#" className={cn(TYPOGRAPHY.caption, 'text-brand-600 hover:underline')}>
            如何获取这些信息？
          </a>
        </div>
      </div>
    </div>
  )

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size={configProvider ? 'sm' : 'lg'}
      title="添加成员"
      subtitle={(
        <span className={cn(TYPOGRAPHY.caption, 'text-slate-500 flex items-center gap-2')}>
          添加至
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200">
            <UserPlus size={12} />
            {role.name}
          </span>
        </span>
      )}
    >
      <div
        className={cn(
          'flex flex-col min-h-0 -mx-8 -my-4 overflow-hidden',
          configProvider ? 'min-h-0' : 'h-[520px]'
        )}
      >
        {!configProvider && (
          <div className="bg-white border-b border-slate-100 px-6 pt-4 pb-0 shrink-0">
            <div className="flex gap-6">
              <button
                type="button"
                onClick={() => setActiveTab('platform')}
                className={cn(
                  TYPOGRAPHY.bodySm,
                  `pb-3 font-medium border-b-2 transition-all ${
                    activeTab === 'platform'
                      ? 'border-brand-600 text-brand-600'
                      : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`
                )}
              >
                组织架构选择
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('import')}
                className={cn(
                  TYPOGRAPHY.bodySm,
                  `pb-3 font-medium border-b-2 transition-all flex items-center gap-1.5 ${
                    activeTab === 'import'
                      ? 'border-brand-600 text-brand-600'
                      : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`
                )}
              >
                第三方同步
                <span className={cn(TYPOGRAPHY.micro, 'bg-green-100 text-green-700 px-1.5 py-0.5 rounded-full font-bold')}>
                  Auto
                </span>
              </button>
            </div>
          </div>
        )}

        <div className="flex-1 min-h-0 bg-slate-50 dark:bg-slate-900/40 overflow-hidden">
          {configProvider ? (
            <div className="flex-1 min-h-0 overflow-y-auto">{renderConfigForm()}</div>
          ) : (
            <>
              {activeTab === 'platform' && (
                <div className="flex w-full h-full min-h-0 bg-white">
                  <div className="w-60 bg-slate-50/50 border-r border-slate-100 flex flex-col shrink-0 min-h-0">
                    <div className="px-4 pt-6 pb-3 border-b border-slate-200/60">
                      <h4 className={cn(TYPOGRAPHY.bodyXs, 'font-semibold text-slate-400 uppercase tracking-wider px-2 mb-2')}>部门筛选</h4>
                    </div>
                    <div className="flex-1 overflow-y-auto px-3 pt-2 pb-3 space-y-0.5 custom-scrollbar min-h-0">
                      <button
                        type="button"
                        onClick={() => setActiveOrgId(allUnit.id)}
                        className={cn(
                          TYPOGRAPHY.bodyXs,
                          `w-full flex items-center justify-between px-3 py-2 rounded-lg font-medium transition-all ${
                            activeOrgId === allUnit.id
                              ? 'bg-white text-brand-600 shadow-sm ring-1 ring-slate-200'
                              : 'text-slate-600 hover:bg-slate-100/50 hover:text-slate-900'
                          }`
                        )}
                      >
                        <div className="flex items-center gap-2.5">
                          <div
                            className={`p-1 rounded ${
                              activeOrgId === allUnit.id ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            <allUnit.icon size={14} />
                          </div>
                          <span>{allUnit.name}</span>
                        </div>
                        <span
                          className={cn(
                            TYPOGRAPHY.micro,
                            `px-1.5 py-0.5 rounded-md font-bold ${
                              activeOrgId === allUnit.id ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-400'
                            }`
                          )}
                        >
                          {allUnit.count}
                        </span>
                      </button>

                      <div className="my-2 border-t border-slate-200/50 mx-2"></div>

                      {departmentUnits.map((unit) => {
                        const isActive = activeOrgId === unit.id
                        const Icon = unit.icon
                        return (
                          <button
                            key={unit.id}
                            type="button"
                            onClick={() => setActiveOrgId(unit.id)}
                            className={cn(
                              TYPOGRAPHY.bodyXs,
                              `w-full flex items-center justify-between px-3 py-2 rounded-lg font-medium transition-all ${
                                isActive
                                  ? 'bg-white text-brand-600 shadow-sm ring-1 ring-slate-200'
                                  : 'text-slate-600 hover:bg-slate-100/50 hover:text-slate-900'
                              }`
                            )}
                          >
                            <div className="flex items-center gap-2.5 truncate">
                              <div className={`p-1 rounded ${isActive ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-500'}`}>
                                <Icon size={14} />
                              </div>
                              <span className="truncate max-w-[120px]" title={unit.name}>
                                {unit.name}
                              </span>
                            </div>
                            <span
                              className={cn(
                                TYPOGRAPHY.micro,
                                `px-1.5 py-0.5 rounded-md font-bold ${
                                  isActive ? 'bg-brand-50 text-brand-600' : 'bg-slate-100 text-slate-400'
                                }`
                              )}
                            >
                              {unit.count}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                  </div>

                  <div className="flex-1 flex flex-col min-w-0 bg-white relative min-h-0">
                    <div className="border-b border-slate-100 sticky top-0 bg-white/95 backdrop-blur z-[5]">
                      <div className="px-1.5 pt-5 pb-4">
                        <div className="relative group">
                          <Search
                            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-brand-500 transition-colors"
                            size={16}
                          />
                          <input
                            type="text"
                            value={searchQuery}
                            onChange={(event) => setSearchQuery(event.target.value)}
                            placeholder="搜索姓名或邮箱..."
                            className={cn(
                              TYPOGRAPHY.bodyXs,
                              'w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg focus:bg-white focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 outline-none transition-all'
                            )}
                          />
                        </div>
                      </div>
                      <div className={cn(TYPOGRAPHY.bodyXs, 'flex items-center px-1.5 py-2 bg-slate-50/50 font-semibold text-slate-500 uppercase tracking-wider')}>
                        <div className="w-[32px] flex justify-center">
                          {filteredUsers.length > 0 && (
                            <div
                              className={`w-3.5 h-3.5 rounded border flex items-center justify-center cursor-pointer transition-colors ${
                                isAllSelected
                                  ? 'bg-brand-600 border-brand-600'
                                  : 'border-slate-300 bg-white hover:border-slate-400'
                              }`}
                              onClick={toggleSelectAll}
                            >
                              {isAllSelected && <Check size={8} className="text-white" />}
                            </div>
                          )}
                        </div>
                        <div className="flex-1 pl-1.5">姓名 / 部门</div>
                        <div className="w-1/3 text-center px-1.5">邮箱</div>
                      </div>
                    </div>

                    <div className="flex-1 overflow-y-auto pb-20 min-h-0">
                      {filteredUsers.length === 0 ? (
                        <div className="h-full flex flex-col items-center justify-center text-slate-400 pb-20">
                          <div className="w-16 h-16 bg-slate-50 rounded-full flex items-center justify-center mb-4">
                            <Search size={24} className="opacity-40" />
                          </div>
                          <p className={cn(TYPOGRAPHY.bodyXs, 'font-medium text-slate-500')}>未找到匹配的成员</p>
                        </div>
                      ) : (
                        <div className="divide-y divide-slate-50">
                          {filteredUsers.map((user) => {
                            const department = roleDepartmentMap[user.roleId] ?? '平台研发部'
                            const isSelected = selectedUserIds.includes(user.id)
                            return (
                              <div
                                key={user.id}
                                onClick={() => toggleUserSelection(user.id)}
                                className={`group flex items-center px-1.5 py-3 cursor-pointer transition-colors duration-150 ${
                                  isSelected ? 'bg-brand-50/60' : 'hover:bg-slate-50'
                                }`}
                              >
                                <div className="w-[32px] flex justify-center shrink-0">
                                  <div
                                    onClick={(event) => {
                                      event.stopPropagation()
                                      toggleUserSelection(user.id)
                                    }}
                                    className={`w-3.5 h-3.5 rounded border flex items-center justify-center transition-all duration-200 ${
                                      isSelected
                                        ? 'bg-brand-600 border-brand-600 scale-100'
                                        : 'border-slate-300 bg-white group-hover:border-slate-400'
                                    }`}
                                  >
                                    <Check
                                      size={8}
                                      className={`text-white transition-transform ${isSelected ? 'scale-100' : 'scale-0'}`}
                                    />
                                  </div>
                                </div>

                                <div className="flex-1 flex items-center gap-3 min-w-0 pl-1.5">
                                  <PermissionAvatar
                                    name={user.name}
                                    src={user.avatarUrl}
                                    size="sm"
                                    className={`${isSelected ? 'ring-2 ring-brand-200' : ''} transition-all w-9 h-9`}
                                  />
                                  <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                      <span className={cn(TYPOGRAPHY.bodyXs, `font-medium truncate ${isSelected ? 'text-brand-900' : 'text-slate-900'}`)}>
                                        {user.name}
                                      </span>
                                    </div>
                                    {activeOrgId === 'all' && (
                                      <div className={cn(TYPOGRAPHY.caption, 'text-slate-500 mt-0.5 flex items-center gap-1.5')}>
                                        <Building2 size={10} />
                                        <span className="truncate">{department}</span>
                                      </div>
                                    )}
                                  </div>
                                </div>

                                <div className={cn(TYPOGRAPHY.caption, 'w-1/3 text-center text-slate-400 truncate font-mono px-1.5 group-hover:text-slate-500 transition-colors')}>
                                  {user.email}
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>

                    {selectedCount > 0 && (
                      <div className="absolute bottom-6 left-1/2 -translate-x-1/2 w-[90%] z-30">
                        <div className="bg-slate-900 text-white px-3 py-1.5 rounded-2xl shadow-2xl shadow-slate-900/20 flex items-center justify-between border border-slate-800 animate-in slide-in-from-bottom-4 duration-300">
                          <div className="flex items-center gap-3 px-2 min-w-0 flex-1">
                            <div className="flex -space-x-2 overflow-hidden py-0.5 pl-1">
                              {selectedUserIds.slice(0, 5).map((userId) => {
                                const user = availableUsers.find((item) => item.id === userId)
                                if (!user) return null
                                return (
                                  <div
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
                                  </div>
                                )
                              })}
                              {selectedCount > 5 && (
                                <div className="w-6 h-6 rounded-full bg-slate-800 ring-2 ring-slate-900 flex items-center justify-center text-[10px] font-bold">
                                  +{selectedCount - 5}
                                </div>
                              )}
                            </div>
                            <div className="h-8 w-px bg-slate-700/50 mx-1 hidden sm:block"></div>
                            <div className={cn(TYPOGRAPHY.caption, 'font-medium whitespace-nowrap')}>已选 {selectedCount} 位成员</div>
                          </div>

                          <div className="flex items-center gap-3 pl-4">
                            <button
                              type="button"
                              onClick={() => setSelectedUserIds([])}
                              className="text-xs text-slate-400 hover:text-white transition-colors"
                            >
                              清空
                            </button>
                            <Button
                              size="small"
                              variant="primary"
                              className="bg-brand-500 hover:bg-brand-400 text-white border-0 shadow-lg shadow-brand-500/25 px-5"
                              onClick={handleConfirmAdd}
                            >
                              确认添加
                            </Button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {activeTab === 'import' && (
                <div className="h-full flex flex-col min-h-0">
                  <div className="flex-1 min-h-0 overflow-y-auto">
                    <div className="px-8 py-8">
                      <div className="text-center mb-8">
                        <div className="inline-flex items-center justify-center w-12 h-12 rounded-2xl bg-brand-50 text-brand-600 mb-4 ring-1 ring-brand-100 shadow-sm">
                          <RefreshCw size={22} />
                        </div>
                        <h3 className={cn(TYPOGRAPHY.subtitle, 'font-bold text-slate-900 tracking-tight')}>同步组织架构</h3>
                        <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 mt-2 max-w-sm mx-auto leading-relaxed')}>
                          连接您的企业 OA 平台，自动拉取最新的部门结构与人员名单，保持权限分配实时更新。
                        </p>
                      </div>

                      <div className="space-y-2">
                        <div className="group relative bg-white rounded-xl border border-slate-200 p-2 flex items-center gap-4 transition-all hover:border-indigo-300 hover:shadow-lg hover:shadow-indigo-500/5">
                          <div className="w-14 h-14 rounded-lg bg-indigo-50/30 border border-indigo-100/50 flex items-center justify-center shrink-0">
                            <ThirdPartyIcons.WeCom />
                          </div>
                          <div className="flex-1 min-w-0 py-0.5">
                            <div className="flex items-center gap-2 mb-1">
                              <h4 className={cn(TYPOGRAPHY.bodyXs, 'font-bold text-slate-900')}>企业微信</h4>
                              <div className={cn(TYPOGRAPHY.micro, 'flex items-center gap-1 bg-emerald-50 text-emerald-700 px-1.5 py-0.5 rounded-full font-bold border border-emerald-100/50')}>
                                <CheckCircle2 size={10} className="fill-emerald-100 stroke-emerald-600" />
                                <span>已连接</span>
                              </div>
                            </div>
                            <div className={cn(TYPOGRAPHY.micro, 'flex items-center gap-2 text-slate-500')}>
                              <span className="truncate">今天 09:30</span>
                              <span className="w-0.5 h-2.5 bg-slate-200 rounded-full" />
                              <span>128 人</span>
                            </div>
                          </div>
                          <div className="pr-2">
                            <Button
                              size="small"
                              variant="outline"
                              className={cn(
                                TYPOGRAPHY.bodyXs,
                                'h-8 px-3 border-slate-200 text-slate-600 group-hover:border-indigo-200 group-hover:text-indigo-600 group-hover:bg-indigo-50 transition-all'
                              )}
                              onClick={handleSync}
                            >
                              {isSyncing ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
                              {isSyncing ? '同步中' : '同步'}
                            </Button>
                          </div>
                        </div>

                        <div className="group relative bg-white rounded-xl border border-slate-200 p-2 flex items-center gap-4 transition-all hover:border-blue-300 hover:shadow-lg hover:shadow-blue-500/5">
                          <div className="w-14 h-14 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-center shrink-0 grayscale group-hover:grayscale-0 transition-all duration-300">
                            <ThirdPartyIcons.DingTalk />
                          </div>
                          <div className="flex-1 min-w-0 py-0.5">
                            <div className="flex items-center gap-2 mb-1">
                              <h4 className={cn(TYPOGRAPHY.bodyXs, 'font-bold text-slate-900 group-hover:text-blue-600 transition-colors')}>
                                钉钉
                              </h4>
                              <span className={cn(TYPOGRAPHY.micro, 'bg-slate-100 text-slate-500 font-bold px-1.5 py-0.5 rounded-full border border-slate-200')}>
                                未配置
                              </span>
                            </div>
                            <p className={cn(TYPOGRAPHY.micro, 'text-slate-400 group-hover:text-slate-500 transition-colors truncate')}>
                              支持同步部门、员工及角色组
                            </p>
                          </div>
                          <div className="pr-2">
                            <Button
                              size="small"
                              variant="outline"
                              className={cn(TYPOGRAPHY.bodyXs, 'h-8 px-3')}
                              onClick={() => setConfigProvider('DingTalk')}
                            >
                              去配置
                              <ArrowRight size={12} />
                            </Button>
                          </div>
                        </div>

                        <div className="group relative bg-white rounded-xl border border-slate-200 p-2 flex items-center gap-4 transition-all hover:border-teal-300 hover:shadow-lg hover:shadow-teal-500/5">
                          <div className="w-14 h-14 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-center shrink-0 grayscale group-hover:grayscale-0 transition-all duration-300">
                            <ThirdPartyIcons.Lark />
                          </div>
                          <div className="flex-1 min-w-0 py-0.5">
                            <div className="flex items-center gap-2 mb-1">
                              <h4 className={cn(TYPOGRAPHY.bodyXs, 'font-bold text-slate-900 group-hover:text-teal-600 transition-colors')}>
                                飞书
                              </h4>
                              <span className={cn(TYPOGRAPHY.micro, 'bg-slate-100 text-slate-500 font-bold px-1.5 py-0.5 rounded-full border border-slate-200')}>
                                未配置
                              </span>
                            </div>
                            <p className={cn(TYPOGRAPHY.micro, 'text-slate-400 group-hover:text-slate-500 transition-colors truncate')}>
                              支持同步组织架构与文档权限
                            </p>
                          </div>
                          <div className="pr-2">
                            <Button
                              size="small"
                              variant="outline"
                              className={cn(TYPOGRAPHY.bodyXs, 'h-8 px-3')}
                              onClick={() => setConfigProvider('Lark')}
                            >
                              去配置
                              <ArrowRight size={12} />
                            </Button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="bg-slate-50 p-4 border-t border-slate-100 text-center">
                    <p className={cn(TYPOGRAPHY.micro, 'text-slate-400')}>
                      需要协助？查看 <a href="#" className="text-brand-600 hover:underline">配置指南</a> 或{' '}
                      <a href="#" className="text-brand-600 hover:underline">联系技术支持</a>
                    </p>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </Modal>
  )
}
