import { useMemo, useState } from 'react'
import { Shield, Users } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import { AccessControlHeader } from '../AccessControlHeader'
import { FeatureList } from '../features/FeatureList'
import { FEATURE_SCHEMA } from '../features/featureSchema'
import { FeatureStep } from '../features/FeatureStep'
import { RoleList } from './RoleList'
import { MembersList } from './MembersList'
import { FunctionalPermission } from './FunctionalPermission'
import { buildPermissions } from './permissionResources'
import type { PermissionLevel } from './permissionConstants'

export type UserStatus = '已激活' | '已邀请' | '已禁用'

interface PermissionResourceBase {
  id: string
  name: string
  category: string
  description: string
}

interface PermissionResource extends PermissionResourceBase {
  level: PermissionLevel
}

export interface RoleDefinition {
  id: string
  name: string
  description: string
  isSystem?: boolean
  permissions: PermissionResource[]
}

export interface RoleItem extends RoleDefinition {
  userCount: number
}

export interface UserPermissionOverride {
  id: string
  level: PermissionLevel
}

export interface UserDataPrivilege {
  assetId: string
  privileges: string[]
}

export interface UserItem {
  id: string
  name: string
  email: string
  avatarUrl?: string
  roleId: string
  status: UserStatus
  lastActive: string
  department?: string
  customPermissions?: UserPermissionOverride[]
  dataPrivileges?: UserDataPrivilege[]
}

const ROLE_DEFINITIONS: RoleDefinition[] = [
  {
    id: 'role_owner',
    name: '超级管理员 (Owner)',
    description: '拥有平台所有权限，可管理组织与安全策略。',
    isSystem: true,
    permissions: buildPermissions('WRITE')
  },
  {
    id: 'role_lead',
    name: '研发负责人 (Tech Lead)',
    description: '负责研发流程、权限策略与发布审批。',
    permissions: buildPermissions('READ', {
      'asset.catalog': 'WRITE',
      'asset.lineage': 'WRITE',
      'asset.quality': 'WRITE',
      'build.workflow': 'WRITE',
      'build.ide': 'WRITE',
      'build.release': 'WRITE',
      'ai.assistant': 'WRITE',
      'ai.models': 'READ',
      'ai.cost': 'READ'
    })
  },
  {
    id: 'role_developer',
    name: '研发工程师 (Developer)',
    description: '专注于代码与工作流构建，无法修改基础设施与生产环境部署。',
    permissions: buildPermissions('READ', {
      'asset.catalog': 'WRITE',
      'asset.lineage': 'WRITE',
      'build.workflow': 'WRITE',
      'build.ide': 'WRITE',
      'ai.assistant': 'WRITE',
      'ai.cost': 'READ'
    })
  },
  {
    id: 'role_sre',
    name: '运维工程师 (SRE)',
    description: '负责稳定性与运行保障，可查看监控与成本。',
    permissions: buildPermissions('READ', {
      'asset.security': 'WRITE',
      'build.release': 'READ',
      'ai.cost': 'WRITE'
    })
  },
  {
    id: 'role_auditor',
    name: '审计员 (Auditor)',
    description: '只读访问审计与安全数据，无法修改配置。',
    permissions: buildPermissions('READ', {
      'build.workflow': 'NONE',
      'build.ide': 'NONE',
      'build.release': 'NONE',
      'ai.assistant': 'NONE',
      'ai.models': 'NONE',
      'ai.cost': 'NONE'
    })
  }
]

const INITIAL_USERS: UserItem[] = [
  {
    id: 'u-1',
    name: 'David Kim',
    email: 'david@datapillar.io',
    roleId: 'role_developer',
    status: '已激活',
    lastActive: '5 小时前',
    department: '平台研发部',
    dataPrivileges: [
      {
        assetId: 'tbl_orders',
        privileges: ['SELECT_TABLE', 'USE_SCHEMA', 'MODIFY_TABLE']
      }
    ]
  },
  {
    id: 'u-2',
    name: 'Emily Chen',
    email: 'emily@datapillar.io',
    roleId: 'role_developer',
    status: '已激活',
    lastActive: '10 分钟前',
    department: '平台研发部'
  },
  {
    id: 'u-3',
    name: '王琳',
    email: 'lin.wang@datapillar.ai',
    roleId: 'role_lead',
    status: '已邀请',
    lastActive: '今天 09:20',
    department: '产品设计中心'
  },
  {
    id: 'u-4',
    name: '赵一',
    email: 'yi.zhao@datapillar.ai',
    roleId: 'role_owner',
    status: '已激活',
    lastActive: '昨天 18:14',
    department: '平台研发部'
  },
  {
    id: 'u-5',
    name: '许星',
    email: 'xing.xu@datapillar.ai',
    roleId: 'role_auditor',
    status: '已禁用',
    lastActive: '3 天前',
    department: '合规与风控部',
    customPermissions: [{ id: 'asset.security', level: 'READ' }]
  },
  {
    id: 'u-6',
    name: '江栖',
    email: 'qi.jiang@datapillar.ai',
    roleId: 'role_sre',
    status: '已邀请',
    lastActive: '未激活',
    department: '运维保障部'
  }
]

