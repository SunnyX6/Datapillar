import React, { useState } from 'react';
import { 
  Server, 
  Cpu, 
  Zap, 
  Database, 
  Box, 
  Activity, 
  Settings, 
  Plus, 
  X, 
  Check, 
  MoreHorizontal, 
  Layers, 
  Search,
  Link,
  Cloud,
  Shield,
  RefreshCw,
  Gauge,
  Network,
  AlertTriangle,
  Info,
  Terminal,
  Code2,
  TrendingUp,
  ArrowUpCircle,
  FileText,
  Users,
  Lock,
  Globe,
  Crown,
  Copy,
  Download,
  Clock
} from 'lucide-react';

// --- Domain Models ---

type ResourceType = 'EXTERNAL_K8S' | 'EXTERNAL_YARN' | 'SAAS_SERVERLESS';

interface EngineSpec {
  type: 'Flink' | 'Spark' | 'Hive' | 'Python';
  version: string;
  status: 'READY' | 'PROVISIONING' | 'ERROR';
}

interface QuotaRequest {
  targetCores: number;
  targetMemoryGB: number;
  reason: string;
  status: 'PENDING_APPROVAL';
  submittedAt: string;
}

interface TeamBinding {
  teamId: string;
  teamName: string;
  avatar: string; // Color code or initial
  priority: 'HIGH' | 'NORMAL' | 'LOW';
}

interface ComputeResource {
  id: string;
  name: string;
  type: ResourceType;
  description?: string;
  
  // Isolation Strategy
  isolationMode: 'GLOBAL_SHARED' | 'TEAM_DEDICATED';
  bindings: TeamBinding[]; // Allowed teams if DEDICATED

  // Connection Details
  connection: {
    endpoint: string;
    namespace?: string;
    queue?: string;
    status: 'CONNECTED' | 'DISCONNECTED' | 'UNAUTHORIZED';
    latency: string;
  };

  // Quota
  quota: {
    maxCores: number;
    maxMemoryGB: number;
    usedCores: number;
    usedMemoryGB: number;
  };

  pendingRequest?: QuotaRequest;
  runtimes: EngineSpec[];
}

// --- Mock Teams for Selection ---
const AVAILABLE_TEAMS = [
    { id: 't1', name: 'Core Data Team', avatar: 'bg-blue-500' },
    { id: 't2', name: 'Marketing Ops', avatar: 'bg-pink-500' },
    { id: 't3', name: 'Risk Control', avatar: 'bg-red-500' },
    { id: 't4', name: 'AI Lab', avatar: 'bg-purple-500' },
];

// --- Mock Data ---

const MOCK_RESOURCES: ComputeResource[] = [
  {
    id: 'res-001',
    name: 'YARN_Batch_Prod',
    type: 'EXTERNAL_YARN',
    description: '核心数仓离线计算队列，支持 Hive SQL 与 Spark 批处理。',
    isolationMode: 'TEAM_DEDICATED',
    bindings: [
        { teamId: 't1', teamName: 'Core Data Team', avatar: 'bg-blue-500', priority: 'HIGH' },
        { teamId: 't3', teamName: 'Risk Control', avatar: 'bg-red-500', priority: 'NORMAL' }
    ],
    connection: {
      endpoint: 'rm-01.corp.internal:8088',
      queue: 'root.prod.batch',
      status: 'CONNECTED',
      latency: '12ms'
    },
    quota: {
      maxCores: 2000,
      maxMemoryGB: 8192,
      usedCores: 1850,
      usedMemoryGB: 6000
    },
    runtimes: [
       { type: 'Spark', version: '3.3.2', status: 'READY' },
       { type: 'Hive', version: '3.1.3', status: 'READY' }
    ]
  },
  {
    id: 'res-002',
    name: 'K8s_Stream_Cluster_A',
    type: 'EXTERNAL_K8S',
    description: '用于实时风控与营销的流计算环境，部署了 Flink Native Operator。',
    isolationMode: 'TEAM_DEDICATED',
    bindings: [
        { teamId: 't2', teamName: 'Marketing Ops', avatar: 'bg-pink-500', priority: 'HIGH' }
    ],
    connection: {
      endpoint: 'api.k8s.us-east-1.aws.com',
      namespace: 'flink-streaming-ns',
      status: 'CONNECTED',
      latency: '45ms'
    },
    quota: {
      maxCores: 128,
      maxMemoryGB: 512,
      usedCores: 110,
      usedMemoryGB: 400
    },
    runtimes: [
       { type: 'Flink', version: '1.17.1', status: 'READY' }
    ]
  },
  {
    id: 'res-003',
    name: 'SaaS_Sandbox_Serverless',
    type: 'SAAS_SERVERLESS',
    description: '平台托管的弹性沙箱，适合探索性分析。',
    isolationMode: 'GLOBAL_SHARED',
    bindings: [], // Empty means accessible by all
    connection: {
      endpoint: 'managed.datapillar.io',
      status: 'CONNECTED',
      latency: '5ms'
    },
    quota: {
      maxCores: 16,
      maxMemoryGB: 64,
      usedCores: 2,
      usedMemoryGB: 8
    },
    runtimes: [
       { type: 'Spark', version: '3.5 (Serverless)', status: 'READY' },
       { type: 'Python', version: '3.11', status: 'READY' }
    ]
  }
];

