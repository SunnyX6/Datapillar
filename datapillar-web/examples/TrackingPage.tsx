import React, { useState, useCallback } from 'react';
import { 
  Target, 
  Search, 
  Plus, 
  Activity, 
  CheckCircle2, 
  Smartphone, 
  Code2,
  Terminal,
  MousePointerClick,
  Filter,
  Settings,
  ShieldCheck,
  LayoutTemplate,
  MousePointer2,
  List,
  Layers,
  Database,
  FileText,
  Hash,
  ToggleLeft,
  Calendar,
  ListChecks,
  X,
  Save,
  Info,
  Combine,
  Trash2,
  User,
  MapPin,
  Clock,
  Copy,
  ArrowRight,
  GitBranch,
  Globe,
  Book,
  Link,
  Lock,
  AlertCircle,
  FileJson,
  Tags,
  MoreHorizontal,
  HelpCircle,
  Fingerprint,
  Eye,
  LayoutGrid,
  Zap,
  TrendingUp,
  Table,
  Type,
  AlignLeft,
  Check,
  Box,
  Split,
  Workflow,
  Merge,
  ArrowDown,
  GripVertical,
  MoveRight,
  Braces,
  Parentheses,
  Delete,
  Sigma,
  FunctionSquare,
  CornerDownRight,
  ArrowDownCircle,
  ToggleRight,
  ChevronRight
} from 'lucide-react';

// --- Domain Models ---

type PropertyType = 'STRING' | 'NUMBER' | 'BOOL' | 'LIST' | 'DATETIME' | 'OBJECT';
type SchemaKind = 'ATOMIC' | 'COMPOSITE';

// 1. The Schema (Meta Definition)
interface EventSchema {
  id: string;
  key: string;        // e.g. "order_paid"
  name: string;       // e.g. "订单支付成功"
  kind: SchemaKind;   // NEW: Atomic or Composite
  description: string;
  domain: string;     // e.g. "Trade", "User"
  status: 'active' | 'deprecated';
  standardProperties: DataProperty[]; 
  owner: string;
  usageCount: number; 
}

// LOGIC STRUCTURE
interface LogicGroup {
    id: string;
    operator: 'OR' | 'AND'; // User can toggle this now
    schemaIds: string[]; 
}

// 2. The Implementation (Tracking Point)
interface TrackingPoint {
  id: string;
  schemaId: string;
  schemaName: string;
  viewPath: string;
  platform: 'Web' | 'App' | 'Server';
  triggerType: 'Click' | 'View' | 'System';
  triggerDescription: string;
  status: 'planned' | 'implemented' | 'tested';
  contextProperties: DataProperty[]; 
}

interface DataProperty {
  id: string;
  name: string;       
  displayName: string; 
  type: PropertyType;
  isRequired: boolean;
  description?: string;
  exampleValue?: string;
  source?: 'SCHEMA' | 'CONTEXT'; 
}

// --- Mock Data ---

const MOCK_SCHEMAS: EventSchema[] = [
    {
        id: 'sch_01', key: 'order_paid', name: '订单支付成功', kind: 'ATOMIC', description: '核心交易事件，当支付网关返回成功时触发。包含订单金额、币种及支付方式。', domain: 'Trade', status: 'active', owner: 'Trade Team', usageCount: 12,
        standardProperties: [
            { id: 'sp_1', name: 'order_id', displayName: '订单ID', type: 'STRING', isRequired: true, source: 'SCHEMA' },
            { id: 'sp_2', name: 'amount', displayName: '支付金额', type: 'NUMBER', isRequired: true, source: 'SCHEMA' }
        ]
    },
    {
        id: 'sch_02', key: 'product_view', name: '商品详情浏览', kind: 'ATOMIC', description: '用户进入商品详情页。用于计算转化率漏斗。', domain: 'Product', status: 'active', owner: 'Product Team', usageCount: 85,
        standardProperties: [
            { id: 'sp_3', name: 'sku_id', displayName: 'SKU ID', type: 'STRING', isRequired: true, source: 'SCHEMA' }
        ]
    },
    {
        id: 'sch_03', key: 'banner_click', name: 'Banner 点击', kind: 'ATOMIC', description: '通用 Banner 组件点击事件。支持首页、活动页等多场景复用。', domain: 'Marketing', status: 'active', owner: 'Growth Team', usageCount: 240,
        standardProperties: [
            { id: 'sp_4', name: 'banner_id', displayName: 'Banner ID', type: 'STRING', isRequired: true, source: 'SCHEMA' },
            { id: 'sp_5', name: 'target_url', displayName: '跳转链接', type: 'STRING', isRequired: true, source: 'SCHEMA' }
        ]
    },
    {
        id: 'sch_04', key: 'add_to_cart', name: '加入购物车', kind: 'ATOMIC', description: '用户点击加入购物车按钮。', domain: 'Trade', status: 'active', owner: 'Trade Team', usageCount: 45,
        standardProperties: [
            { id: 'sp_6', name: 'sku_id', displayName: 'SKU ID', type: 'STRING', isRequired: true, source: 'SCHEMA' },
            { id: 'sp_7', name: 'quantity', displayName: '数量', type: 'NUMBER', isRequired: true, source: 'SCHEMA' }
        ]
    },
    {
        id: 'sch_05', key: 'app_launch', name: 'App 启动', kind: 'ATOMIC', description: '应用冷启动或从后台唤醒。', domain: 'Tech', status: 'active', owner: 'Client Team', usageCount: 900,
        standardProperties: []
    }
];

const MOCK_TRACKING_POINTS: TrackingPoint[] = [
    {
        id: 'tp_01', schemaId: 'sch_01', schemaName: '订单支付成功', viewPath: '/checkout/success_page', platform: 'Web', triggerType: 'System', triggerDescription: 'Page load after redirect from payment gateway.', status: 'implemented',
        contextProperties: [
            { id: 'cp_1', name: 'page_load_time', displayName: '页面加载耗时', type: 'NUMBER', isRequired: false, source: 'CONTEXT' }
        ]
    },
    {
        id: 'tp_02', schemaId: 'sch_01', schemaName: '订单支付成功', viewPath: 'OrderSuccessViewController', platform: 'App', triggerType: 'System', triggerDescription: 'Callback from Alipay SDK.', status: 'tested',
        contextProperties: [
            { id: 'cp_2', name: 'network_type', displayName: '网络类型', type: 'STRING', isRequired: true, source: 'CONTEXT' }
        ]
    }
];

const PROPERTY_LIBRARY: DataProperty[] = [
    { id: 'lib_1', name: 'element_position', displayName: '元素位置', type: 'NUMBER', isRequired: false, source: 'CONTEXT' },
    { id: 'lib_2', name: 'referral_source', displayName: '来源页面', type: 'STRING', isRequired: false, source: 'CONTEXT' },
    { id: 'lib_3', name: 'ab_test_group', displayName: '实验分组', type: 'STRING', isRequired: true, source: 'CONTEXT' },
];

