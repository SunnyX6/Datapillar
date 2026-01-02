
import React, { useEffect, useState } from 'react';
import { 
  ReactFlow, 
  Background, 
  Controls, 
  MiniMap, 
  useNodesState, 
  useEdgesState, 
  Handle, 
  Position, 
  MarkerType, 
  Node, 
  Edge,
  BackgroundVariant
} from '@xyflow/react';
import { useStore } from '../store';
import { Database, Filter, Save, ShieldCheck, Box, Rocket, Loader2, CheckCircle2 } from 'lucide-react';

// --- Custom Node Component: Optimized for Light Mode ---
const CustomNode = ({ data }: { data: any }) => {
  
  const getIcon = (type: string) => {
    switch (type) {
      case 'source': return <Database size={16} className="text-blue-500" />;
      case 'transform': return <Filter size={16} className="text-indigo-500" />;
      case 'sink': return <Save size={16} className="text-orange-500" />;
      case 'quality': return <ShieldCheck size={16} className="text-emerald-500" />;
      default: return <Box size={16} className="text-slate-400" />;
    }
  };

  const getStyles = (type: string) => {
    switch (type) {
      case 'source': return { border: 'border-blue-200', accent: 'bg-blue-50', shadow: 'shadow-blue-500/5' };
      case 'transform': return { border: 'border-indigo-200', accent: 'bg-indigo-50', shadow: 'shadow-indigo-500/5' };
      case 'sink': return { border: 'border-orange-200', accent: 'bg-orange-50', shadow: 'shadow-orange-500/5' };
      case 'quality': return { border: 'border-emerald-200', accent: 'bg-emerald-50', shadow: 'shadow-emerald-500/5' };
      default: return { border: 'border-slate-200', accent: 'bg-slate-50', shadow: 'shadow-slate-500/5' };
    }
  };

  const styles = getStyles(data.type);

  return (
    <div className={`w-[260px] rounded-2xl border ${styles.border} bg-white/90 backdrop-blur-xl ${styles.shadow} transition-all duration-300 hover:shadow-2xl hover:border-indigo-400 group relative overflow-hidden shadow-sm`}>
      {/* Handles */}
      {data.type !== 'source' && (
        <Handle 
          type="target" 
          position={Position.Left} 
          className="!w-2.5 !h-2.5 !bg-white !border-2 !border-slate-300 group-hover:!border-indigo-500 transition-colors !-left-1.5" 
        />
      )}

      <div className="p-4">
        <div className="flex items-center gap-3 mb-2">
          <div className={`w-8 h-8 rounded-lg ${styles.accent} flex items-center justify-center`}>
            {getIcon(data.type)}
          </div>
          <div className="flex-1 min-w-0">
            <h4 className="font-bold text-slate-900 text-[12px] truncate tracking-tight">{data.label}</h4>
            <span className="text-[9px] text-slate-400 uppercase font-black tracking-widest">{data.type}</span>
          </div>
        </div>
        <p className="text-[10px] text-slate-500 leading-relaxed line-clamp-2 font-medium">
          {data.description}
        </p>
      </div>

      {data.type !== 'sink' && (
        <Handle 
          type="source" 
          position={Position.Right} 
          className="!w-2.5 !h-2.5 !bg-white !border-2 !border-slate-300 group-hover:!border-indigo-500 transition-colors !-right-1.5" 
        />
      )}
    </div>
  );
};

const nodeTypes = { custom: CustomNode };

export const WorkflowCanvas: React.FC = () => {
  const { workflowData } = useStore();
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [isDeploying, setIsDeploying] = useState(false);
  const [deployStatus, setDeployStatus] = useState<'idle' | 'success'>('idle');

  useEffect(() => {
    if (workflowData.nodes.length > 0) {
      setNodes(workflowData.nodes.map(node => ({
        id: node.id,
        type: 'custom',
        position: { x: node.x, y: node.y },
        data: { label: node.label, type: node.type, description: node.description },
      })));
      setEdges(workflowData.edges.map(edge => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        animated: true,
        style: { stroke: '#6366f1', strokeWidth: 2, opacity: 0.3 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#6366f1' },
      })));
      setDeployStatus('idle');
    }
  }, [workflowData, setNodes, setEdges]);

  const handleDeploy = async () => {
    setIsDeploying(true);
    await new Promise(resolve => setTimeout(resolve, 2000));
    setIsDeploying(false);
    setDeployStatus('success');
    setTimeout(() => setDeployStatus('idle'), 3000);
  };

  if (workflowData.nodes.length === 0) {
    return (
      <div className="h-full w-full flex items-center justify-center bg-[#f8fafc] relative overflow-hidden group">
        <div className="text-center relative z-10">
          <div className="relative w-20 h-20 mx-auto mb-6">
            <div className="absolute inset-0 bg-indigo-500/10 rounded-full blur-2xl animate-pulse"></div>
            <div className="relative w-full h-full bg-white rounded-3xl border border-slate-200 flex items-center justify-center shadow-sm">
               <Database size={28} className="text-slate-300" />
            </div>
          </div>
          <h3 className="text-lg font-bold text-slate-800 mb-1 tracking-tight">One Workflow Studio</h3>
          <p className="text-slate-400 max-w-xs mx-auto text-[11px] font-medium leading-relaxed">
            Please describe your requirements to generate a data pipeline.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="h-full w-full bg-[#f8fafc] relative">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        colorMode="light"
        fitView
        className="bg-transparent"
      >
        <Background color="#cbd5e1" gap={24} size={1} variant={BackgroundVariant.Dots} className="opacity-[0.4]" />
        <Controls className="!m-6 !bg-white !border-slate-200 !rounded-xl !shadow-sm" />
        <MiniMap 
          className="!m-6 !bg-white/80 !backdrop-blur-xl !border-slate-200 !rounded-3xl !overflow-hidden !shadow-xl" 
          style={{ width: 240, height: 160 }}
          nodeColor={(node: any) => {
             switch (node.data?.type) {
               case 'source': return '#3B82F6';
               case 'transform': return '#6366f1';
               case 'sink': return '#F97316';
               case 'quality': return '#10B981';
               default: return '#e2e8f0';
             }
          }}
          maskColor="rgba(241, 245, 249, 0.6)"
          zoomable
          pannable
        />
      </ReactFlow>

      {/* Floating Toolbar */}
      <div className="absolute top-6 right-6 z-20 flex gap-2">
        {deployStatus === 'success' ? (
          <div className="flex items-center gap-2 px-4 py-2 bg-emerald-50 border border-emerald-200 rounded-2xl text-emerald-600 text-xs font-bold animate-fade-in shadow-sm">
            <CheckCircle2 size={14} />
            PIPELINE READY
          </div>
        ) : (
          <button 
            onClick={handleDeploy}
            disabled={isDeploying}
            className={`flex items-center gap-2 px-5 py-2.5 bg-slate-900 hover:bg-indigo-600 text-white rounded-2xl shadow-xl transition-all font-bold text-xs tracking-wide uppercase ${isDeploying ? 'opacity-50' : ''}`}
          >
            {isDeploying ? <Loader2 size={14} className="animate-spin" /> : <Rocket size={14} />}
            {isDeploying ? 'Deploying...' : 'Deploy to Cluster'}
          </button>
        )}
      </div>
    </div>
  );
};