const ResourceRow: React.FC<{ 
  resource: ComputeResource, 
  onScale: () => void, 
  onAccess: () => void 
}> = ({ 
  resource, 
  onScale, 
  onAccess 
}) => {
  const getTypeConfig = (type: ResourceType) => {
    switch(type) {
      case 'EXTERNAL_K8S': return { icon: Cloud, label: 'Kubernetes', color: 'text-blue-600', bg: 'bg-blue-50' };
      case 'EXTERNAL_YARN': return { icon: Layers, label: 'YARN Queue', color: 'text-amber-600', bg: 'bg-amber-50' };
      case 'SAAS_SERVERLESS': return { icon: Zap, label: 'Serverless', color: 'text-purple-600', bg: 'bg-purple-50' };
      default: return { icon: Server, label: 'Unknown', color: 'text-gray-600', bg: 'bg-gray-50' };
    }
  };

  const typeCfg = getTypeConfig(resource.type);
  const coreUsagePercent = Math.min(100, (resource.quota.usedCores / resource.quota.maxCores) * 100);
  const memUsagePercent = Math.min(100, (resource.quota.usedMemoryGB / resource.quota.maxMemoryGB) * 100);

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5 hover:shadow-md transition-shadow relative group">
       {/* Pending Request Banner */}
       {resource.pendingRequest && (
          <div className="absolute top-0 right-0 bg-orange-50 text-orange-600 px-3 py-1 rounded-bl-xl rounded-tr-xl text-[10px] font-bold border-b border-l border-orange-100 flex items-center">
             <Clock size={12} className="mr-1.5" />
             Quota Request Pending
          </div>
       )}

       <div className="flex items-start justify-between">
          {/* Left: Info */}
          <div className="flex items-start space-x-4">
             <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${typeCfg.bg} ${typeCfg.color} shadow-sm border border-gray-100`}>
                <typeCfg.icon size={24} />
             </div>
             <div>
                <div className="flex items-center space-x-2">
                   <h3 className="text-lg font-bold text-gray-900">{resource.name}</h3>
                   {/* Isolation Badge */}
                   {resource.isolationMode === 'GLOBAL_SHARED' ? (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-100 flex items-center">
                         <Globe size={10} className="mr-1" /> Shared
                      </span>
                   ) : (
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-100 flex items-center">
                         <Lock size={10} className="mr-1" /> Restricted
                      </span>
                   )}
                </div>
                <p className="text-sm text-gray-500 mt-1 max-w-lg">{resource.description}</p>
                
                {/* Connection Details */}
                <div className="flex items-center space-x-4 mt-3 text-xs text-gray-500 font-mono">
                   <div className="flex items-center px-2 py-1 bg-gray-50 rounded border border-gray-100">
                      <div className={`w-1.5 h-1.5 rounded-full mr-1.5 ${resource.connection.status === 'CONNECTED' ? 'bg-green-500' : 'bg-red-500'}`}></div>
                      {resource.connection.endpoint}
                   </div>
                   <div className="flex items-center">
                      <Activity size={12} className="mr-1.5 text-gray-400" />
                      {resource.connection.latency}
                   </div>
                   {resource.bindings.length > 0 && (
                      <div className="flex items-center text-purple-600 bg-purple-50 px-2 py-1 rounded">
                         <Users size={12} className="mr-1.5" />
                         Accessible by {resource.bindings.length} teams
                      </div>
                   )}
                </div>
             </div>
          </div>

          {/* Right: Actions */}
          <div className="flex items-center space-x-2">
             <button 
               onClick={onScale}
               className="flex items-center px-3 py-1.5 bg-white border border-gray-200 text-gray-600 text-xs font-bold rounded-lg hover:bg-gray-50 hover:text-gray-900 transition-colors"
             >
                <TrendingUp size={14} className="mr-1.5" /> Scale
             </button>
             <button 
               onClick={onAccess}
               className="flex items-center px-3 py-1.5 bg-white border border-gray-200 text-gray-600 text-xs font-bold rounded-lg hover:bg-gray-50 hover:text-gray-900 transition-colors"
             >
                <Shield size={14} className="mr-1.5" /> Access
             </button>
             <button className="p-1.5 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg">
                <MoreHorizontal size={16} />
             </button>
          </div>
       </div>

       <div className="h-px bg-gray-100 my-4"></div>

       {/* Footer: Quota & Runtimes */}
       <div className="grid grid-cols-2 gap-8">
          {/* Quota Usage */}
          <div className="space-y-3">
             <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Resource Quota</h4>
             <div className="grid grid-cols-2 gap-4">
                <div>
                   <div className="flex justify-between text-xs mb-1.5">
                      <span className="font-medium text-gray-600">CPU (Cores)</span>
                      <span className="font-mono text-gray-900">{resource.quota.usedCores} / {resource.quota.maxCores}</span>
                   </div>
                   <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${coreUsagePercent > 90 ? 'bg-red-500' : 'bg-blue-500'}`} style={{ width: `${coreUsagePercent}%` }}></div>
                   </div>
                </div>
                <div>
                   <div className="flex justify-between text-xs mb-1.5">
                      <span className="font-medium text-gray-600">Memory (GB)</span>
                      <span className="font-mono text-gray-900">{resource.quota.usedMemoryGB} / {resource.quota.maxMemoryGB}</span>
                   </div>
                   <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden">
                      <div className={`h-full rounded-full ${memUsagePercent > 90 ? 'bg-red-500' : 'bg-purple-500'}`} style={{ width: `${memUsagePercent}%` }}></div>
                   </div>
                </div>
             </div>
          </div>

          {/* Runtimes */}
          <div>
             <h4 className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Supported Runtimes</h4>
             <div className="flex flex-wrap gap-2">
                {resource.runtimes.map((rt, idx) => (
                   <span key={idx} className="flex items-center px-2 py-1 bg-gray-50 border border-gray-100 rounded-md text-[10px] font-bold text-gray-600">
                      {rt.type === 'Flink' && <Zap size={10} className="mr-1 text-red-500" />}
                      {rt.type === 'Spark' && <Activity size={10} className="mr-1 text-orange-500" />}
                      {rt.type === 'Hive' && <Database size={10} className="mr-1 text-yellow-500" />}
                      {rt.type === 'Python' && <Code2 size={10} className="mr-1 text-blue-500" />}
                      {rt.type} <span className="text-gray-400 font-normal ml-1">v{rt.version}</span>
                   </span>
                ))}
                <button className="px-2 py-1 text-[10px] border border-dashed border-gray-300 rounded-md text-gray-400 hover:text-gray-600 hover:border-gray-400 transition-colors">
                   + Add
                </button>
             </div>
          </div>
       </div>
    </div>
  );
};

