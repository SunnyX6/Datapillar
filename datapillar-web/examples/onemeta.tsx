/* eslint-disable */
import React, { useState, useEffect } from 'react';
import { 
  Database, Table, ArrowLeft, 
  Server, Layers, FileText, Medal, Tag, 
  GitFork, Activity, User, Clock, CheckCircle2,
  MoreHorizontal, Share2, Book, AlertCircle,
  ShieldCheck, ChevronRight, BarChart3,
  FolderTree, Search, Filter, Plus, Settings,
  ArrowRight, Key, Lock, Eye, Download, History,
  GitBranch, Workflow, ChevronDown, LayoutGrid,
  RefreshCw, UploadCloud
} from './Icons';
import { metadataService } from '../services/metadataService';
import { Catalog, Schema, Table as TableType } from '../types';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';

// --- Sub-Components ---

// 1. Tree Node with "Guide Lines" (IDE Style)
interface TreeNodeProps {
    id: string;
    label: string;
    type: 'ROOT' | 'CATALOG' | 'SCHEMA' | 'TABLE';
    isOpen: boolean;
    isActive: boolean;
    hasChildren: boolean;
    level: number;
    onClick: () => void;
    onToggle: () => void;
}

const TreeNode: React.FC<TreeNodeProps> = ({ 
    label, type, isOpen, isActive, hasChildren, level, onClick, onToggle 
}) => {
    // Dynamic Indentation based on level
    const paddingLeft = level * 20 + 12;

    const getIcon = () => {
        switch(type) {
            case 'ROOT': return <Server size={15} className="text-indigo-500" />;
            case 'CATALOG': return <Database size={14} className="text-blue-600" />;
            case 'SCHEMA': return <FolderTree size={14} className="text-amber-500" />;
            case 'TABLE': return <Table size={14} className="text-slate-400 group-hover:text-slate-500" />;
            default: return <FileText size={14} />;
        }
    };

    return (
        <div className="relative">
            {/* Vertical Guide Line for levels > 0 */}
            {level > 0 && (
                <div 
                    className="absolute border-l border-slate-200 h-full"
                    style={{ left: `${(level - 1) * 20 + 20}px` }}
                />
            )}
            
            <div 
                className={`
                    group flex items-center gap-2 py-1.5 pr-3 cursor-pointer select-none text-sm transition-all
                    hover:bg-slate-100 relative
                    ${isActive 
                        ? 'bg-blue-50/60 text-blue-700 font-medium' 
                        : 'text-slate-600'}
                `}
                style={{ paddingLeft: `${paddingLeft}px` }}
                onClick={(e) => {
                    e.stopPropagation();
                    onClick();
                }}
            >
                 {/* Active Indicator Bar */}
                 {isActive && <div className="absolute left-0 top-0 bottom-0 w-[3px] bg-blue-600 rounded-r-sm" />}

                {/* Toggle Arrow */}
                <div 
                    className={`
                        w-4 h-4 flex items-center justify-center rounded hover:bg-black/5 transition-colors z-10
                        ${!hasChildren ? 'invisible' : ''}
                    `}
                    onClick={(e) => {
                        e.stopPropagation();
                        onToggle();
                    }}
                >
                    <ChevronRight size={10} className={`text-slate-400 transition-transform duration-200 ${isOpen ? 'rotate-90' : ''}`} />
                </div>

                {/* Icon & Label */}
                {getIcon()}
                <span className="truncate flex-1">{label}</span>
                
                {/* Hover Actions (Only visible on hover) */}
                <div className="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
                    <button className="p-0.5 hover:bg-slate-200 rounded text-slate-400 hover:text-blue-600">
                        <MoreHorizontal size={12} />
                    </button>
                </div>
            </div>
        </div>
    );
};

