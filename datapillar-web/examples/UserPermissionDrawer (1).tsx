import React, { useMemo, useState, useEffect } from 'react';
import { User, Role, PermissionLevel, PermissionResource, GravitinoAsset, AssetType } from '../../types';
import { X, Shield, ArrowRight, CornerDownRight, AlertCircle, Save, Database, SlidersHorizontal, Server, Folder, Table, ChevronDown, ChevronRight, AlertTriangle, CheckSquare, Layers, Lock, Unlock } from 'lucide-react';
import { Button } from '../UI/Button';
import { Avatar } from '../UI/Avatar';
import { Badge } from '../UI/Badge';
import { MOCK_GRAVITINO_ASSETS } from '../../constants';

interface UserPermissionDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  user: User;
  role: Role;
  onUpdatePermissions: (userId: string, resourceId: string, level: PermissionLevel) => void;
}

// Internal Switch Component
const Switch: React.FC<{ checked: boolean; onChange: () => void; color?: string; disabled?: boolean }> = ({ checked, onChange, color = 'bg-brand-600', disabled }) => (
    <button 
        type="button"
        onClick={onChange}
        disabled={disabled}
        className={`
            relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-600 focus-visible:ring-offset-2
            ${checked ? color : 'bg-slate-200'}
            ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
        `}
    >
        <span className="sr-only">Use setting</span>
        <span
            aria-hidden="true"
            className={`
                pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out
                ${checked ? 'translate-x-4' : 'translate-x-0'}
            `}
        />
    </button>
);