export default function ComputePage() {
  const [resources, setResources] = useState<ComputeResource[]>(MOCK_RESOURCES);
  
  // Modals State
  const [isModalOpen, setIsModalOpen] = useState(false); // Create
  const [isScaleModalOpen, setIsScaleModalOpen] = useState(false); // Scale
  const [isAccessModalOpen, setIsAccessModalOpen] = useState(false); // Access Control
  const [targetResource, setTargetResource] = useState<ComputeResource | null>(null);
  
  // Search
  const [searchQuery, setSearchQuery] = useState('');

  // Form State
  const [bindingType, setBindingType] = useState<ResourceType>('EXTERNAL_K8S');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  
  // K8s Specific Form State
  const [k8sConnectMode, setK8sConnectMode] = useState<'AGENT' | 'DIRECT'>('AGENT');
  const [k8sApiEndpoint, setK8sApiEndpoint] = useState('');
  const [k8sToken, setK8sToken] = useState('');
  
  // YARN Specific Form State
  const [yarnEndpoint, setYarnEndpoint] = useState('');
  const [yarnQueue, setYarnQueue] = useState('');

  const [selectedEngines, setSelectedEngines] = useState<{type: string, version: string}[]>([]);
  // Create Form Isolation State
  const [createIsolationMode, setCreateIsolationMode] = useState<'GLOBAL_SHARED' | 'TEAM_DEDICATED'>('GLOBAL_SHARED');

  
  // Scale Modal State
  const [scaleCores, setScaleCores] = useState(0);
  const [scaleMemory, setScaleMemory] = useState(0);
  const [scaleReason, setScaleReason] = useState('');

  // Access Modal State
  const [accessMode, setAccessMode] = useState<'GLOBAL_SHARED' | 'TEAM_DEDICATED'>('GLOBAL_SHARED');
  const [accessBindings, setAccessBindings] = useState<TeamBinding[]>([]);

  // --- Handlers ---

  const toggleEngine = (type: string, version: string) => {
      const exists = selectedEngines.find(e => e.type === type);
      if (exists) {
          if (exists.version === version) {
              setSelectedEngines(selectedEngines.filter(e => e.type !== type));
          } else {
              setSelectedEngines(selectedEngines.map(e => e.type === type ? { type, version } : e));
          }
      } else {
          setSelectedEngines([...selectedEngines, { type, version }]);
      }
  };

  const resetForm = () => {
     setFormName('');
     setFormDesc('');
     setK8sApiEndpoint('');
     setK8sToken('');
     setYarnEndpoint('');
     setYarnQueue('');
     setSelectedEngines([]);
     setCreateIsolationMode('GLOBAL_SHARED');
     setK8sConnectMode('AGENT');
  };

  const handleRegister = () => {
     const endpoint = bindingType === 'EXTERNAL_K8S' 
        ? (k8sConnectMode === 'AGENT' ? 'Agent-Managed' : k8sApiEndpoint)
        : (bindingType === 'EXTERNAL_YARN' ? yarnEndpoint : 'Managed');

     const newRes: ComputeResource = {
        id: `res-${Date.now()}`,
        name: formName || 'New_Resource',
        type: bindingType,
        description: formDesc || 'No description',
        isolationMode: createIsolationMode,
        bindings: [], // Default to empty, can configure in Access Modal later
        connection: {
           endpoint: endpoint || 'pending...',
           queue: bindingType === 'EXTERNAL_YARN' ? yarnQueue : 'default',
           status: 'CONNECTED',
           latency: '20ms'
        },
        quota: {
           maxCores: 100,
           maxMemoryGB: 400,
           usedCores: 0,
           usedMemoryGB: 0
        },
        runtimes: selectedEngines.map(e => ({
            type: e.type as any,
            version: e.version,
            status: 'PROVISIONING'
        }))
     };
     setResources([...resources, newRes]);
     setIsModalOpen(false);
     resetForm();
  };

  const openScaleModal = (res: ComputeResource) => {
      setTargetResource(res);
      setScaleCores(res.quota.maxCores);
      setScaleMemory(res.quota.maxMemoryGB);
      setScaleReason('');
      setIsScaleModalOpen(true);
  };

  const openAccessModal = (res: ComputeResource) => {
      setTargetResource(res);
      setAccessMode(res.isolationMode);
      setAccessBindings([...res.bindings]);
      setIsAccessModalOpen(true);
  };

  const handleSubmitScale = () => {
      if (!targetResource) return;
      const updated = resources.map(r => r.id === targetResource.id ? {
          ...r,
          pendingRequest: {
              targetCores: scaleCores,
              targetMemoryGB: scaleMemory,
              reason: scaleReason,
              status: 'PENDING_APPROVAL' as const,
              submittedAt: 'Just now'
          }
      } : r);
      setResources(updated);
      setIsScaleModalOpen(false);
  };

  const handleSubmitAccess = () => {
      if (!targetResource) return;
      const updated = resources.map(r => r.id === targetResource.id ? {
          ...r,
          isolationMode: accessMode,
          bindings: accessMode === 'GLOBAL_SHARED' ? [] : accessBindings
      } : r);
      setResources(updated);
      setIsAccessModalOpen(false);
  };

  const toggleTeamBinding = (teamId: string) => {
      const exists = accessBindings.find(b => b.teamId === teamId);
      if (exists) {
          setAccessBindings(accessBindings.filter(b => b.teamId !== teamId));
      } else {
          const team = AVAILABLE_TEAMS.find(t => t.id === teamId);
          if (team) {
              setAccessBindings([...accessBindings, { 
                  teamId: team.id, 
                  teamName: team.name, 
                  avatar: team.avatar, 
                  priority: 'NORMAL' 
              }]);
          }
      }
  };

  const updateTeamPriority = (teamId: string, priority: 'HIGH' | 'NORMAL' | 'LOW') => {
      setAccessBindings(accessBindings.map(b => b.teamId === teamId ? { ...b, priority } : b));
  };

  const getOpsRequestNote = () => {
      if (selectedEngines.length === 0) return "仅申请基础资源连接权限。";
      const engines = selectedEngines.map(e => `${e.type} ${e.version}`).join(', ');
      return `申请开通计算环境，并预装/兼容以下运行时：${engines}。请确保底层节点镜像包含对应依赖。`;
  };

  return (
    <div className="flex flex-col h-full bg-[#F8F9FA] overflow-y-auto custom-scrollbar">
      
      {/* 1. Page Header */}
      <div className="px-8 py-8 flex-shrink-0 bg-white border-b border-gray-200">
        <div className="flex justify-between items-end">
           <div>
              <h1 className="text-2xl font-bold text-gray-900 flex items-center mb-2">
                 <Network className="mr-3 text-brand-600" size={28} />
                 计算仓 (Compute Warehouses)
              </h1>
              <p className="text-gray-500 text-sm max-w-2xl leading-relaxed">
                 接入并定义计算环境。
                 <span className="font-medium text-gray-700 mx-1">多团队隔离：</span>
                 配置 <span className="text-brand-600 font-bold">资源映射与访问控制 (ACL)</span>，决定哪些团队可以使用特定队列。
              </p>
           </div>
           <div className="flex items-center space-x-3">
              <div className="relative group">
                 <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 group-focus-within:text-brand-500" size={16} />
                 <input 
                   type="text" 
                   value={searchQuery}
                   onChange={(e) => setSearchQuery(e.target.value)}
                   placeholder="Search environments..." 
                   className="pl-10 pr-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-sm w-64 focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all"
                 />
              </div>
              <button 
                onClick={() => setIsModalOpen(true)}
                className="flex items-center px-5 py-2 bg-gray-900 text-white text-sm font-bold rounded-xl shadow-lg shadow-gray-200 hover:bg-black transition-all transform hover:-translate-y-0.5 active:scale-95"
              >
                 <Link size={16} className="mr-2" />
                 接入新资源
              </button>
           </div>
        </div>
      </div>

      {/* 2. Resources List */}
      <div className="p-8">
         <div className="flex flex-col space-y-4">
            {resources.map(res => (
               <ResourceRow 
                 key={res.id} 
                 resource={res} 
                 onScale={() => openScaleModal(res)}
                 onAccess={() => openAccessModal(res)}
               />
            ))}
            
            {resources.length === 0 && (
               <div className="text-center py-20 text-gray-400">
                  <Cloud size={48} className="mx-auto mb-4 opacity-20" />
                  <p>暂无计算资源接入</p>
               </div>
            )}
         </div>
      </div>

      {/* --- MODAL 1: NEW CONNECTION (Enhanced for K8s) --- */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
           <div className="absolute inset-0 bg-gray-900/30 backdrop-blur-sm transition-opacity" onClick={() => setIsModalOpen(false)}></div>
           
           <div className="relative bg-white rounded-2xl w-full max-w-4xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200 flex flex-col max-h-[90vh]">
              {/* Header */}
              <div className="px-6 py-5 border-b border-gray-100 bg-white flex justify-between items-center">
                 <div>
                    <h3 className="text-lg font-bold text-gray-900">接入计算资源 (Ops Request)</h3>
                    <p className="text-xs text-gray-500 mt-1">请详细填写基础设施信息及所需的运行时版本，以便运维侧进行环境准备。</p>
                 </div>
                 <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600 p-2 hover:bg-gray-100 rounded-full transition-colors"><X size={20} /></button>
              </div>

              {/* Body */}
              <div className="flex flex-1 overflow-hidden">
                 
                 {/* Left: Infrastructure Config */}
                 <div className="w-1/2 p-6 bg-white overflow-y-auto border-r border-gray-100">
                     <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-4 flex items-center">
                        <Server size={14} className="mr-2" /> 1. 基础设施连接
                     </h4>

                     <div className="space-y-5">
                         <div>
                            <label className="text-xs font-bold text-gray-700 block mb-2">资源类型</label>
                            <div className="grid grid-cols-3 gap-2">
                               {[
                                  { id: 'EXTERNAL_K8S', label: 'Kubernetes', icon: Cloud },
                                  { id: 'EXTERNAL_YARN', label: 'Hadoop YARN', icon: Layers },
                                  { id: 'SAAS_SERVERLESS', label: 'Serverless', icon: Zap },
                               ].map((opt) => (
                                  <button
                                     key={opt.id}
                                     onClick={() => setBindingType(opt.id as any)}
                                     className={`flex flex-col items-center justify-center p-3 rounded-lg border transition-all ${
                                        bindingType === opt.id 
                                        ? 'bg-blue-50 border-blue-500 text-blue-700' 
                                        : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
                                     }`}
                                  >
                                     <opt.icon size={20} className="mb-1" />
                                     <span className="text-[10px] font-bold">{opt.label}</span>
                                  </button>
                               ))}
                            </div>
                         </div>

                         <div>
                            <label className="text-xs font-bold text-gray-700 block mb-1.5">显示名称 (Display Name)</label>
                            <input 
                               type="text" 
                               value={formName}
                               onChange={(e) => setFormName(e.target.value)}
                               placeholder="e.g. Corp_Prod_K8s_Cluster"
                               className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-brand-500 transition-all"
                            />
                         </div>

                         {/* KUBERNETES SPECIFIC CONFIG */}
                         {bindingType === 'EXTERNAL_K8S' && (
                             <div className="bg-gray-50 p-4 rounded-xl border border-gray-200">
                                 <label className="text-xs font-bold text-gray-700 block mb-3">连接模式 (Connection Mode)</label>
                                 <div className="flex bg-white p-1 rounded-lg border border-gray-200 mb-4">
                                     <button 
                                        onClick={() => setK8sConnectMode('AGENT')}
                                        className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${k8sConnectMode === 'AGENT' ? 'bg-brand-50 text-brand-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                     >
                                         Agent (Helm)
                                     </button>
                                     <button 
                                        onClick={() => setK8sConnectMode('DIRECT')}
                                        className={`flex-1 py-1.5 text-[10px] font-bold rounded-md transition-all ${k8sConnectMode === 'DIRECT' ? 'bg-blue-50 text-blue-700 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}
                                     >
                                         Direct API
                                     </button>
                                 </div>

                                 {k8sConnectMode === 'AGENT' ? (
                                     <div className="animate-in fade-in duration-300">
                                         <p className="text-[10px] text-gray-500 mb-3 leading-relaxed">
                                             在您的集群中执行以下 Helm 命令以安装 Datapillar Agent。Agent 将主动建立安全反向隧道，<span className="font-bold text-gray-700">无需暴露 API Server</span>。
                                         </p>
                                         <div className="bg-gray-900 rounded-lg p-3 relative group">
                                             <code className="text-[10px] text-green-400 font-mono break-all block leading-relaxed">
                                                 helm install datapillar-agent oci://registry.dp.io/charts/agent \<br/>
                                                 &nbsp;&nbsp;--set token=dp_tok_8923a... \<br/>
                                                 &nbsp;&nbsp;--namespace datapillar-system --create-namespace
                                             </code>
                                             <button className="absolute top-2 right-2 text-gray-500 hover:text-white opacity-0 group-hover:opacity-100 transition-opacity">
                                                 <Copy size={14} />
                                             </button>
                                         </div>
                                     </div>
                                 ) : (
                                     <div className="space-y-3 animate-in fade-in duration-300">
                                         <div>
                                            <label className="text-[10px] font-bold text-gray-500 uppercase block mb-1">API Server Endpoint</label>
                                            <input 
                                                type="text" 
                                                value={k8sApiEndpoint} 
                                                onChange={(e) => setK8sApiEndpoint(e.target.value)}
                                                placeholder="https://10.20.30.40:6443"
                                                className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-xs font-mono"
                                            />
                                         </div>
                                         <div>
                                            <label className="text-[10px] font-bold text-gray-500 uppercase block mb-1">Service Account Token</label>
                                            <textarea 
                                                value={k8sToken} 
                                                onChange={(e) => setK8sToken(e.target.value)}
                                                placeholder="eyJhGci..."
                                                rows={3}
                                                className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-xs font-mono resize-none"
                                            />
                                         </div>
                                     </div>
                                 )}
                             </div>
                         )}

                         {/* YARN SPECIFIC CONFIG */}
                         {bindingType === 'EXTERNAL_YARN' && (
                            <div className="space-y-3">
                               <div>
                                  <label className="text-xs font-bold text-gray-700 block mb-1.5">ResourceManager URL</label>
                                  <div className="relative">
                                     <Network size={14} className="absolute left-3 top-2.5 text-gray-400" />
                                     <input 
                                        type="text" 
                                        value={yarnEndpoint}
                                        onChange={(e) => setYarnEndpoint(e.target.value)}
                                        placeholder="http://rm-host:8088"
                                        className="w-full pl-9 pr-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm font-mono focus:outline-none focus:border-brand-500 transition-all"
                                     />
                                  </div>
                               </div>
                               <div>
                                  <label className="text-xs font-bold text-gray-700 block mb-1.5">YARN Queue</label>
                                  <input 
                                     type="text" 
                                     value={yarnQueue}
                                     onChange={(e) => setYarnQueue(e.target.value)}
                                     placeholder="root.default"
                                     className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:border-brand-500 transition-all"
                                  />
                               </div>
                            </div>
                         )}
                         
                         <div>
                            <label className="text-xs font-bold text-gray-700 block mb-1.5">初始隔离策略 (Isolation)</label>
                            <div className="flex space-x-2">
                                <button 
                                   onClick={() => setCreateIsolationMode('GLOBAL_SHARED')}
                                   className={`flex-1 py-2 text-xs font-bold rounded-lg border transition-all ${createIsolationMode === 'GLOBAL_SHARED' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' : 'bg-white border-gray-200 text-gray-500'}`}
                                >
                                   Global Shared
                                </button>
                                <button 
                                   onClick={() => setCreateIsolationMode('TEAM_DEDICATED')}
                                   className={`flex-1 py-2 text-xs font-bold rounded-lg border transition-all ${createIsolationMode === 'TEAM_DEDICATED' ? 'bg-purple-50 text-purple-700 border-purple-200' : 'bg-white border-gray-200 text-gray-500'}`}
                                >
                                   Restricted
                                </button>
                            </div>
                         </div>
                     </div>
                 </div>

                 {/* Right: Runtime Specs */}
                 <div className="w-1/2 p-6 bg-gray-50/50 overflow-y-auto flex flex-col">
                     <h4 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2 flex items-center">
                        <Code2 size={14} className="mr-2" /> 2. 声明运行时 (Required Runtimes)
                     </h4>
                     <p className="text-[10px] text-gray-500 mb-4 bg-yellow-50 border border-yellow-100 p-2 rounded text-yellow-800">
                        <AlertTriangle size={12} className="inline mr-1 mb-0.5" />
                        请务必准确勾选该环境所需的引擎版本。运维将根据此列表配置基础镜像与 Operator。
                     </p>

                     <div className="space-y-4 flex-1">
                        {[
                           { 
                              type: 'Flink', 
                              icon: Zap, 
                              desc: 'Stream Processing',
                              versions: ['1.13', '1.15', '1.17', '1.18']
                           },
                           { 
                              type: 'Spark', 
                              icon: Activity, 
                              desc: 'Batch & ML',
                              versions: ['3.1', '3.3', '3.4', '3.5']
                           },
                           { 
                              type: 'Hive', 
                              icon: Database, 
                              desc: 'Data Warehouse',
                              versions: ['2.3', '3.1']
                           }
                        ].map(engine => {
                           const activeSelection = selectedEngines.find(e => e.type === engine.type);
                           return (
                              <div key={engine.type} className={`bg-white border rounded-xl p-3 transition-all ${activeSelection ? 'border-brand-500 shadow-sm' : 'border-gray-200 opacity-80 hover:opacity-100'}`}>
                                 <div className="flex items-center space-x-3 mb-3">
                                    <div className={`p-2 rounded-lg ${activeSelection ? 'bg-brand-50 text-brand-600' : 'bg-gray-100 text-gray-500'}`}>
                                       <engine.icon size={18} />
                                    </div>
                                    <div className="flex-1">
                                       <div className="text-sm font-bold text-gray-900">{engine.type}</div>
                                       <div className="text-[10px] text-gray-400">{engine.desc}</div>
                                    </div>
                                 </div>
                                 
                                 {/* Version Grid */}
                                 <div className="grid grid-cols-4 gap-2">
                                    {engine.versions.map(v => (
                                       <button 
                                          key={v}
                                          onClick={() => toggleEngine(engine.type, v)}
                                          className={`px-2 py-1 text-[10px] font-mono font-bold rounded border transition-all ${
                                             activeSelection?.version === v 
                                             ? 'bg-gray-900 text-white border-gray-900' 
                                             : 'bg-gray-50 text-gray-600 border-gray-100 hover:border-gray-300'
                                          }`}
                                       >
                                          v{v}
                                       </button>
                                    ))}
                                 </div>
                              </div>
                           );
                        })}
                     </div>

                     {/* Ops Note Preview */}
                     <div className="mt-4 p-3 bg-white border border-gray-200 rounded-xl">
                        <div className="text-[10px] font-bold text-gray-400 uppercase mb-1">Generated Ops Request Note</div>
                        <p className="text-xs text-gray-600 italic leading-relaxed">
                           "{getOpsRequestNote()}"
                        </p>
                     </div>
                 </div>

              </div>

              {/* Footer */}
              <div className="px-6 py-4 border-t border-gray-100 bg-white flex justify-end space-x-3">
                 <button onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-sm font-bold text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">取消</button>
                 <button onClick={handleRegister} className="px-6 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-black transition-colors flex items-center shadow-sm">
                    <Check size={14} className="mr-2" />
                    提交工单
                 </button>
              </div>
           </div>
        </div>
      )}

      {/* ... (Rest of the component: Scale Modal, Access Modal, etc.) ... */}
      
      {/* --- MODAL 2: SCALE REQUEST --- */}
      {isScaleModalOpen && targetResource && (
         <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
             <div className="absolute inset-0 bg-gray-900/30 backdrop-blur-sm transition-opacity" onClick={() => setIsScaleModalOpen(false)}></div>
             
             <div className="relative bg-white rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200">
                 <div className="px-6 py-5 border-b border-gray-100 bg-white flex justify-between items-center">
                    <div>
                        <h3 className="text-lg font-bold text-gray-900">申请资源扩容 (Expansion Request)</h3>
                        <p className="text-xs text-gray-500 mt-1">
                            为 <span className="font-mono font-bold text-gray-700">{targetResource.name}</span> 申请新的配额。
                        </p>
                    </div>
                    <button onClick={() => setIsScaleModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
                 </div>
                 
                 <div className="p-6 space-y-6">
                     <div className="grid grid-cols-2 gap-4">
                         <div>
                             <label className="text-xs font-bold text-gray-700 block mb-1.5 uppercase">期望 CPU (Cores)</label>
                             <div className="relative">
                                 <input type="number" value={scaleCores} onChange={(e) => setScaleCores(parseInt(e.target.value))} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm font-mono focus:border-brand-500 font-bold text-brand-600" />
                             </div>
                         </div>
                         <div>
                             <label className="text-xs font-bold text-gray-700 block mb-1.5 uppercase">期望内存 (GB)</label>
                             <div className="relative">
                                 <input type="number" value={scaleMemory} onChange={(e) => setScaleMemory(parseInt(e.target.value))} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm font-mono focus:border-brand-500 font-bold text-purple-600" />
                             </div>
                         </div>
                     </div>
                     <div>
                         <label className="text-xs font-bold text-gray-700 block mb-1.5 uppercase">申请理由</label>
                         <textarea value={scaleReason} onChange={(e) => setScaleReason(e.target.value)} className="w-full px-3 py-2 bg-white border border-gray-200 rounded-lg text-sm h-20 resize-none" placeholder="Explain why you need more..." />
                     </div>
                 </div>

                 <div className="px-6 py-4 border-t border-gray-100 bg-white flex justify-end space-x-3">
                     <button onClick={() => setIsScaleModalOpen(false)} className="px-4 py-2 text-sm font-bold text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg">取消</button>
                     <button onClick={handleSubmitScale} disabled={!scaleReason} className="px-6 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-black disabled:opacity-50">提交扩容申请</button>
                 </div>
             </div>
         </div>
      )}

      {/* --- MODAL 3: ACCESS CONTROL (NEW) --- */}
      {isAccessModalOpen && targetResource && (
         <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
             <div className="absolute inset-0 bg-gray-900/30 backdrop-blur-sm transition-opacity" onClick={() => setIsAccessModalOpen(false)}></div>
             
             <div className="relative bg-white rounded-2xl w-full max-w-xl shadow-2xl overflow-hidden animate-in zoom-in-95 duration-200 flex flex-col max-h-[85vh]">
                 <div className="px-6 py-5 border-b border-gray-100 bg-white flex justify-between items-center">
                    <div>
                        <div className="flex items-center space-x-2">
                            <Shield size={18} className="text-brand-600" />
                            <h3 className="text-lg font-bold text-gray-900">访问控制策略 (ACL)</h3>
                        </div>
                        <p className="text-xs text-gray-500 mt-1">
                            配置 <span className="font-mono font-bold text-gray-700">{targetResource.name}</span> 的可见性与使用权限。
                        </p>
                    </div>
                    <button onClick={() => setIsAccessModalOpen(false)} className="text-gray-400 hover:text-gray-600"><X size={20} /></button>
                 </div>
                 
                 <div className="p-6 bg-gray-50/50 flex-1 overflow-y-auto">
                     
                     {/* 1. Isolation Mode Switch */}
                     <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-sm mb-6">
                         <label className="text-xs font-bold text-gray-400 uppercase tracking-wide mb-3 block">资源隔离级别 (Isolation Level)</label>
                         <div className="grid grid-cols-2 gap-3">
                             <button 
                                onClick={() => setAccessMode('GLOBAL_SHARED')}
                                className={`flex items-start p-3 rounded-lg border-2 transition-all ${accessMode === 'GLOBAL_SHARED' ? 'border-brand-500 bg-brand-50/50' : 'border-gray-100 hover:border-gray-200'}`}
                             >
                                 <div className={`mt-0.5 p-1.5 rounded-full mr-3 ${accessMode === 'GLOBAL_SHARED' ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-400'}`}>
                                     <Globe size={16} />
                                 </div>
                                 <div className="text-left">
                                     <div className={`text-sm font-bold ${accessMode === 'GLOBAL_SHARED' ? 'text-brand-900' : 'text-gray-600'}`}>Global Shared</div>
                                     <p className="text-[10px] text-gray-500 mt-1">全员可用。适用于公共测试集群或 Serverless 资源池。</p>
                                 </div>
                             </button>

                             <button 
                                onClick={() => setAccessMode('TEAM_DEDICATED')}
                                className={`flex items-start p-3 rounded-lg border-2 transition-all ${accessMode === 'TEAM_DEDICATED' ? 'border-purple-500 bg-purple-50/50' : 'border-gray-100 hover:border-gray-200'}`}
                             >
                                 <div className={`mt-0.5 p-1.5 rounded-full mr-3 ${accessMode === 'TEAM_DEDICATED' ? 'bg-purple-500 text-white' : 'bg-gray-100 text-gray-400'}`}>
                                     <Lock size={16} />
                                 </div>
                                 <div className="text-left">
                                     <div className={`text-sm font-bold ${accessMode === 'TEAM_DEDICATED' ? 'text-purple-900' : 'text-gray-600'}`}>Restricted</div>
                                     <p className="text-[10px] text-gray-500 mt-1">仅限特定团队使用。适用于生产环境或高保密资源。</p>
                                 </div>
                             </button>
                         </div>
                     </div>

                     {/* 2. Team Binding List (Only if Dedicated) */}
                     {accessMode === 'TEAM_DEDICATED' && (
                         <div className="animate-in slide-in-from-top-2 duration-300">
                             <div className="flex justify-between items-end mb-3">
                                <label className="text-xs font-bold text-gray-400 uppercase tracking-wide">授权团队 (Allowed Teams)</label>
                                <span className="text-[10px] text-gray-500 bg-white px-2 py-0.5 rounded border border-gray-200">
                                    {accessBindings.length} teams selected
                                </span>
                             </div>
                             
                             <div className="bg-white border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                                 {AVAILABLE_TEAMS.map((team) => {
                                     const binding = accessBindings.find(b => b.teamId === team.id);
                                     const isSelected = !!binding;
                                     
                                     return (
                                         <div key={team.id} className={`flex items-center justify-between p-3 border-b border-gray-50 last:border-0 hover:bg-gray-50 transition-colors ${isSelected ? 'bg-purple-50/30' : ''}`}>
                                             <div className="flex items-center space-x-3">
                                                 <button 
                                                    onClick={() => toggleTeamBinding(team.id)}
                                                    className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${isSelected ? 'bg-purple-600 border-purple-600 text-white' : 'border-gray-300 bg-white hover:border-gray-400'}`}
                                                 >
                                                     {isSelected && <Check size={12} strokeWidth={3} />}
                                                 </button>
                                                 <div className={`w-8 h-8 rounded-lg ${team.avatar} flex items-center justify-center text-white text-[10px] font-bold shadow-sm`}>
                                                     {team.name.charAt(0)}
                                                 </div>
                                                 <span className={`text-sm font-medium ${isSelected ? 'text-gray-900' : 'text-gray-500'}`}>{team.name}</span>
                                             </div>

                                             {isSelected && (
                                                 <div className="flex items-center space-x-2 animate-in fade-in slide-in-from-right-2">
                                                     <span className="text-[10px] text-gray-400 font-bold uppercase mr-1">Priority:</span>
                                                     {['LOW', 'NORMAL', 'HIGH'].map(p => (
                                                         <button 
                                                            key={p}
                                                            onClick={() => updateTeamPriority(team.id, p as any)}
                                                            className={`px-2 py-0.5 text-[9px] font-bold rounded border transition-all ${
                                                                binding?.priority === p 
                                                                ? 'bg-white border-purple-300 text-purple-700 shadow-sm scale-105' 
                                                                : 'bg-transparent border-transparent text-gray-400 hover:bg-gray-100'
                                                            }`}
                                                         >
                                                             {p}
                                                         </button>
                                                     ))}
                                                 </div>
                                             )}
                                         </div>
                                     );
                                 })}
                             </div>
                         </div>
                     )}
                 </div>

                 <div className="px-6 py-4 border-t border-gray-100 bg-white flex justify-end space-x-3">
                     <button onClick={() => setIsAccessModalOpen(false)} className="px-4 py-2 text-sm font-bold text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg">取消</button>
                     <button onClick={handleSubmitAccess} className="px-6 py-2 bg-gray-900 text-white text-sm font-bold rounded-lg hover:bg-black shadow-sm">
                         更新策略
                     </button>
                 </div>
             </div>
         </div>
      )}

    </div>
  );
}