// --- Components ---

const TypeBadge = ({ type }: { type: PropertyType }) => {
    const styles = {
        'STRING': 'text-blue-600 bg-blue-50 border-blue-100',
        'NUMBER': 'text-green-600 bg-green-50 border-green-100',
        'BOOL': 'text-purple-600 bg-purple-50 border-purple-100',
        'LIST': 'text-orange-600 bg-orange-50 border-orange-100',
        'DATETIME': 'text-gray-600 bg-gray-50 border-gray-200',
        'OBJECT': 'text-indigo-600 bg-indigo-50 border-indigo-100',
    }[type];
    return <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold border font-mono ${styles}`}>{type}</span>;
};

const SectionHeader = ({ icon: Icon, title, step }: { icon: any, title: string, step?: string }) => (
    <div className="flex items-center">
        {step && (
            <div className="w-5 h-5 rounded-full bg-gray-900 text-white flex items-center justify-center text-[10px] font-bold mr-2.5 shadow-sm">
                {step}
            </div>
        )}
        {!step && (
            <div className="w-6 h-6 rounded bg-gray-100 flex items-center justify-center mr-2.5 text-gray-500">
                <Icon size={14} />
            </div>
        )}
        <h4 className="text-xs font-bold text-gray-900 uppercase tracking-wide">{title}</h4>
    </div>
);

// --- Helper Components for Drawer ---

const PropertyList = ({ properties, onRemove, mode, emptyText = "No properties defined." }: any) => (
    <>
        {properties.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-gray-400 py-8">
                <ListChecks size={32} className="mb-2 opacity-20" />
                <p className="text-sm text-center px-8">{emptyText}</p>
            </div>
        ) : (
            <div className="space-y-3">
                {properties.map((prop: DataProperty, idx: number) => (
                    <div key={idx} className={`flex items-center justify-between p-3 bg-white border rounded-xl shadow-sm group ${
                        prop.source === 'SCHEMA' ? 'border-purple-100 bg-purple-50/10' : 'border-gray-200'
                    }`}>
                        <div className="flex items-center">
                            <div className={`w-8 h-8 rounded-lg flex items-center justify-center mr-3 font-mono text-xs font-bold ${
                                prop.source === 'SCHEMA' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-600'
                            }`}>
                                {idx + 1}
                            </div>
                            <div>
                                <div className="flex items-center">
                                    <span className="text-sm font-bold text-gray-900 mr-2">{prop.name}</span>
                                    <TypeBadge type={prop.type} />
                                    {prop.source === 'SCHEMA' && <span className="ml-2 text-[9px] bg-purple-100 text-purple-700 px-1.5 py-0.5 rounded border border-purple-200">Inherited</span>}
                                </div>
                                <div className="text-xs text-gray-500 mt-0.5">{prop.displayName}</div>
                            </div>
                        </div>
                        
                        {(mode === 'SCHEMA' || prop.source === 'CONTEXT') && (
                            <button 
                                onClick={() => onRemove(idx)}
                                className="opacity-0 group-hover:opacity-100 p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-all"
                            >
                                <Trash2 size={14} />
                            </button>
                        )}
                        {mode === 'TRACKING' && prop.source === 'SCHEMA' && (
                            <Lock size={14} className="text-gray-300 mr-2" />
                        )}
                    </div>
                ))}
            </div>
        )}
    </>
);

const PropertyAdder = ({ onAdd }: { onAdd: (p: DataProperty) => void }) => (
    <div className="p-4 border-t border-gray-200 bg-white">
        <div className="text-[10px] font-bold text-gray-400 uppercase mb-2">Add Property</div>
        <div className="flex space-x-2 overflow-x-auto pb-2 custom-scrollbar">
            {PROPERTY_LIBRARY.map(libProp => (
                <button
                    key={libProp.id}
                    onClick={() => onAdd(libProp)}
                    className="flex-shrink-0 flex items-center px-3 py-1.5 border border-gray-200 rounded-lg hover:border-gray-300 hover:bg-gray-50 transition-all text-left group min-w-[120px]"
                >
                    <Plus size={10} className="text-gray-400 mr-2 group-hover:text-gray-600" />
                    <div>
                        <div className="text-xs font-bold text-gray-700">{libProp.name}</div>
                        <div className="text-[9px] text-gray-400">{libProp.displayName}</div>
                    </div>
                </button>
            ))}
            <button className="flex-shrink-0 flex items-center justify-center px-3 py-1.5 border border-dashed border-gray-300 rounded-lg text-gray-400 hover:text-gray-600 hover:border-gray-400 transition-all text-xs font-bold min-w-[80px]">
                <Plus size={12} className="mr-1" /> Custom
            </button>
        </div>
    </div>
);

// --- DRAWER: Unified but Mode-Switched ---
const EventEditorDrawer = ({ 
    isOpen, 
    onClose, 
    mode,
    initialData = null
}: { 
    isOpen: boolean, 
    onClose: () => void, 
    mode: 'SCHEMA' | 'TRACKING',
    initialData?: any 
}) => {
    // Shared State
    const [properties, setProperties] = useState<DataProperty[]>([]);
    
    // Schema Mode State
    const [schemaKind, setSchemaKind] = useState<SchemaKind>('ATOMIC');
    const [schemaKey, setSchemaKey] = useState('');
    const [schemaName, setSchemaName] = useState('');
    
    // --- Composite Editor State (NEW) ---
    const [compositeTab, setCompositeTab] = useState<'LOGIC' | 'PROPERTIES'>('LOGIC');
    
    // --- Logic State ---
    const [topLevelOperator, setTopLevelOperator] = useState<'AND' | 'OR'>('AND'); // NEW: Global operator between groups
    const [logicGroups, setLogicGroups] = useState<LogicGroup[]>([
        { id: 'g_1', operator: 'OR', schemaIds: [] } // Default Group 1
    ]);
    
    // Drag & Drop State
    const [dragOverGroupId, setDragOverGroupId] = useState<string | null>(null);

    // Tracking Mode State (5W1H)
    const [selectedSchemaId, setSelectedSchemaId] = useState('');
    const [viewPath, setViewPath] = useState(''); 
    const [platform, setPlatform] = useState<'Web'|'App'|'Server'>('Web'); 

    // --- Helpers for Logic Builder ---
    const addGroup = () => {
        setLogicGroups([...logicGroups, { id: `g_${Date.now()}`, operator: 'OR', schemaIds: [] }]);
    };

    const removeGroup = (groupId: string) => {
        if (logicGroups.length <= 1) {
            setLogicGroups([{ id: groupId, operator: 'OR', schemaIds: [] }]); // Reset if last one
        } else {
            setLogicGroups(logicGroups.filter(g => g.id !== groupId));
        }
    };

    const toggleGroupOperator = (groupId: string) => {
        setLogicGroups(groups => groups.map(g => {
            if (g.id === groupId) {
                return { ...g, operator: g.operator === 'OR' ? 'AND' : 'OR' };
            }
            return g;
        }));
    };

    const addItemToGroup = (groupId: string, schemaId: string) => {
        setLogicGroups(groups => groups.map(g => {
            if (g.id === groupId && !g.schemaIds.includes(schemaId)) {
                return { ...g, schemaIds: [...g.schemaIds, schemaId] };
            }
            return g;
        }));
    };

    const removeItemFromGroup = (groupId: string, schemaId: string) => {
        setLogicGroups(groups => groups.map(g => {
            if (g.id === groupId) {
                return { ...g, schemaIds: g.schemaIds.filter(id => id !== schemaId) };
            }
            return g;
        }));
    };

    const handleDragStart = (e: React.DragEvent, schemaId: string) => {
        e.dataTransfer.setData("schemaId", schemaId);
        e.dataTransfer.effectAllowed = "copy";
    };

    const handleDragOverGroup = (e: React.DragEvent, groupId: string) => {
        e.preventDefault();
        setDragOverGroupId(groupId);
    };

    const handleDropOnGroup = (e: React.DragEvent, groupId: string) => {
        e.preventDefault();
        setDragOverGroupId(null);
        const schemaId = e.dataTransfer.getData("schemaId");
        if (schemaId) {
            addItemToGroup(groupId, schemaId);
        }
    };

    // --- General Helpers ---
    const handleSelectSchema = (schema: EventSchema) => {
        setSelectedSchemaId(schema.id);
        setProperties(schema.standardProperties.map(p => ({ ...p, source: 'SCHEMA' })));
    };

    const addContextProperty = (prop: DataProperty) => {
        setProperties([...properties, { ...prop, source: 'CONTEXT' }]);
    };

    const removeProperty = (index: number) => {
        const newProps = [...properties];
        newProps.splice(index, 1);
        setProperties(newProps);
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[60] flex justify-end">
            <div className="absolute inset-0 bg-gray-900/30 backdrop-blur-sm transition-opacity" onClick={onClose} />
            <div className="relative w-full max-w-[1200px] bg-white h-full shadow-2xl flex flex-col animate-in slide-in-from-right duration-300 border-l border-gray-100 font-sans">
                
                {/* Header */}
                <div className={`h-16 px-6 border-b flex justify-between items-center flex-shrink-0 z-10 ${mode === 'SCHEMA' ? 'bg-purple-50 border-purple-100' : 'bg-emerald-50 border-emerald-100'}`}>
                    <div className="flex items-center space-x-3">
                        <div className={`p-2 rounded-lg text-white ${mode === 'SCHEMA' ? 'bg-purple-600' : 'bg-emerald-600'}`}>
                            {mode === 'SCHEMA' ? <Book size={20} /> : <MousePointerClick size={20} />}
                        </div>
                        <div>
                            <h2 className={`text-lg font-bold ${mode === 'SCHEMA' ? 'text-purple-900' : 'text-emerald-900'}`}>
                                {mode === 'SCHEMA' ? 'Define Meta Event (Schema)' : 'New Tracking Point'}
                            </h2>
                            <p className={`text-xs ${mode === 'SCHEMA' ? 'text-purple-600' : 'text-emerald-600'}`}>
                                {mode === 'SCHEMA' ? 'Define reusable event standards.' : 'Implement tracking based on 5W1H.'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-3">
                        <button className={`flex items-center px-4 py-2 text-white text-xs font-bold rounded-lg shadow-sm transition-all ${mode === 'SCHEMA' ? 'bg-purple-600 hover:bg-purple-700' : 'bg-emerald-600 hover:bg-emerald-700'}`}>
                            <Save size={14} className="mr-1.5" /> 
                            {mode === 'SCHEMA' ? 'Save Schema' : 'Save Tracking'}
                        </button>
                        <button onClick={onClose} className="p-2 text-gray-400 hover:text-gray-600 hover:bg-white/50 rounded-lg transition-colors">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-hidden flex flex-col bg-[#F9FAFB]">
                    
                    {/* [TRACKING MODE ONLY] TOP SECTION */}
                    {mode === 'TRACKING' && (
                        <div className="px-6 py-4 bg-white border-b border-gray-200 flex-shrink-0">
                            <SectionHeader icon={User} title="1. Who (Subject)" />
                            <div className="pl-8">
                                <div className="flex items-center space-x-4 bg-gray-50 border border-gray-200 rounded-lg p-3">
                                    <div className="flex items-center space-x-2">
                                        <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-gray-500">
                                            <Fingerprint size={16} />
                                        </div>
                                        <div className="flex flex-col">
                                            <span className="text-xs font-bold text-gray-900">User Identity (SDK Managed)</span>
                                            <span className="text-[10px] text-gray-500">Auto-collected: user_id, device_id, session_id</span>
                                        </div>
                                    </div>
                                    <div className="h-6 w-px bg-gray-200"></div>
                                    <span className="text-[10px] text-emerald-600 bg-emerald-50 px-2 py-1 rounded-full font-medium">
                                        Always Present
                                    </span>
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="flex-1 overflow-hidden flex">
                        
                        {/* LEFT COLUMN: CONTEXT or IDENTITY */}
                        <div className="w-[360px] border-r border-gray-200 flex flex-col bg-white overflow-y-auto custom-scrollbar">
                            <div className="p-6 space-y-8">
                                
                                {/* --- SCHEMA MODE: IDENTITY --- */}
                                {mode === 'SCHEMA' && (
                                    <>
                                        {/* Identity Card */}
                                        <div className={`bg-white border rounded-xl p-5 shadow-[0_2px_12px_-4px_rgba(0,0,0,0.05)] relative overflow-hidden group ${schemaKind === 'ATOMIC' ? 'border-purple-100 shadow-[rgba(147,51,234,0.05)]' : 'border-amber-100 shadow-[rgba(245,158,11,0.05)]'}`}>
                                            <div className={`absolute top-0 right-0 w-24 h-24 bg-gradient-to-br to-transparent rounded-bl-full opacity-50 pointer-events-none ${schemaKind === 'ATOMIC' ? 'from-purple-50' : 'from-amber-50'}`}></div>
                                            
                                            <div className="flex items-center justify-between mb-4 relative z-10">
                                                <div className="flex items-center space-x-2">
                                                    <div className={`p-1.5 rounded-lg ${schemaKind === 'ATOMIC' ? 'bg-purple-50 text-purple-600' : 'bg-amber-50 text-amber-600'}`}>
                                                        {schemaKind === 'ATOMIC' ? <Hash size={14} /> : <Combine size={14} />}
                                                    </div>
                                                    <h4 className="text-xs font-bold text-gray-900 uppercase tracking-wider">Event Identity</h4>
                                                </div>
                                            </div>

                                            {/* Type Switcher */}
                                            <div className="bg-gray-100/80 p-1 rounded-lg flex mb-5 relative z-10">
                                                <button 
                                                    onClick={() => { setSchemaKind('ATOMIC'); setLogicGroups([{id:'g_1', operator: 'OR', schemaIds:[]}]); setCompositeTab('LOGIC'); }}
                                                    className={`flex-1 flex items-center justify-center py-1.5 text-[10px] font-bold rounded-md transition-all ${schemaKind === 'ATOMIC' ? 'bg-white text-purple-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                                >
                                                    <Zap size={12} className="mr-1.5" /> Atomic Event
                                                </button>
                                                <button 
                                                    onClick={() => setSchemaKind('COMPOSITE')}
                                                    className={`flex-1 flex items-center justify-center py-1.5 text-[10px] font-bold rounded-md transition-all ${schemaKind === 'COMPOSITE' ? 'bg-white text-amber-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                                >
                                                    <Combine size={12} className="mr-1.5" /> Composite Event
                                                </button>
                                            </div>

                                            <div className="space-y-4 relative z-10">
                                                <div>
                                                    <label className="block text-[10px] font-bold text-gray-500 uppercase mb-1.5">Event Key</label>
                                                    <div className="relative group/input">
                                                        <div className="absolute left-3 top-2.5 text-gray-400">
                                                            <Terminal size={14} />
                                                        </div>
                                                        <input 
                                                            type="text" 
                                                            value={schemaKey}
                                                            onChange={e => setSchemaKey(e.target.value)}
                                                            placeholder={schemaKind === 'ATOMIC' ? "e.g. order_paid_success" : "e.g. high_value_conversion"}
                                                            className={`w-full pl-9 pr-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm font-mono font-bold text-gray-800 transition-all outline-none focus:ring-4 ${schemaKind === 'ATOMIC' ? 'focus:border-purple-500 focus:ring-purple-500/10' : 'focus:border-amber-500 focus:ring-amber-500/10'}`}
                                                        />
                                                    </div>
                                                </div>
                                                <div>
                                                    <label className="block text-[10px] font-bold text-gray-500 uppercase mb-1.5">Display Name</label>
                                                    <div className="relative group/input">
                                                        <div className="absolute left-3 top-2.5 text-gray-400">
                                                            <Type size={14} />
                                                        </div>
                                                        <input 
                                                            type="text" 
                                                            value={schemaName}
                                                            onChange={e => setSchemaName(e.target.value)}
                                                            placeholder="e.g. Order Payment Success"
                                                            className={`w-full pl-9 pr-3 py-2 bg-white border border-gray-200 rounded-lg text-sm font-medium text-gray-900 transition-all outline-none ${schemaKind === 'ATOMIC' ? 'focus:border-purple-500 focus:ring-4 focus:ring-purple-500/10' : 'focus:border-amber-500 focus:ring-4 focus:ring-amber-500/10'}`}
                                                        />
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </>
                                )}

                                {/* --- TRACKING MODE: CONTEXT --- */}
                                {mode === 'TRACKING' && (
                                    <>
                                        {/* 2. WHERE */}
                                        <div className="relative">
                                            <div className="absolute left-3 top-8 bottom-0 w-px bg-gray-100"></div>
                                            <SectionHeader icon={MapPin} title="2. Where (Location)" />
                                            <div className="pl-8 space-y-3">
                                                <div className="flex rounded-lg bg-gray-100 p-1">
                                                    {['Web', 'App', 'Server'].map(p => (
                                                        <button 
                                                            key={p}
                                                            onClick={() => setPlatform(p as any)}
                                                            className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${platform === p ? 'bg-white text-emerald-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                                        >
                                                            {p}
                                                        </button>
                                                    ))}
                                                </div>
                                                <input 
                                                    type="text" 
                                                    value={viewPath}
                                                    onChange={e => setViewPath(e.target.value)}
                                                    placeholder={platform === 'Web' ? 'e.g. /checkout/success' : 'e.g. PaymentSuccessViewController'}
                                                    className="w-full px-3 py-2.5 bg-white border border-gray-200 rounded-lg text-sm font-mono focus:border-emerald-500 outline-none shadow-sm"
                                                />
                                            </div>
                                        </div>
                                    </>
                                )}
                            </div>
                        </div>

                        {/* RIGHT COLUMN: CONTENT (The Canvas) */}
                        <div className="flex-1 flex flex-col bg-gray-50">
                            
                            {/* --- SCHEMA MODE: STRUCTURE --- */}
                            {mode === 'SCHEMA' && (
                                <div className="flex flex-col h-full">
                                    <div className="p-4 border-b border-gray-200 bg-white flex-shrink-0">
                                        <div className="flex justify-between items-center">
                                            <div>
                                                {schemaKind === 'ATOMIC' ? (
                                                    <SectionHeader icon={Table} title="3. Data Structure (Properties)" />
                                                ) : (
                                                    <div className="flex items-center space-x-4">
                                                        <SectionHeader icon={Workflow} title="3. Event Definition" />
                                                    </div>
                                                )}
                                                {schemaKind === 'ATOMIC' && (
                                                    <p className="text-[10px] text-gray-400 pl-8">Define the standard payload for this event.</p>
                                                )}
                                            </div>
                                            
                                            {/* Composite Tabs Switcher */}
                                            {schemaKind === 'COMPOSITE' && (
                                                <div className="flex p-1 bg-gray-100 rounded-lg">
                                                    <button 
                                                        onClick={() => setCompositeTab('LOGIC')}
                                                        className={`px-3 py-1.5 text-[10px] font-bold rounded-md transition-all flex items-center ${compositeTab === 'LOGIC' ? 'bg-white text-amber-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                                    >
                                                        <Workflow size={12} className="mr-1.5" /> Logic Rules
                                                    </button>
                                                    <button 
                                                        onClick={() => setCompositeTab('PROPERTIES')}
                                                        className={`px-3 py-1.5 text-[10px] font-bold rounded-md transition-all flex items-center ${compositeTab === 'PROPERTIES' ? 'bg-white text-amber-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                                    >
                                                        <ListChecks size={12} className="mr-1.5" /> Output Properties
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                    
                                    <div className="flex-1 overflow-hidden flex flex-col">
                                        {schemaKind === 'ATOMIC' ? (
                                            /* Atomic Property List */
                                            <div className="flex-1 p-6 overflow-y-auto">
                                                <PropertyList 
                                                    properties={properties} 
                                                    onRemove={removeProperty} 
                                                    mode={mode} 
                                                />
                                                <PropertyAdder onAdd={addContextProperty} />
                                            </div>
                                        ) : (
                                            /* COMPOSITE MODE: TABBED CONTENT */
                                            <>
                                                {/* TAB 1: LOGIC BUILDER */}
                                                {compositeTab === 'LOGIC' && (
                                                    <div className="flex flex-1 overflow-hidden">
                                                        {/* Assets Palette */}
                                                        <div className="w-[260px] border-r border-gray-200 bg-white flex flex-col">
                                                            <div className="p-3 border-b border-gray-100 bg-gray-50/50">
                                                                <div className="relative">
                                                                    <Search size={12} className="absolute left-2.5 top-2 text-gray-400" />
                                                                    <input 
                                                                        type="text" 
                                                                        placeholder="Search atomic events..." 
                                                                        className="w-full pl-8 pr-3 py-1.5 bg-white border border-gray-200 rounded-lg text-xs outline-none focus:border-amber-400"
                                                                    />
                                                                </div>
                                                            </div>
                                                            <div className="flex-1 overflow-y-auto p-3 space-y-2">
                                                                <div className="text-[9px] font-bold text-gray-400 uppercase tracking-wider mb-2 px-1">Atomic Events</div>
                                                                {MOCK_SCHEMAS.filter(s => s.kind === 'ATOMIC').map(source => (
                                                                    <div 
                                                                        key={source.id}
                                                                        draggable
                                                                        onDragStart={(e) => handleDragStart(e, source.id)}
                                                                        className="flex items-center bg-white border border-gray-200 p-2.5 rounded-lg shadow-sm cursor-grab hover:border-blue-400 hover:shadow-md transition-all group active:cursor-grabbing select-none"
                                                                    >
                                                                        <div className="w-6 h-6 rounded bg-blue-50 text-blue-600 flex items-center justify-center mr-2.5 border border-blue-100">
                                                                            <Zap size={14} />
                                                                        </div>
                                                                        <div className="flex-1 min-w-0">
                                                                            <div className="text-xs font-bold text-gray-700 truncate group-hover:text-blue-700">{source.name}</div>
                                                                            <div className="text-[9px] text-gray-400 font-mono truncate">{source.key}</div>
                                                                        </div>
                                                                        <GripVertical size={12} className="text-gray-300 group-hover:text-blue-400" />
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>

                                                        {/* Logic Canvas */}
                                                        <div className="flex-1 flex flex-col bg-[#FAFAFA] relative overflow-auto p-8 custom-scrollbar">
                                                            <div className="absolute inset-0 pointer-events-none opacity-[0.6]" style={{ backgroundImage: 'radial-gradient(#E5E7EB 1px, transparent 1px)', backgroundSize: '24px 24px' }}></div>

                                                            <div className="relative z-10 max-w-4xl mx-auto w-full space-y-4 pb-20">
                                                                {/* Logic Info */}
                                                                <div className="flex items-center justify-between mb-6">
                                                                    <div className="flex items-center space-x-2">
                                                                        <div className="w-8 h-8 rounded-lg bg-amber-100 text-amber-600 flex items-center justify-center border border-amber-200 shadow-sm">
                                                                            <FunctionSquare size={18} />
                                                                        </div>
                                                                        <div>
                                                                            <h3 className="text-sm font-bold text-gray-900">Trigger Logic</h3>
                                                                            <div className="flex items-center text-[10px] text-gray-500 mt-0.5">
                                                                                <span>Trigger when</span>
                                                                                <button 
                                                                                    onClick={() => setTopLevelOperator(prev => prev === 'AND' ? 'OR' : 'AND')}
                                                                                    className={`mx-1.5 px-2 py-0.5 rounded font-bold border transition-all ${
                                                                                        topLevelOperator === 'AND' 
                                                                                        ? 'bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100' 
                                                                                        : 'bg-amber-50 text-amber-700 border-amber-200 hover:bg-amber-100'
                                                                                    }`}
                                                                                >
                                                                                    {topLevelOperator === 'AND' ? 'ALL (AND)' : 'ANY (OR)'}
                                                                                </button>
                                                                                <span>groups match.</span>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                </div>

                                                                {/* Groups */}
                                                                {logicGroups.map((group, idx) => {
                                                                    const isLast = idx === logicGroups.length - 1;
                                                                    const isOR = group.operator === 'OR';
                                                                    const groupColor = isOR ? 'amber' : 'blue';
                                                                    
                                                                    return (
                                                                        <React.Fragment key={group.id}>
                                                                            <div 
                                                                                className={`
                                                                                    relative bg-white border-2 rounded-2xl overflow-hidden shadow-sm transition-all duration-300 flex
                                                                                    ${dragOverGroupId === group.id ? `border-${groupColor}-400 bg-${groupColor}-50/10 shadow-lg scale-[1.01]` : 'border-gray-200 hover:border-gray-300'}
                                                                                `}
                                                                                onDragOver={(e) => handleDragOverGroup(e, group.id)}
                                                                                onDragLeave={() => setDragOverGroupId(null)}
                                                                                onDrop={(e) => handleDropOnGroup(e, group.id)}
                                                                            >
                                                                                <div className={`w-14 flex-shrink-0 flex flex-col items-center py-4 border-r ${isOR ? 'bg-amber-50 border-amber-100' : 'bg-blue-50 border-blue-100'}`}>
                                                                                    <button 
                                                                                        onClick={() => toggleGroupOperator(group.id)}
                                                                                        className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-xs shadow-sm transition-all border ${
                                                                                            isOR ? 'bg-white text-amber-600 border-amber-200 hover:bg-amber-100' : 'bg-white text-blue-600 border-blue-200 hover:bg-blue-100'
                                                                                        }`}
                                                                                        title="Click to toggle logic"
                                                                                    >
                                                                                        {group.operator}
                                                                                    </button>
                                                                                    <div className={`flex-1 w-0.5 my-2 rounded-full ${isOR ? 'bg-amber-200' : 'bg-blue-200'}`}></div>
                                                                                </div>

                                                                                <div className="flex-1 p-6">
                                                                                    <div className="flex justify-between items-start mb-4">
                                                                                        <span className="text-xs font-bold text-gray-500">Condition Group {idx + 1}</span>
                                                                                        <button onClick={() => removeGroup(group.id)} className="text-gray-300 hover:text-red-500 p-1 transition-colors rounded hover:bg-gray-100">
                                                                                            <Trash2 size={14} />
                                                                                        </button>
                                                                                    </div>

                                                                                    <div className="flex flex-wrap gap-3 items-center">
                                                                                        {group.schemaIds.length === 0 ? (
                                                                                            <div className="w-full border-2 border-dashed border-gray-100 rounded-xl py-6 flex flex-col items-center justify-center text-gray-400 bg-gray-50/50">
                                                                                                <span className="text-xs">Drag atomic events here</span>
                                                                                            </div>
                                                                                        ) : (
                                                                                            group.schemaIds.map((sid, sIdx) => {
                                                                                                const schema = MOCK_SCHEMAS.find(s => s.id === sid);
                                                                                                return (
                                                                                                    <React.Fragment key={`${sid}-${sIdx}`}>
                                                                                                        <div className={`flex items-center bg-white border rounded-lg p-2 shadow-sm group hover:shadow-md transition-all min-w-[140px] ${isOR ? 'border-gray-200 hover:border-amber-300' : 'border-gray-200 hover:border-blue-300'}`}>
                                                                                                            <div className={`p-1.5 rounded mr-2 ${isOR ? 'bg-amber-50 text-amber-600' : 'bg-blue-50 text-blue-600'}`}>
                                                                                                                <Zap size={12} />
                                                                                                            </div>
                                                                                                            <div className="flex-1 min-w-0 mr-2">
                                                                                                                <div className="text-[11px] font-bold text-gray-800 truncate">{schema?.name}</div>
                                                                                                                <div className="text-[9px] text-gray-400 font-mono truncate">{schema?.key}</div>
                                                                                                            </div>
                                                                                                            <button onClick={() => removeItemFromGroup(group.id, sid)} className="text-gray-300 hover:text-red-500 opacity-0 group-hover:opacity-100 transition-opacity">
                                                                                                                <X size={12} />
                                                                                                            </button>
                                                                                                        </div>
                                                                                                        {sIdx < group.schemaIds.length - 1 && (
                                                                                                            <div className={`text-[9px] font-bold uppercase ${isOR ? 'text-amber-400' : 'text-blue-400'}`}>{group.operator}</div>
                                                                                                        )}
                                                                                                    </React.Fragment>
                                                                                                );
                                                                                            })
                                                                                        )}
                                                                                    </div>
                                                                                </div>
                                                                            </div>

                                                                            {!isLast && (
                                                                                <div className="flex justify-center py-2 relative">
                                                                                    <div className={`absolute top-0 bottom-0 left-1/2 w-0.5 ${topLevelOperator === 'AND' ? 'bg-blue-100' : 'bg-amber-100'}`}></div>
                                                                                    <div className={`relative z-10 px-3 py-1 text-[10px] font-bold rounded-full border uppercase shadow-sm ${topLevelOperator === 'AND' ? 'bg-white text-blue-500 border-blue-100' : 'bg-white text-amber-500 border-amber-100'}`}>
                                                                                        {topLevelOperator}
                                                                                    </div>
                                                                                </div>
                                                                            )}
                                                                        </React.Fragment>
                                                                    );
                                                                })}

                                                                <div className="flex justify-center pt-4">
                                                                    <button 
                                                                        onClick={addGroup}
                                                                        className={`flex items-center px-4 py-2 bg-white border-2 border-dashed rounded-full text-xs font-bold transition-all shadow-sm hover:shadow-md group ${topLevelOperator === 'AND' ? 'border-blue-200 text-blue-500 hover:border-blue-400' : 'border-amber-200 text-amber-500 hover:border-amber-400'}`}
                                                                    >
                                                                        <Plus size={14} className="mr-1.5 group-hover:scale-110 transition-transform" /> Add Condition Group
                                                                    </button>
                                                                </div>

                                                                {/* NEXT STEP ACTION */}
                                                                <div className="mt-12 flex justify-center">
                                                                    <button 
                                                                        onClick={() => setCompositeTab('PROPERTIES')}
                                                                        className="flex items-center px-6 py-2.5 bg-amber-600 text-white text-sm font-bold rounded-xl shadow-lg hover:bg-amber-700 transition-all transform hover:-translate-y-0.5"
                                                                    >
                                                                        Next: Define Output Properties <ArrowRight size={16} className="ml-2" />
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}

                                                {/* TAB 2: PROPERTIES */}
                                                {compositeTab === 'PROPERTIES' && (
                                                    <div className="flex-1 overflow-hidden flex flex-col bg-gray-50/50 animate-in fade-in slide-in-from-right-4 duration-300">
                                                        <div className="flex-1 overflow-y-auto p-8 max-w-4xl mx-auto w-full">
                                                            <div className="bg-amber-50 border border-amber-100 rounded-xl p-4 mb-6 flex items-start">
                                                                <Info size={16} className="text-amber-600 mt-0.5 mr-3 flex-shrink-0" />
                                                                <div>
                                                                    <h4 className="text-sm font-bold text-amber-800">Composite Event Contract</h4>
                                                                    <p className="text-xs text-amber-700 mt-1 leading-relaxed">
                                                                        Define the properties that will be available in the final output of this composite event.
                                                                        <br/>These can be static values or derived from the underlying atomic events.
                                                                    </p>
                                                                </div>
                                                            </div>

                                                            <div className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden mb-6">
                                                                <div className="p-4 border-b border-gray-100 flex justify-between items-center bg-gray-50/30">
                                                                    <h3 className="text-sm font-bold text-gray-900 flex items-center">
                                                                        <ListChecks size={16} className="mr-2 text-gray-500" />
                                                                        Output Properties
                                                                    </h3>
                                                                    <span className="text-[10px] text-gray-400 bg-white px-2 py-0.5 rounded border border-gray-200">
                                                                        {properties.length} Defined
                                                                    </span>
                                                                </div>
                                                                <div className="p-6">
                                                                    <PropertyList 
                                                                        properties={properties} 
                                                                        onRemove={removeProperty} 
                                                                        mode={mode} 
                                                                        emptyText="No properties defined yet. Use the tool below to add properties."
                                                                    />
                                                                </div>
                                                            </div>

                                                            <PropertyAdder onAdd={addContextProperty} />
                                                            
                                                            <div className="mt-8 flex justify-between items-center">
                                                                <button 
                                                                    onClick={() => setCompositeTab('LOGIC')}
                                                                    className="text-xs font-bold text-gray-500 hover:text-gray-900 flex items-center px-4 py-2"
                                                                >
                                                                    <ChevronRight size={14} className="mr-1 rotate-180" /> Back to Logic
                                                                </button>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )}
                                            </>
                                        )}
                                    </div>
                                    
                                    {schemaKind === 'ATOMIC' && <PropertyAdder onAdd={addContextProperty} />}
                                </div>
                            )}

                            {/* --- TRACKING MODE: CONTENT --- */}
                            {mode === 'TRACKING' && (
                                <div className="flex flex-col h-full">
                                    {/* 5. WHAT (Schema Selection) */}
                                    <div className="p-5 border-b border-gray-200 bg-white">
                                        <SectionHeader icon={Activity} title="5. What (Event Schema)" />
                                        <div className="pl-8 mt-2">
                                            <div className="relative group">
                                                <Search size={14} className="absolute left-3 top-3 text-gray-400 group-focus-within:text-emerald-500" />
                                                <input 
                                                    type="text"
                                                    placeholder="Select Event Schema..."
                                                    className="w-full pl-9 pr-3 py-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm font-medium focus:bg-white focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500 outline-none transition-all"
                                                />
                                                {!selectedSchemaId && (
                                                    <div className="mt-2 space-y-1 max-h-32 overflow-y-auto border border-gray-100 rounded-lg p-1 bg-white shadow-sm">
                                                        {MOCK_SCHEMAS.map(s => (
                                                            <div 
                                                                key={s.id} 
                                                                onClick={() => handleSelectSchema(s)}
                                                                className="px-3 py-2 hover:bg-emerald-50 rounded-md cursor-pointer text-xs flex justify-between items-center group/item transition-all"
                                                            >
                                                                <div>
                                                                    <div className="flex items-center">
                                                                        {s.kind === 'COMPOSITE' && <Combine size={12} className="mr-1.5 text-amber-500" />}
                                                                        <span className={`font-bold ${s.kind === 'COMPOSITE' ? 'text-amber-700' : 'text-gray-700'}`}>{s.name}</span>
                                                                    </div>
                                                                    <div className="text-[10px] text-gray-400 font-mono mt-0.5">{s.key}</div>
                                                                </div>
                                                                {s.kind === 'COMPOSITE' && <span className="text-[9px] bg-amber-50 text-amber-600 px-1.5 py-0.5 rounded border border-amber-100">Virtual</span>}
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                                {selectedSchemaId && (
                                                    <div className="mt-2 p-3 bg-emerald-50 border border-emerald-100 rounded-lg flex justify-between items-center">
                                                        <div>
                                                            <div className="text-xs font-bold text-emerald-800">{MOCK_SCHEMAS.find(s=>s.id === selectedSchemaId)?.name}</div>
                                                            <div className="text-[10px] text-emerald-600 font-mono mt-0.5">{MOCK_SCHEMAS.find(s=>s.id === selectedSchemaId)?.key}</div>
                                                        </div>
                                                        <button onClick={() => { setSelectedSchemaId(''); setProperties([]); }} className="text-emerald-400 hover:text-emerald-700 p-1 hover:bg-emerald-100 rounded"><X size={14}/></button>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {/* 6. HOW (Payload Details) */}
                                    <div className="flex-1 flex flex-col overflow-hidden">
                                        <div className="p-4 border-b border-gray-200 bg-gray-50/50 flex justify-between items-center">
                                            <div className="pl-1">
                                                <SectionHeader icon={ListChecks} title="6. How (Data Payload)" />
                                            </div>
                                            <span className="text-[10px] bg-white border border-gray-200 px-2 py-0.5 rounded text-gray-500 font-mono">
                                                {properties.length} Props
                                            </span>
                                        </div>
                                        
                                        <div className="flex-1 overflow-y-auto p-6">
                                            <PropertyList 
                                                properties={properties} 
                                                onRemove={removeProperty} 
                                                mode={mode} 
                                                emptyText="Select a Schema to inherit properties, or add context properties below."
                                            />
                                        </div>

                                        <PropertyAdder onAdd={addContextProperty} />
                                    </div>
                                </div>
                            )}

                        </div>

                    </div>
                </div>
            </div>
        </div>
    );
};

export default function TrackingPage() {
  const [activeTab, setActiveTab] = useState<'PLAN' | 'LIBRARY'>('LIBRARY');
  
  // Drawer States
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'SCHEMA' | 'TRACKING'>('TRACKING');

  const openSchemaCreator = () => {
      setDrawerMode('SCHEMA');
      setDrawerOpen(true);
  };

  const openTrackingCreator = () => {
      setDrawerMode('TRACKING');
      setDrawerOpen(true);
  };

  return (
    <div className="flex flex-col h-full bg-[#F8F9FA] overflow-y-auto custom-scrollbar">
        
        {/* Header */}
        <div className="px-8 py-8 flex-shrink-0 bg-white border-b border-gray-200">
            <div className="flex justify-between items-end mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 flex items-center mb-2">
                        <Target className="mr-3 text-brand-600" size={28} />
                        数据埋点 (Data Tracking)
                    </h1>
                    <p className="text-gray-500 text-sm max-w-2xl leading-relaxed">
                        一站式管理 <span className="font-bold text-gray-700">事件模型 (Schema)</span> 与 <span className="font-bold text-gray-700">埋点方案 (Plan)</span>。
                        将业务动作抽象为标准事件，并在各端进行具体实施。
                    </p>
                </div>
                <div className="flex items-center space-x-3">
                    {/* Context Aware Buttons */}
                    <button 
                        onClick={openSchemaCreator}
                        className="flex items-center px-4 py-2.5 bg-white border border-purple-200 text-purple-700 text-xs font-bold rounded-xl shadow-sm hover:bg-purple-50 transition-all"
                    >
                        <Book size={14} className="mr-2" />
                        定义元事件 (Define Schema)
                    </button>
                    <button 
                        onClick={openTrackingCreator}
                        className="flex items-center px-5 py-2.5 bg-gray-900 text-white text-sm font-bold rounded-xl shadow-lg shadow-gray-200 hover:bg-black transition-all transform hover:-translate-y-0.5 active:scale-95"
                    >
                        <MousePointerClick size={16} className="mr-2" />
                        新增埋点 (Add Tracking)
                    </button>
                </div>
            </div>

            {/* Main Tabs */}
            <div className="flex space-x-1 bg-gray-100 p-1 rounded-xl w-fit">
                <button
                    onClick={() => setActiveTab('PLAN')}
                    className={`px-4 py-2 text-xs font-bold rounded-lg transition-all flex items-center ${activeTab === 'PLAN' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                >
                    <MapPin size={14} className="mr-2" />
                    埋点方案 (Tracking Plan)
                </button>
                <button
                    onClick={() => setActiveTab('LIBRARY')}
                    className={`px-4 py-2 text-xs font-bold rounded-lg transition-all flex items-center ${activeTab === 'LIBRARY' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                >
                    <Book size={14} className="mr-2" />
                    元事件库 (Event Library)
                </button>
            </div>
        </div>

        {/* Content Area */}
        <div className="p-8">
            
            {/* VIEW 1: TRACKING PLAN (Instances) */}
            {activeTab === 'PLAN' && (
                <div className="space-y-4">
                    {/* Table Header / Filters */}
                    <div className="flex items-center justify-between mb-4">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={14} />
                            <input 
                                type="text" 
                                placeholder="Search tracking points..." 
                                className="pl-9 pr-4 py-2 bg-white border border-gray-200 rounded-lg text-xs w-64 focus:border-emerald-500 outline-none"
                            />
                        </div>
                        <div className="flex space-x-2">
                            <button className="px-3 py-1.5 bg-white border border-gray-200 rounded-lg text-xs font-medium text-gray-600 hover:text-gray-900">
                                Filter by Page
                            </button>
                        </div>
                    </div>

                    {/* Tracking List */}
                    <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
                        <table className="w-full text-left">
                            <thead className="bg-gray-50 border-b border-gray-100">
                                <tr>
                                    <th className="px-6 py-4 text-[10px] font-bold text-gray-500 uppercase w-[30%]">Event (Schema)</th>
                                    <th className="px-6 py-4 text-[10px] font-bold text-gray-500 uppercase w-[25%]">Context (Where)</th>
                                    <th className="px-6 py-4 text-[10px] font-bold text-gray-500 uppercase w-[25%]">Trigger</th>
                                    <th className="px-6 py-4 text-[10px] font-bold text-gray-500 uppercase text-center w-[10%]">Status</th>
                                    <th className="px-6 py-4 text-[10px] font-bold text-gray-500 uppercase text-right">Action</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-50">
                                {MOCK_TRACKING_POINTS.map(tp => (
                                    <tr key={tp.id} className="group hover:bg-gray-50 transition-colors">
                                        <td className="px-6 py-4">
                                            <div className="flex items-center">
                                                <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg mr-3">
                                                    <MousePointer2 size={16} />
                                                </div>
                                                <div>
                                                    <div className="text-sm font-bold text-gray-900">{tp.schemaName}</div>
                                                    <div className="text-[10px] text-gray-400 font-mono mt-0.5">Schema: {tp.schemaId}</div>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="flex flex-col">
                                                <div className="flex items-center text-xs font-bold text-gray-700 mb-1">
                                                    {tp.platform === 'Web' && <Globe size={12} className="mr-1 text-blue-500" />}
                                                    {tp.platform === 'App' && <Smartphone size={12} className="mr-1 text-purple-500" />}
                                                    {tp.viewPath}
                                                </div>
                                                <div className="flex gap-1">
                                                    {tp.contextProperties.map(p => (
                                                        <span key={p.id} className="px-1.5 py-0.5 bg-gray-100 text-gray-500 text-[9px] rounded border border-gray-200">
                                                            {p.name}
                                                        </span>
                                                    ))}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="text-xs text-gray-600 leading-relaxed max-w-[200px]">
                                                {tp.triggerDescription}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-[9px] font-bold uppercase tracking-wide border ${
                                                tp.status === 'implemented' ? 'bg-green-50 text-green-700 border-green-100' :
                                                tp.status === 'tested' ? 'bg-blue-50 text-blue-700 border-blue-100' :
                                                'bg-gray-100 text-gray-500 border-gray-200'
                                            }`}>
                                                {tp.status}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <button className="text-gray-400 hover:text-gray-900 p-1.5 hover:bg-gray-100 rounded-lg transition-colors">
                                                <MoreHorizontal size={16} />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* VIEW 2: EVENT LIBRARY (Schemas) */}
            {activeTab === 'LIBRARY' && (
                <div className="space-y-4 animate-in fade-in slide-in-from-right-4 duration-300">
                    <div className="flex items-center justify-between mb-4">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={14} />
                            <input 
                                type="text" 
                                placeholder="Search event schemas..." 
                                className="pl-9 pr-4 py-2 bg-white border border-gray-200 rounded-lg text-xs w-64 focus:border-purple-500 outline-none"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-3 gap-6">
                        {MOCK_SCHEMAS.map(schema => {
                            // Dynamic Styles based on Domain
                            const domainColor = 
                                schema.domain === 'Trade' ? 'bg-blue-500' : 
                                schema.domain === 'User' ? 'bg-purple-500' : 
                                schema.domain === 'Marketing' ? 'bg-orange-500' : 'bg-gray-500';
                            
                            // Visual difference for Composite
                            const isComposite = schema.kind === 'COMPOSITE';

                            return (
                                <div key={schema.id} className={`bg-white border rounded-xl hover:shadow-xl transition-all group relative cursor-pointer overflow-hidden flex flex-col h-full ${isComposite ? 'border-amber-200 hover:border-amber-300' : 'border-gray-200 hover:border-purple-200'}`}>
                                    {/* Top Color Bar */}
                                    <div className={`h-1 w-full ${isComposite ? 'bg-amber-500' : domainColor}`}></div>
                                    
                                    <div className="p-5 flex-1 flex flex-col">
                                        <div className="flex justify-between items-start mb-3">
                                            <div className="flex items-center space-x-3">
                                                <div className={`p-2 rounded-lg ${isComposite ? 'bg-amber-50 text-amber-600' : 'bg-purple-50 text-purple-600'}`}>
                                                    {isComposite ? <Combine size={20} /> : <Book size={20} />}
                                                </div>
                                                <div>
                                                    <h3 className="text-sm font-bold text-gray-900 group-hover:text-purple-700 transition-colors">{schema.name}</h3>
                                                    <div className="text-[10px] text-gray-400 font-mono mt-0.5">{schema.key}</div>
                                                </div>
                                            </div>
                                            <span className={`text-[9px] px-2 py-1 rounded border font-medium ${isComposite ? 'bg-amber-50 text-amber-700 border-amber-100' : 'bg-gray-50 text-gray-500 border-gray-100'}`}>
                                                {isComposite ? 'COMPOSITE' : schema.domain}
                                            </span>
                                        </div>
                                        
                                        <p className="text-xs text-gray-500 leading-relaxed mb-4 line-clamp-2 h-10">
                                            {schema.description}
                                        </p>

                                        <div className="space-y-2 mt-auto">
                                            {isComposite ? (
                                                <>
                                                    <div className="flex items-center justify-between">
                                                        <div className="text-[9px] font-bold text-amber-500 uppercase">Composition Logic</div>
                                                    </div>
                                                    <div className="flex items-center space-x-2 bg-amber-50 p-2 rounded-lg border border-amber-100 text-xs text-amber-800 font-mono">
                                                        <FunctionSquare size={12} className="mr-1" />
                                                        Recursive Formula
                                                    </div>
                                                </>
                                            ) : (
                                                <>
                                                    <div className="flex items-center justify-between">
                                                        <div className="text-[9px] font-bold text-gray-400 uppercase">Standard Props</div>
                                                        <div className="text-[9px] text-gray-400 flex items-center bg-gray-50 px-1.5 py-0.5 rounded">
                                                            <MousePointerClick size={10} className="mr-1" />
                                                            {schema.usageCount} implementations
                                                        </div>
                                                    </div>
                                                    <div className="flex flex-wrap gap-1.5">
                                                        {schema.standardProperties.map(p => (
                                                            <span key={p.id} className="px-1.5 py-0.5 rounded border border-purple-100 bg-purple-50/50 text-purple-700 text-[9px] font-mono">
                                                                {p.name}
                                                            </span>
                                                        ))}
                                                    </div>
                                                </>
                                            )}
                                        </div>
                                    </div>

                                    <div className="absolute top-4 right-4 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button className={`p-1.5 text-gray-400 rounded-lg ${isComposite ? 'hover:text-amber-600 hover:bg-amber-50' : 'hover:text-purple-600 hover:bg-purple-50'}`}>
                                            <Settings size={14} />
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                        
                        {/* New Schema Card */}
                        <button 
                            onClick={openSchemaCreator}
                            className="border-2 border-dashed border-gray-200 rounded-xl p-5 flex flex-col items-center justify-center text-gray-400 hover:border-purple-300 hover:text-purple-600 hover:bg-purple-50/30 transition-all group"
                        >
                            <div className="w-12 h-12 bg-gray-50 rounded-full flex items-center justify-center mb-3 group-hover:bg-white group-hover:shadow-sm">
                                <Plus size={24} />
                            </div>
                            <span className="text-sm font-bold">Define New Schema</span>
                            <span className="text-xs mt-1 opacity-70">Create a reusable event template</span>
                        </button>
                    </div>
                </div>
            )}

        </div>

        {/* --- DRAWER --- */}
        <EventEditorDrawer 
            isOpen={drawerOpen} 
            onClose={() => setDrawerOpen(false)} 
            mode={drawerMode}
        />

    </div>
  );
}