// Internal Data Config Component embedded for User granularity
const UserDataPermissionPanel: React.FC<{ user: User }> = ({ user }) => {
    const [selectedAssetId, setSelectedAssetId] = useState<string>('metalake_prod');
    const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['metalake_prod', 'cat_pg_payment']));
    const [localPrivileges, setLocalPrivileges] = useState<Record<string, string[]>>({});

    // Initialize local privileges from user prop
    useEffect(() => {
        const privs: Record<string, string[]> = {};
        user.dataPrivileges?.forEach(dp => {
            privs[dp.assetId] = dp.privileges;
        });
        setLocalPrivileges(privs);
    }, [user]);

    const findAsset = (nodes: GravitinoAsset[], id: string): GravitinoAsset | undefined => {
        for (const node of nodes) {
            if (node.id === id) return node;
            if (node.children) {
                const found = findAsset(node.children, id);
                if (found) return found;
            }
        }
        return undefined;
    };

    const selectedAsset = findAsset(MOCK_GRAVITINO_ASSETS, selectedAssetId);

    // Dynamic Privilege Definitions based on Asset Type
    const getPrivilegeGroups = (assetType: AssetType) => {
        const typeLabel = 
            assetType === 'metalake' ? 'Metalake' :
            assetType === 'catalog' ? 'Catalog' :
            assetType === 'schema' ? 'Schema' : 'Table';
            
        const childTypeLabel = 
            assetType === 'metalake' ? 'Catalog' :
            assetType === 'catalog' ? 'Schema' :
            assetType === 'schema' ? 'Table' : 'Column';

        return [
        {
            name: "元数据访问 (Metadata Access)",
            color: "bg-blue-600",
            items: [
            { 
                key: 'USAGE', 
                label: 'USAGE', 
                desc: `允许查看 ${typeLabel} 的属性、配置及元数据信息` 
            },
            { 
                key: 'SELECT', 
                label: 'SELECT', 
                desc: assetType === 'table' ? '允许查询表中的数据行' : `允许读取该 ${typeLabel} 下所有资源的元数据` 
            },
            ]
        },
        {
            name: "资源与数据管理 (Data Management)",
            color: "bg-emerald-600",
            items: [
            { 
                key: 'CREATE', 
                label: 'CREATE', 
                desc: assetType === 'table' ? '允许创建子分区或索引' : `允许在此 ${typeLabel} 下创建新的 ${childTypeLabel}` 
            },
            { 
                key: 'MODIFY', 
                label: 'MODIFY', 
                desc: '允许执行 UPDATE / INSERT 等数据变更操作' 
            },
            { 
                key: 'ALTER', 
                label: 'ALTER', 
                desc: `允许修改 ${typeLabel} 的结构（如增加列、修改属性）` 
            },
            ]
        },
        {
            name: "高危操作 (Danger Zone)",
            color: "bg-rose-600",
            danger: true,
            items: [
            { 
                key: 'DROP', 
                label: 'DROP', 
                desc: `高危：允许永久删除此 ${typeLabel} 及其所有包含的对象` 
            },
            ]
        }
        ];
    };

    const privilegeGroups = useMemo(() => {
        if (!selectedAsset) return [];
        return getPrivilegeGroups(selectedAsset.type);
    }, [selectedAsset]);

    // Breadcrumb generator
    const getBreadcrumbs = (id: string, nodes: GravitinoAsset[], path: GravitinoAsset[] = []): GravitinoAsset[] | null => {
        for (const node of nodes) {
            if (node.id === id) return [...path, node];
            if (node.children) {
                const res = getBreadcrumbs(id, node.children, [...path, node]);
                if (res) return res;
            }
        }
        return null;
    };

    const breadcrumbs = selectedAsset ? getBreadcrumbs(selectedAssetId, MOCK_GRAVITINO_ASSETS) : [];
    
    const hasPrivilege = (privilege: string) => {
        return localPrivileges[selectedAssetId]?.includes(privilege) || false;
    };

    const togglePrivilege = (privilege: string) => {
        setLocalPrivileges(prev => {
            const current = prev[selectedAssetId] || [];
            const newPrivs = current.includes(privilege) 
                ? current.filter(p => p !== privilege)
                : [...current, privilege];
            return { ...prev, [selectedAssetId]: newPrivs };
        });
    };

    const toggleExpand = (id: string, e: React.MouseEvent) => {
        e.stopPropagation();
        const newSet = new Set(expandedNodes);
        if (newSet.has(id)) newSet.delete(id);
        else newSet.add(id);
        setExpandedNodes(newSet);
    };

    const renderTree = (nodes: GravitinoAsset[], level = 0) => {
        return nodes.map(node => {
        const isExpanded = expandedNodes.has(node.id);
        const isSelected = selectedAssetId === node.id;
        const hasChildren = node.children && node.children.length > 0;

        const Icon = node.type === 'metalake' ? Server : 
                    node.type === 'catalog' ? Database : 
                    node.type === 'schema' ? Folder : Table;

        const colorClass = node.type === 'metalake' ? 'text-indigo-600' :
                            node.type === 'catalog' ? 'text-blue-600' :
                            node.type === 'schema' ? 'text-amber-500' : 'text-slate-500';

        // Check local state for highlight
        const hasPermissions = localPrivileges[node.id]?.length > 0;

        return (
            <div key={node.id}>
            <div 
                onClick={() => setSelectedAssetId(node.id)}
                className={`
                flex items-center gap-1.5 py-1.5 px-2 cursor-pointer select-none transition-colors border-l-2
                ${isSelected 
                    ? 'bg-brand-50 border-brand-500 text-brand-900 font-medium' 
                    : 'border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900'}
                `}
                style={{ paddingLeft: `${level * 16 + 8}px` }}
            >
                <div 
                onClick={(e) => hasChildren && toggleExpand(node.id, e)}
                className={`p-0.5 rounded hover:bg-black/5 transition-colors ${!hasChildren ? 'invisible' : ''}`}
                >
                {isExpanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                </div>
                
                <Icon size={14} className={`${colorClass} shrink-0`} />
                <span className="truncate text-sm">{node.name}</span>
                {hasPermissions && <span className="w-1.5 h-1.5 rounded-full bg-brand-500 ml-auto"></span>}
            </div>
            {hasChildren && isExpanded && (
                <div>{renderTree(node.children!, level + 1)}</div>
            )}
            </div>
        );
        });
    };

    return (
        <div className="flex h-full border rounded-xl border-slate-200 overflow-hidden bg-white shadow-sm ring-1 ring-slate-100">
             {/* Tree */}
             <div className="w-[280px] bg-slate-50/50 border-r border-slate-200 flex flex-col">
                <div className="p-3 border-b border-slate-200 bg-slate-50 font-bold text-xs text-slate-500 uppercase tracking-wider flex items-center gap-2">
                    <Layers size={14} />
                    资源拓扑
                </div>
                <div className="flex-1 overflow-y-auto py-2 custom-scrollbar">
                    {renderTree(MOCK_GRAVITINO_ASSETS)}
                </div>
             </div>

             {/* Matrix */}
             <div className="flex-1 flex flex-col bg-white min-w-0">
                {selectedAsset ? (
                    <div className="flex flex-col h-full animate-in fade-in duration-200">
                         {/* Header with Breadcrumbs */}
                         <div className="px-6 py-5 border-b border-slate-100">
                            <div className="flex items-center gap-2 text-xs text-slate-500 mb-2">
                                {breadcrumbs?.map((crumb, idx) => (
                                    <React.Fragment key={crumb.id}>
                                        {idx > 0 && <ChevronRight size={12} />}
                                        <span className={idx === breadcrumbs.length - 1 ? 'font-semibold text-slate-900' : ''}>
                                            {crumb.name}
                                        </span>
                                    </React.Fragment>
                                ))}
                            </div>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <div className={`p-2 rounded-lg ${
                                        selectedAsset.type === 'metalake' ? 'bg-indigo-50 text-indigo-600' :
                                        selectedAsset.type === 'catalog' ? 'bg-blue-50 text-blue-600' :
                                        selectedAsset.type === 'schema' ? 'bg-amber-50 text-amber-600' : 'bg-slate-100 text-slate-600'
                                    }`}>
                                         {selectedAsset.type === 'metalake' ? <Server size={20} /> : 
                                          selectedAsset.type === 'catalog' ? <Database size={20} /> : 
                                          selectedAsset.type === 'schema' ? <Folder size={20} /> : <Table size={20} />}
                                    </div>
                                    <div>
                                        <h4 className="font-bold text-slate-900 text-lg tracking-tight">{selectedAsset.name}</h4>
                                        <p className="text-xs text-slate-500 font-mono mt-0.5">{selectedAsset.description || '无描述'}</p>
                                    </div>
                                </div>
                                <div className="text-right">
                                    <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">ACL Context</span>
                                    <div className="flex items-center gap-1.5 mt-1">
                                        <Avatar src={user.avatarUrl} name={user.name} size="sm" className="w-5 h-5 text-[10px]" />
                                        <span className="text-sm font-semibold text-slate-700">{user.name}</span>
                                    </div>
                                </div>
                            </div>
                         </div>

                         {/* ACL List Content */}
                         <div className="flex-1 overflow-y-auto p-6 bg-slate-50/30">
                            
                            <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
                                {privilegeGroups.map((group, idx) => (
                                    <div key={group.name} className={`${idx !== privilegeGroups.length - 1 ? 'border-b border-slate-100' : ''}`}>
                                        {/* Group Header */}
                                        <div className="px-5 py-2.5 bg-slate-50/80 border-b border-slate-100 backdrop-blur-sm flex items-center justify-between">
                                            <h5 className={`text-xs font-bold uppercase tracking-wider flex items-center gap-2 ${group.danger ? 'text-rose-600' : 'text-slate-500'}`}>
                                                {group.danger ? <AlertCircle size={14} /> : <Shield size={14} />}
                                                {group.name}
                                            </h5>
                                            <span className="text-[10px] font-medium text-slate-400 bg-white px-2 py-0.5 rounded border border-slate-200">
                                                {group.items.filter(i => hasPrivilege(i.key)).length} / {group.items.length} 授权
                                            </span>
                                        </div>
                                        
                                        {/* Permission Rows */}
                                        <div className="divide-y divide-slate-50">
                                            {group.items.map(item => {
                                                const isActive = hasPrivilege(item.key);
                                                return (
                                                <div 
                                                    key={item.key} 
                                                    className={`
                                                        flex items-center justify-between px-5 py-4 transition-all duration-200
                                                        ${isActive ? 'bg-slate-50/50' : 'hover:bg-slate-50/30'}
                                                    `}
                                                >
                                                    <div className="flex items-start gap-4">
                                                        <div className={`mt-1 p-1.5 rounded-md transition-colors ${isActive ? 'bg-white shadow-sm ring-1 ring-slate-200' : 'bg-slate-100 text-slate-400'}`}>
                                                            {isActive ? <Unlock size={14} className="text-brand-600" /> : <Lock size={14} />}
                                                        </div>
                                                        <div>
                                                            <div className="flex items-center gap-2">
                                                                <span className={`text-sm font-bold font-mono ${isActive ? 'text-slate-900' : 'text-slate-600'}`}>
                                                                    {item.label}
                                                                </span>
                                                                {group.danger && (
                                                                    <span className="text-[10px] text-rose-600 bg-rose-50 border border-rose-100 px-1.5 rounded font-medium">High Risk</span>
                                                                )}
                                                            </div>
                                                            <p className="text-xs text-slate-500 mt-1">{item.desc}</p>
                                                        </div>
                                                    </div>
                                                    
                                                    <div className="flex items-center gap-4">
                                                        <span className={`text-xs font-medium transition-colors ${isActive ? 'text-brand-600' : 'text-slate-300'}`}>
                                                            {isActive ? 'Allow' : 'Deny'}
                                                        </span>
                                                        <Switch 
                                                            checked={isActive} 
                                                            onChange={() => togglePrivilege(item.key)}
                                                            color={group.color}
                                                        />
                                                    </div>
                                                </div>
                                            )})}
                                        </div>
                                    </div>
                                ))}
                            </div>
                         </div>
                    </div>
                ) : (
                    <div className="flex items-center justify-center h-full text-slate-400 flex-col gap-3">
                        <Database size={48} className="opacity-20" />
                        <p className="font-medium">请从左侧选择数据资产进行配置</p>
                    </div>
                )}
             </div>
        </div>
    );
};