// 2. Status Badge
const QualityBadge = ({ score }: { score: number }) => {
    let colorClass = "bg-green-100 text-green-700 border-green-200";
    if (score < 90) colorClass = "bg-yellow-100 text-yellow-700 border-yellow-200";
    if (score < 70) colorClass = "bg-red-100 text-red-700 border-red-200";

    return (
        <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md border text-xs font-semibold ${colorClass}`}>
            <Activity size={12} />
            <span>{score} / 100 Quality</span>
        </div>
    );
};

// --- Main Explorer Component ---

const OneMetaExplorer: React.FC = () => {
  // Data State
  const [catalogs, setCatalogs] = useState<Catalog[]>([]);
  const [schemas, setSchemas] = useState<Record<string, Schema[]>>({});
  const [tables, setTables] = useState<Record<string, TableType[]>>({});
  
  // UI State
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['ROOT']));
  const [selectedNodeId, setSelectedNodeId] = useState<string>('ROOT');
  const [selectedNodeType, setSelectedNodeType] = useState<'ROOT' | 'CATALOG' | 'SCHEMA' | 'TABLE'>('ROOT');
  const [selectedAsset, setSelectedAsset] = useState<any>(null);
  
  // Tab State
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'COLUMNS' | 'LINEAGE' | 'QUALITY' | 'PROFILE'>('OVERVIEW');

  // Load Data
  useEffect(() => {
    const load = async () => {
        const cats = await metadataService.getCatalogs();
        setCatalogs(cats);
    };
    load();
  }, []);

  // Handlers
  const toggleNode = async (id: string, type: 'CATALOG' | 'SCHEMA') => {
    const newExpanded = new Set(expandedNodes);
    if (newExpanded.has(id)) {
        newExpanded.delete(id);
    } else {
        newExpanded.add(id);
        if (type === 'CATALOG') {
            const cat = catalogs.find(c => c.id === id);
            if (cat && !schemas[id]) {
                const s = await metadataService.getSchemas(cat.name);
                setSchemas(prev => ({ ...prev, [id]: s }));
            }
        } else if (type === 'SCHEMA') {
             const schemaName = id.split('::')[1]; 
             const t = await metadataService.getTables(schemaName);
             setTables(prev => ({ ...prev, [id]: t }));
        }
    }
    setExpandedNodes(newExpanded);
  };

  const handleSelect = (id: string, type: any, asset: any) => {
    setSelectedNodeId(id);
    setSelectedNodeType(type);
    setSelectedAsset(asset);
    setActiveTab('OVERVIEW');
  };

  // --- Render Sections ---

  const renderTree = () => (
      <div className="flex flex-col min-w-full pb-10">
          <TreeNode 
            id="ROOT" label="One Meta (Enterprise)" type="ROOT" 
            isOpen={expandedNodes.has('ROOT')} isActive={selectedNodeId === 'ROOT'} hasChildren={true} level={0}
            onClick={() => handleSelect('ROOT', 'ROOT', { name: 'One Meta' })}
            onToggle={() => {
                const next = new Set(expandedNodes);
                next.has('ROOT') ? next.delete('ROOT') : next.add('ROOT');
                setExpandedNodes(next);
            }}
          />
          {expandedNodes.has('ROOT') && catalogs.map(cat => (
              <React.Fragment key={cat.id}>
                  <TreeNode 
                    id={cat.id} label={cat.name} type="CATALOG"
                    isOpen={expandedNodes.has(cat.id)} isActive={selectedNodeId === cat.id} hasChildren={true} level={1}
                    onClick={() => handleSelect(cat.id, 'CATALOG', cat)}
                    onToggle={() => toggleNode(cat.id, 'CATALOG')}
                  />
                  {expandedNodes.has(cat.id) && schemas[cat.id]?.map(sch => {
                      const schNodeId = `${cat.id}::${sch.name}`;
                      return (
                          <React.Fragment key={sch.id}>
                              <TreeNode 
                                id={schNodeId} label={sch.name} type="SCHEMA"
                                isOpen={expandedNodes.has(schNodeId)} isActive={selectedNodeId === schNodeId} hasChildren={true} level={2}
                                onClick={() => handleSelect(schNodeId, 'SCHEMA', sch)}
                                onToggle={() => toggleNode(schNodeId, 'SCHEMA')}
                              />
                              {expandedNodes.has(schNodeId) && tables[schNodeId]?.map(tbl => (
                                  <TreeNode 
                                    key={tbl.id} id={tbl.id} label={tbl.name} type="TABLE"
                                    isOpen={false} isActive={selectedNodeId === tbl.id} hasChildren={false} level={3}
                                    onClick={() => handleSelect(tbl.id, 'TABLE', tbl)}
                                    onToggle={() => {}}
                                  />
                              ))}
                          </React.Fragment>
                      );
                  })}
              </React.Fragment>
          ))}
      </div>
  );

  const renderDetails = () => {
    if (selectedNodeType === 'ROOT') return renderDashboard();
    if (selectedNodeType === 'TABLE' && selectedAsset) return renderTableDetails(selectedAsset as TableType);
    return renderEmptyState();
  };

  const renderDashboard = () => (
      <div className="p-8 max-w-7xl mx-auto">
          <div className="mb-8">
            <h1 className="text-2xl font-bold text-slate-900">Platform Overview</h1>
            <p className="text-slate-500 mt-1">Global health and statistics for your data estate.</p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
              {[
                  { label: "Data Catalogs", value: catalogs.length, sub: "Connected", icon: Database, color: "text-blue-600", bg: "bg-blue-50" },
                  { label: "Total Assets", value: "12,402", sub: "+52 today", icon: Layers, color: "text-indigo-600", bg: "bg-indigo-50" },
                  { label: "Storage", value: "8.4 PB", sub: "Optimized", icon: Server, color: "text-purple-600", bg: "bg-purple-50" },
                  { label: "Avg Quality", value: "94%", sub: "Excellent", icon: ShieldCheck, color: "text-green-600", bg: "bg-green-50" },
              ].map((stat, i) => (
                  <div key={i} className="bg-white p-5 rounded-lg border border-slate-200 shadow-sm hover:shadow-md transition-shadow">
                      <div className="flex justify-between items-start">
                          <div>
                              <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">{stat.label}</p>
                              <p className="text-2xl font-bold text-slate-900">{stat.value}</p>
                              <p className="text-xs text-slate-400 mt-1">{stat.sub}</p>
                          </div>
                          <div className={`p-2 rounded-md ${stat.bg} ${stat.color}`}>
                              <stat.icon size={18} />
                          </div>
                      </div>
                  </div>
              ))}
          </div>
          
          <div className="bg-white rounded-lg border border-slate-200 shadow-sm p-6">
              <h3 className="text-sm font-bold text-slate-900 mb-6 flex items-center gap-2">
                <BarChart3 size={16} className="text-slate-400"/>
                Ingestion Volume (Last 24h)
              </h3>
              <div className="h-64 w-full">
                    <ResponsiveContainer width="100%" height="100%">
                        <BarChart data={[
                            { name: 'Hive', value: 400 }, { name: 'Iceberg', value: 300 },
                            { name: 'MySQL', value: 200 }, { name: 'Kafka', value: 150 },
                            { name: 'Postgres', value: 100 },
                        ]} barSize={32}>
                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                            <XAxis dataKey="name" tick={{fontSize: 12, fill: '#64748b'}} axisLine={false} tickLine={false} />
                            <YAxis tick={{fontSize: 12, fill: '#64748b'}} axisLine={false} tickLine={false} />
                            <Tooltip 
                                cursor={{fill: '#f1f5f9'}} 
                                contentStyle={{borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'}}
                            />
                            <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                        </BarChart>
                    </ResponsiveContainer>
              </div>
          </div>
      </div>
  );

  const renderTableDetails = (table: TableType) => (
      <div className="flex flex-col h-full bg-slate-50/50">
          {/* Header Section */}
          <div className="bg-white border-b border-slate-200 px-8 py-5 shadow-sm z-10">
              {/* Breadcrumb - Subtle */}
              <div className="flex items-center gap-2 text-xs text-slate-400 mb-3">
                  <span className="hover:text-blue-600 cursor-pointer transition-colors">OneMeta</span>
                  <ChevronRight size={10} />
                  <span className="hover:text-blue-600 cursor-pointer transition-colors">prod_hive_dw</span>
                  <ChevronRight size={10} />
                  <span className="hover:text-blue-600 cursor-pointer transition-colors">finance_mart</span>
              </div>

              <div className="flex items-start justify-between">
                  <div className="flex gap-5">
                       {/* Icon Block */}
                       <div className="w-14 h-14 bg-gradient-to-br from-blue-500 to-blue-600 rounded-lg shadow-blue-200 shadow-md flex items-center justify-center text-white flex-shrink-0">
                           <Table size={28} />
                       </div>
                       
                       {/* Title Block */}
                       <div>
                           <div className="flex items-center gap-3">
                               <h1 className="text-xl font-bold text-slate-900">{table.name}</h1>
                               {table.certification === 'GOLD' && (
                                   <span className="bg-amber-50 text-amber-600 border border-amber-200 text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wide flex items-center gap-1">
                                       <Medal size={10} /> Certified
                                   </span>
                               )}
                           </div>
                           <p className="text-slate-500 text-sm mt-1 max-w-3xl leading-relaxed">
                               {table.description}
                           </p>
                           
                           {/* Quick Stats Row */}
                           <div className="flex items-center gap-6 mt-4">
                                <QualityBadge score={table.qualityScore} />
                                <div className="h-4 w-px bg-slate-200"></div>
                                <div className="flex items-center gap-2 text-xs text-slate-500">
                                    <User size={12} className="text-slate-400"/> 
                                    <span>Owner: <span className="font-medium text-slate-700">{table.owner}</span></span>
                                </div>
                                <div className="flex items-center gap-2 text-xs text-slate-500">
                                    <Clock size={12} className="text-slate-400"/> 
                                    <span>Updated: <span className="font-medium text-slate-700">{table.updatedAt}</span></span>
                                </div>
                           </div>
                       </div>
                  </div>

                  {/* Actions */}
                  <div className="flex gap-2">
                      <button className="btn-secondary">
                          <Share2 size={14} /> Share
                      </button>
                      <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md shadow-sm transition-all flex items-center gap-2">
                          <Database size={14} /> Query
                      </button>
                  </div>
              </div>

              {/* Tag Row */}
              <div className="mt-5 flex items-center gap-2">
                   {table.domains.map(d => (
                       <span key={d} className="px-2 py-1 bg-slate-100 text-slate-600 text-xs rounded-md border border-slate-200 font-medium">
                           {d}
                       </span>
                   ))}
              </div>
          </div>

          {/* Navigation Tabs */}
          <div className="bg-white px-8 border-b border-slate-200 sticky top-0 z-10">
              <div className="flex gap-1">
                  {['OVERVIEW', 'COLUMNS', 'LINEAGE', 'QUALITY', 'PROFILE'].map((tab) => (
                      <button
                        key={tab}
                        onClick={() => setActiveTab(tab as any)}
                        className={`
                            px-4 py-3 text-sm font-medium border-b-2 transition-all
                            ${activeTab === tab 
                                ? 'border-blue-600 text-blue-600' 
                                : 'border-transparent text-slate-500 hover:text-slate-700 hover:bg-slate-50'}
                        `}
                      >
                        {tab.charAt(0) + tab.slice(1).toLowerCase()}
                      </button>
                  ))}
              </div>
          </div>

          {/* Scrollable Content */}
          <div className="flex-1 overflow-auto p-8">
              <div className="max-w-6xl mx-auto">
                  {activeTab === 'OVERVIEW' && (
                    <div className="space-y-6 animate-in fade-in duration-300">
                        {/* 1. Context Cards */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <div className="bg-white p-5 rounded-lg border border-slate-200 shadow-sm">
                                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">Technical Specs</h4>
                                <div className="space-y-3">
                                    <div className="flex justify-between text-sm"><span className="text-slate-500">Type</span> <span className="font-mono text-slate-700 bg-slate-100 px-1.5 rounded text-xs">HIVE_EXTERNAL</span></div>
                                    <div className="flex justify-between text-sm"><span className="text-slate-500">Format</span> <span className="font-mono text-slate-700 bg-slate-100 px-1.5 rounded text-xs">PARQUET</span></div>
                                    <div className="flex justify-between text-sm"><span className="text-slate-500">Rows</span> <span className="font-medium text-slate-800">{table.rowCount.toLocaleString()}</span></div>
                                    <div className="flex justify-between text-sm"><span className="text-slate-500">Size</span> <span className="font-medium text-slate-800">{table.size}</span></div>
                                </div>
                            </div>
                            
                            <div className="bg-white p-5 rounded-lg border border-slate-200 shadow-sm md:col-span-2">
                                <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">Governance & Usage</h4>
                                <div className="grid grid-cols-3 gap-4">
                                    <div className="text-center p-3 bg-slate-50 rounded border border-slate-100">
                                        <div className="text-2xl font-bold text-slate-800">1.2k</div>
                                        <div className="text-xs text-slate-500 mt-1">Weekly Queries</div>
                                    </div>
                                    <div className="text-center p-3 bg-slate-50 rounded border border-slate-100">
                                        <div className="text-2xl font-bold text-slate-800">42</div>
                                        <div className="text-xs text-slate-500 mt-1">Downstream Jobs</div>
                                    </div>
                                    <div className="text-center p-3 bg-slate-50 rounded border border-slate-100">
                                        <div className="text-2xl font-bold text-green-600">99.9%</div>
                                        <div className="text-xs text-slate-500 mt-1">SLA Met</div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* 2. Readme / Wiki */}
                        <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                             <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
                                 <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                     <Book size={16} className="text-slate-400"/> Documentation
                                 </h3>
                                 <button className="text-xs text-blue-600 hover:underline">Edit</button>
                             </div>
                             <div className="p-6 prose prose-sm prose-slate max-w-none">
                                 <p className="text-slate-600 leading-relaxed">
                                    This dataset contains the consolidated monthly revenue figures aggregated from regional sales marts. 
                                    It is the <strong>official</strong> source for Executive Quarterly Business Reviews (QBR).
                                 </p>
                                 <h4 className="text-slate-800 font-medium mt-4">Key Business Rules</h4>
                                 <ul className="list-disc pl-4 space-y-1 text-slate-600">
                                     <li>Revenue is recognized upon shipment, not order placement.</li>
                                     <li>All currencies are converted to USD using the daily spot rate at <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">close_of_business</code>.</li>
                                     <li>Returns are processed in the <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">fact_returns</code> table and must be joined for net revenue.</li>
                                 </ul>
                             </div>
                        </div>
                    </div>
                  )}

                  {activeTab === 'COLUMNS' && (
                      <div className="bg-white border border-slate-200 rounded-lg shadow-sm overflow-hidden animate-in fade-in slide-in-from-bottom-2 duration-300">
                           <table className="w-full text-left text-sm">
                              <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200">
                                  <tr>
                                      <th className="px-6 py-3 font-medium text-xs uppercase tracking-wider">Column Name</th>
                                      <th className="px-6 py-3 font-medium text-xs uppercase tracking-wider">Data Type</th>
                                      <th className="px-6 py-3 font-medium text-xs uppercase tracking-wider">Description</th>
                                      <th className="px-6 py-3 font-medium text-xs uppercase tracking-wider">Tags</th>
                                  </tr>
                              </thead>
                              <tbody className="divide-y divide-slate-100">
                                  {table.columns.map((col, idx) => (
                                      <tr key={idx} className="hover:bg-slate-50 transition-colors group">
                                          <td className="px-6 py-3 text-slate-700 font-medium font-mono text-xs">
                                              <div className="flex items-center gap-2">
                                                  {col.name}
                                                  {col.isPrimaryKey && <Key size={12} className="text-amber-500" />}
                                              </div>
                                          </td>
                                          <td className="px-6 py-3 text-slate-500 font-mono text-xs">{col.type}</td>
                                          <td className="px-6 py-3 text-slate-600">{col.comment || '-'}</td>
                                          <td className="px-6 py-3">
                                              {col.piiTag && (
                                                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-red-50 text-red-600 border border-red-100">
                                                      <Lock size={10} /> {col.piiTag}
                                                  </span>
                                              )}
                                          </td>
                                      </tr>
                                  ))}
                              </tbody>
                           </table>
                      </div>
                  )}
                  
                  {activeTab === 'LINEAGE' && (
                       <div className="h-[500px] bg-white border border-slate-200 rounded-lg shadow-sm flex items-center justify-center relative overflow-hidden">
                           <div className="absolute inset-0 bg-[radial-gradient(#e5e7eb_1px,transparent_1px)] [background-size:20px_20px] opacity-50"></div>
                           <div className="z-10 flex flex-col items-center">
                               <p className="mb-8 text-slate-500 font-medium">Data Lineage Graph</p>
                               <div className="flex items-center gap-12">
                                   {/* Source Node */}
                                   <div className="w-40 p-4 bg-white border border-slate-200 rounded-lg shadow-sm hover:shadow-md transition-shadow cursor-pointer flex flex-col items-center gap-2">
                                       <div className="p-2 bg-orange-100 text-orange-600 rounded-full"><Database size={16}/></div>
                                       <span className="text-sm font-bold text-slate-700">Kafka Stream</span>
                                       <span className="text-xs text-slate-400">Raw Topic</span>
                                   </div>

                                   {/* Connector */}
                                   <div className="w-16 h-px bg-slate-300 relative">
                                       <div className="absolute right-0 -top-1 w-2 h-2 border-t border-r border-slate-300 transform rotate-45"></div>
                                   </div>

                                   {/* Current Node */}
                                   <div className="w-48 p-5 bg-blue-50 border-2 border-blue-500 rounded-lg shadow-md flex flex-col items-center gap-2 relative">
                                       <div className="absolute -top-3 bg-blue-600 text-white text-[10px] px-2 py-0.5 rounded-full font-bold">CURRENT</div>
                                       <div className="p-2 bg-blue-100 text-blue-600 rounded-full"><Table size={20}/></div>
                                       <span className="text-sm font-bold text-slate-800">{table.name}</span>
                                       <span className="text-xs text-slate-500">Gold Certified</span>
                                   </div>

                                   {/* Connector */}
                                   <div className="w-16 h-px bg-slate-300 relative">
                                       <div className="absolute right-0 -top-1 w-2 h-2 border-t border-r border-slate-300 transform rotate-45"></div>
                                   </div>

                                   {/* Downstream */}
                                   <div className="w-40 p-4 bg-white border border-slate-200 rounded-lg shadow-sm hover:shadow-md transition-shadow cursor-pointer flex flex-col items-center gap-2">
                                       <div className="p-2 bg-purple-100 text-purple-600 rounded-full"><BarChart3 size={16}/></div>
                                       <span className="text-sm font-bold text-slate-700">Exec QBR</span>
                                       <span className="text-xs text-slate-400">Tableau</span>
                                   </div>
                               </div>
                           </div>
                       </div>
                  )}

                  {activeTab === 'QUALITY' && (
                      <div className="space-y-6">
                           <div className="bg-white border border-slate-200 rounded-lg p-6 shadow-sm">
                               <div className="flex items-center justify-between mb-6">
                                   <h3 className="font-bold text-slate-800">Quality Metrics Trend</h3>
                                   <div className="flex gap-2">
                                       <span className="text-xs font-medium text-slate-500 px-2 py-1 bg-slate-100 rounded">Last 30 Days</span>
                                   </div>
                               </div>
                               <div className="h-64 bg-slate-50 rounded flex items-center justify-center text-slate-400 text-sm border border-dashed border-slate-200">
                                   [Quality Trend Chart Placeholder]
                               </div>
                           </div>

                           <div className="bg-white border border-slate-200 rounded-lg overflow-hidden shadow-sm">
                               <table className="w-full text-left text-sm">
                                   <thead className="bg-slate-50 text-slate-500 font-semibold">
                                       <tr>
                                           <th className="px-6 py-3">Rule Name</th>
                                           <th className="px-6 py-3">Type</th>
                                           <th className="px-6 py-3">Status</th>
                                           <th className="px-6 py-3">Value</th>
                                           <th className="px-6 py-3">History</th>
                                       </tr>
                                   </thead>
                                   <tbody className="divide-y divide-slate-100">
                                       <tr>
                                           <td className="px-6 py-4 font-medium text-slate-700">unique_region_id</td>
                                           <td className="px-6 py-4 text-slate-500">Uniqueness</td>
                                           <td className="px-6 py-4"><span className="inline-flex items-center gap-1 text-green-600 text-xs font-bold bg-green-50 px-2 py-1 rounded"><CheckCircle2 size={12}/> PASS</span></td>
                                           <td className="px-6 py-4 font-mono text-slate-600">100%</td>
                                           <td className="px-6 py-4"><div className="flex gap-0.5"><div className="w-1 h-4 bg-green-400 rounded-sm"></div><div className="w-1 h-4 bg-green-400 rounded-sm"></div><div className="w-1 h-4 bg-green-400 rounded-sm"></div></div></td>
                                       </tr>
                                       <tr>
                                           <td className="px-6 py-4 font-medium text-slate-700">amount_positive</td>
                                           <td className="px-6 py-4 text-slate-500">Validity</td>
                                           <td className="px-6 py-4"><span className="inline-flex items-center gap-1 text-green-600 text-xs font-bold bg-green-50 px-2 py-1 rounded"><CheckCircle2 size={12}/> PASS</span></td>
                                           <td className="px-6 py-4 font-mono text-slate-600">100%</td>
                                           <td className="px-6 py-4"><div className="flex gap-0.5"><div className="w-1 h-4 bg-green-400 rounded-sm"></div><div className="w-1 h-4 bg-green-400 rounded-sm"></div><div className="w-1 h-4 bg-green-400 rounded-sm"></div></div></td>
                                       </tr>
                                   </tbody>
                               </table>
                           </div>
                      </div>
                  )}
              </div>
          </div>
      </div>
  );

  const renderEmptyState = () => (
    <div className="flex flex-col items-center justify-center h-full text-slate-400 bg-slate-50/50">
        <div className="w-20 h-20 bg-slate-100 rounded-full flex items-center justify-center mb-4">
            <Layers size={32} className="text-slate-300" />
        </div>
        <p className="text-lg font-medium text-slate-600">No Asset Selected</p>
        <p className="text-sm max-w-xs text-center mt-2">Select an item from the enterprise catalog tree on the left to view details.</p>
    </div>
  );

  return (
    <div className="flex flex-col h-full w-full bg-white">
      {/* --- Module Header (Management Perspective) --- */}
      <div className="h-14 border-b border-slate-200 flex items-center justify-between px-6 bg-white flex-shrink-0 z-20">
          <div className="flex items-center gap-4">
               <h2 className="text-lg font-bold text-slate-800 tracking-tight">Metadata Explorer</h2>
               <div className="h-4 w-px bg-slate-200"></div>
               <div className="flex gap-2">
                   <button className="text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-md transition-colors">
                       prod_hive_dw
                   </button>
                   <button className="text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 px-3 py-1.5 rounded-md transition-colors">
                       Last 24h
                   </button>
               </div>
          </div>
          <div className="flex items-center gap-3">
              <div className="relative">
                  <Search size={14} className="absolute left-2.5 top-2 text-slate-400" />
                  <input 
                      type="text" placeholder="Search catalog..." 
                      className="pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 w-64 transition-all"
                  />
              </div>
              <button className="p-2 text-slate-400 hover:text-blue-600 transition-colors"><RefreshCw size={16} /></button>
              <button className="btn-primary flex items-center gap-2 text-sm px-3 py-1.5 bg-slate-900 text-white hover:bg-slate-800 rounded-md shadow-sm transition-all">
                  <UploadCloud size={14} /> Connect Source
              </button>
          </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* --- Left Sidebar (Developer/Nav Perspective) --- */}
        <div className="w-[280px] flex-shrink-0 bg-white border-r border-slate-200 flex flex-col h-full">
            {/* Sidebar Tools */}
            <div className="p-3 border-b border-slate-100 flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider pl-2">Browser</span>
                <div className="flex gap-1">
                    <button className="p-1.5 hover:bg-slate-100 rounded text-slate-500 transition-colors"><Filter size={14} /></button>
                    <button className="p-1.5 hover:bg-slate-100 rounded text-slate-500 transition-colors"><Settings size={14} /></button>
                </div>
            </div>
            
            {/* Tree Content */}
            <div className="flex-1 overflow-y-auto py-2 custom-scrollbar">
                {renderTree()}
            </div>
            
            {/* Sidebar Footer */}
            <div className="p-3 border-t border-slate-200 text-[10px] text-slate-400 flex justify-center uppercase tracking-widest font-semibold">
                Gravitino Core v0.5.1
            </div>
        </div>

        {/* --- Right Main Content --- */}
        <div className="flex-1 h-full overflow-hidden bg-white">
            {renderDetails()}
        </div>
      </div>
    </div>
  );
};

export default OneMetaExplorer;