export function PermissionLayout() {
  const [users, setUsers] = useState<UserItem[]>(INITIAL_USERS)
  const [roleDefinitions, setRoleDefinitions] = useState<RoleDefinition[]>(ROLE_DEFINITIONS)
  const [selectedRoleId, setSelectedRoleId] = useState<string>(ROLE_DEFINITIONS[2]?.id ?? ROLE_DEFINITIONS[0]?.id ?? '')
  const [activeTab, setActiveTab] = useState<'members' | 'functional'>('members')
  const [activeSection, setActiveSection] = useState<'architecture' | 'definition'>('definition')
  const [selectedFeatureId, setSelectedFeatureId] = useState<string>(FEATURE_SCHEMA[0]?.id ?? '')
  const [featureStepIndex, setFeatureStepIndex] = useState(0)

  const roleUserCounts = useMemo(() => {
    return users.reduce<Record<string, number>>((acc, user) => {
      acc[user.roleId] = (acc[user.roleId] ?? 0) + 1
      return acc
    }, {})
  }, [users])

  const roles = useMemo<RoleItem[]>(
    () =>
      roleDefinitions.map((role) => ({
        ...role,
        userCount: roleUserCounts[role.id] ?? 0
      })),
    [roleDefinitions, roleUserCounts]
  )

  const selectedRole = useMemo(() => roles.find((role) => role.id === selectedRoleId) ?? roles[0], [roles, selectedRoleId])

  const handleAddUser = (userId: string, roleId: string) => {
    setUsers((prev) =>
      prev.map((user) => (user.id === userId ? { ...user, roleId } : user))
    )
  }

  const handlePermissionUpdate = (userId: string, resourceId: string, level: PermissionLevel) => {
    setUsers((prev) =>
      prev.map((user) => {
        if (user.id !== userId) return user
        const customPermissions = user.customPermissions ?? []
        const exists = customPermissions.some((permission) => permission.id === resourceId)
        const nextCustom = exists
          ? customPermissions.map((permission) =>
              permission.id === resourceId ? { ...permission, level } : permission
            )
          : [...customPermissions, { id: resourceId, level }]
        return {
          ...user,
          customPermissions: nextCustom
        }
      })
    )
  }

  const handleRolePermissionUpdate = (resourceId: string, level: PermissionLevel) => {
    setRoleDefinitions((prev) =>
      prev.map((role) => {
        if (role.id !== selectedRoleId) return role
        return {
          ...role,
          permissions: role.permissions.map((permission) =>
            permission.id === resourceId ? { ...permission, level } : permission
          )
        }
      })
    )
  }

  const handleFeatureSelect = (featureId: string) => {
    setSelectedFeatureId(featureId)
    setFeatureStepIndex(0)
  }

  const handleSectionChange = (section: 'architecture' | 'definition') => {
    setActiveSection(section)
    if (section === 'definition') {
      setFeatureStepIndex(0)
    }
  }

  if (!selectedRole) {
    return null
  }

  return (
    <section className="flex h-full w-full flex-col overflow-hidden bg-slate-50 dark:bg-[#0f172a]">
      <AccessControlHeader activeTab={activeSection} onTabChange={handleSectionChange} />
      <div className="flex-1 overflow-hidden">
        {activeSection === 'architecture' ? (
          <div className="flex h-full w-full overflow-hidden">
            <RoleList roles={roles} selectedRoleId={selectedRoleId} onSelectRole={setSelectedRoleId} />
            <div className="flex-1 overflow-hidden">
              <div className="h-full overflow-hidden flex flex-col bg-white dark:bg-slate-900 shadow-sm dark:shadow-[0_16px_40px_-16px_rgba(0,0,0,0.45)]">
                <div className="h-14 px-6 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 flex items-end">
                  <div className="flex items-center justify-between gap-4 w-full">
                    <div className="flex items-center gap-8">
                      <button
                        type="button"
                        onClick={() => setActiveTab('members')}
                        className={cn(
                        TYPOGRAPHY.bodySm,
                        'pb-2 font-medium border-b-2 transition-all flex items-center gap-2',
                          activeTab === 'members'
                            ? 'border-brand-600 text-brand-600'
                            : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                        )}
                      >
                        <Users size={16} />
                        成员列表
                        <span className="ml-0.5 px-2 py-0.5 rounded-full text-xs font-semibold bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
                          {roleUserCounts[selectedRole.id] ?? 0}
                        </span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setActiveTab('functional')}
                        className={cn(
                        TYPOGRAPHY.bodySm,
                        'pb-2 font-medium border-b-2 transition-all flex items-center gap-2',
                          activeTab === 'functional'
                            ? 'border-brand-600 text-brand-600'
                            : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
                        )}
                      >
                        <Shield size={16} />
                        功能权限
                      </button>
                    </div>
                  </div>
                </div>
                <div
                  className={cn(
                    'flex-1 overflow-y-auto bg-slate-50 dark:bg-slate-950/35 custom-scrollbar',
                    activeTab === 'members' ? 'px-6 py-6' : 'px-0 py-0'
                  )}
                >
                  {activeTab === 'members' ? (
                    <MembersList
                      role={selectedRole}
                      users={users}
                      onAddUser={handleAddUser}
                      onUpdateUserPermissions={handlePermissionUpdate}
                    />
                  ) : (
                    <FunctionalPermission
                      mode="role"
                      role={selectedRole}
                      onUpdatePermission={handleRolePermissionUpdate}
                      className="px-6 py-6 animate-in fade-in zoom-in-95 duration-200"
                    />
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex h-full w-full overflow-hidden">
            <FeatureList selectedId={selectedFeatureId} onSelect={handleFeatureSelect} />
            <div className="flex-1 overflow-hidden">
              <div className="h-full overflow-hidden flex flex-col bg-white dark:bg-slate-900 shadow-sm dark:shadow-[0_16px_40px_-16px_rgba(0,0,0,0.45)]">
                <FeatureStep selectedId={selectedFeatureId} roles={roles} currentStepIndex={featureStepIndex} onStepChange={setFeatureStepIndex} />
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}