// Define the extended permission type for internal use
type ExtendedPermission = PermissionResource & {
  isCustom: boolean;
  inheritedLevel: PermissionLevel;
};

export const UserPermissionDrawer: React.FC<UserPermissionDrawerProps> = ({ 
  isOpen, onClose, user, role, onUpdatePermissions 
}) => {
  const [activeTab, setActiveTab] = useState<'functional' | 'data'>('functional');

  if (!isOpen) return null;

  const PERMISSION_LABELS: Partial<Record<PermissionLevel, string>> = {
    [PermissionLevel.NONE]: '禁止',
    [PermissionLevel.READ]: '查看',
    [PermissionLevel.WRITE]: '管理', // 'Manage' implies write
  };

  // Merge role permissions with user custom permissions
  const effectivePermissions = useMemo<ExtendedPermission[]>(() => {
    return role.permissions.map(rp => {
      const custom = user.customPermissions?.find(cp => cp.id === rp.id);
      return {
        ...rp,
        level: custom ? custom.level : rp.level,
        isCustom: !!custom,
        inheritedLevel: rp.level
      };
    });
  }, [role, user]);

  const groupedPermissions = useMemo(() => {
    const groups: Record<string, ExtendedPermission[]> = {};
    effectivePermissions.forEach(p => {
      if (!groups[p.category]) groups[p.category] = [];
      groups[p.category].push(p);
    });
    return groups;
  }, [effectivePermissions]);

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/20 backdrop-blur-[1px] transition-opacity" onClick={onClose} />
      
      <div className="relative w-full max-w-4xl bg-white shadow-2xl h-full flex flex-col animate-in slide-in-from-right duration-300">
        
        {/* Header */}
        <div className="px-6 py-5 border-b border-slate-100 bg-slate-50/50 flex-shrink-0">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-4">
                <Avatar src={user.avatarUrl} name={user.name} size="lg" className="ring-4 ring-white shadow-sm" />
                <div>
                <h2 className="text-xl font-bold text-slate-900">{user.name} 的权限配置</h2>
                <div className="flex items-center gap-2 mt-1 text-sm text-slate-500">
                    <span>所属角色:</span>
                    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 border border-slate-200 text-slate-700`}>
                        <Shield size={10} /> {role.name}
                    </span>
                    <span className="w-1 h-1 bg-slate-300 rounded-full mx-1"></span>
                    <span>部门: {user.department}</span>
                </div>
                </div>
            </div>
            <button onClick={onClose} className="p-2 hover:bg-slate-200 rounded-full text-slate-400 transition-colors">
                <X size={20} />
            </button>
          </div>

          {/* Tabs */}
          <div className="flex gap-8">
            <button 
                onClick={() => setActiveTab('functional')}
                className={`pb-3 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
                    activeTab === 'functional' 
                    ? 'border-brand-600 text-brand-600' 
                    : 'border-transparent text-slate-500 hover:text-slate-700'
                }`}
            >
                <SlidersHorizontal size={16} />
                功能权限 (Functional)
            </button>

            <button 
                onClick={() => setActiveTab('data')}
                className={`pb-3 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
                    activeTab === 'data' 
                    ? 'border-brand-600 text-brand-600' 
                    : 'border-transparent text-slate-500 hover:text-slate-700'
                }`}
            >
                <Database size={16} />
                数据权限 (Gravitino)
                {user.dataPrivileges && user.dataPrivileges.length > 0 && (
                    <span className="w-2 h-2 rounded-full bg-brand-500"></span>
                )}
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto bg-slate-50/30">
          
          {activeTab === 'functional' && (
            <div className="p-6 animate-in fade-in duration-200">
                <div className="mb-6 bg-indigo-50 border border-indigo-100 rounded-lg p-4 flex gap-3">
                    <AlertCircle size={20} className="text-indigo-600 shrink-0" />
                    <div className="text-sm text-indigo-900">
                        <p className="font-medium">独立权限配置模式</p>
                        <p className="opacity-80 mt-0.5">您正在为该用户单独配置功能权限。此处的修改将覆盖 <span className="font-semibold">{role.name}</span> 角色的默认设置。</p>
                    </div>
                </div>

                <div className="space-y-6">
                    {Object.entries(groupedPermissions).map(([category, items]) => (
                    <div key={category}>
                        <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3 px-1">{category}</h3>
                        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden divide-y divide-slate-100">
                        {(items as ExtendedPermission[]).map((perm) => (
                            <div key={perm.id} className="p-4 flex items-center justify-between group hover:bg-slate-50 transition-colors">
                            <div className="flex-1 pr-4">
                                <div className="font-medium text-slate-900 text-sm mb-0.5">{perm.name}</div>
                                <div className="text-xs text-slate-500 flex items-center gap-2">
                                    {perm.isCustom ? (
                                        <span className="text-amber-600 font-medium bg-amber-50 px-1.5 rounded flex items-center gap-1">
                                            覆盖继承
                                        </span>
                                    ) : (
                                        <span className="text-slate-400 flex items-center gap-1">
                                            <CornerDownRight size={10} /> 继承自角色 ({PERMISSION_LABELS[perm.inheritedLevel]})
                                        </span>
                                    )}
                                </div>
                            </div>
                            
                            <div className="flex bg-slate-100 p-1 rounded-lg border border-slate-200">
                                {[PermissionLevel.NONE, PermissionLevel.READ, PermissionLevel.WRITE].map((level) => {
                                    const isActive = perm.level === level;
                                    
                                    let activeClass = "";
                                    if (isActive) {
                                        if (level === PermissionLevel.WRITE) activeClass = "bg-white text-brand-600 shadow-sm font-bold ring-1 ring-black/5";
                                        else if (level === PermissionLevel.READ) activeClass = "bg-white text-indigo-600 shadow-sm font-bold ring-1 ring-black/5";
                                        else activeClass = "bg-white text-slate-600 shadow-sm font-bold ring-1 ring-black/5";
                                    } else {
                                        activeClass = "text-slate-500 hover:text-slate-700 hover:bg-white/50";
                                    }

                                    return (
                                        <button
                                            key={level}
                                            onClick={() => onUpdatePermissions(user.id, perm.id, level)}
                                            className={`px-3 py-1.5 rounded text-xs transition-all ${activeClass}`}
                                        >
                                            {PERMISSION_LABELS[level]}
                                        </button>
                                    );
                                })}
                            </div>
                            </div>
                        ))}
                        </div>
                    </div>
                    ))}
                </div>
            </div>
          )}

          {activeTab === 'data' && (
              <div className="p-6 h-full flex flex-col animate-in fade-in duration-200">
                  <UserDataPermissionPanel user={user} />
              </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 bg-white border-t border-slate-200 flex justify-between items-center z-10 shrink-0">
           <div className="text-xs text-slate-400">
              修改即时生效
           </div>
           <div className="flex gap-3">
              <Button variant="secondary" onClick={onClose}>关闭</Button>
              <Button icon={<Save size={16} />} onClick={onClose}>完成配置</Button>
           </div>
        </div>

      </div>
    </div>
  );
};