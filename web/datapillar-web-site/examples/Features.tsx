import React from 'react';
import { Database, Zap, Users, ShieldCheck } from 'lucide-react';

export const Features: React.FC = () => {
  return (
    <section id="features" className="py-32 bg-[#020410] relative border-b border-white/5">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="mb-20">
           <h2 className="text-violet-500 font-mono text-xs mb-4 tracking-wider uppercase">核心架构</h2>
           <h3 className="text-3xl md:text-5xl font-bold text-white tracking-tight mb-6">
             一体化 <br/>
             <span className="text-slate-500">SaaS 数据基座</span>
           </h3>
           <p className="text-slate-400 max-w-2xl text-lg mb-8 leading-relaxed">
             专为 SaaS 应用设计的数据基础设施。
             <br className="hidden md:block" />
             从多租户隔离到高并发 API，Datapillar 为您的产品提供企业级的数据处理能力，无需自行构建复杂的 ETL 管道。
           </p>

           {/* Tech Stack Badges */}
           <div className="flex flex-wrap gap-3">
              <div className="px-3 py-1.5 rounded-full bg-[#0f1623] border border-white/10 text-xs text-slate-300 font-mono flex items-center gap-2 cursor-default">
                <div className="w-1.5 h-1.5 rounded-full bg-violet-500"></div> 
                Multi-Tenancy
              </div>
              <div className="px-3 py-1.5 rounded-full bg-[#0f1623] border border-white/10 text-xs text-slate-300 font-mono flex items-center gap-2 cursor-default">
                <div className="w-1.5 h-1.5 rounded-full bg-cyan-500"></div> 
                Row Level Security
              </div>
              <div className="px-3 py-1.5 rounded-full bg-[#0f1623] border border-white/10 text-xs text-slate-300 font-mono flex items-center gap-2 cursor-default">
                <div className="w-1.5 h-1.5 rounded-full bg-emerald-500"></div> 
                Sub-millisecond Latency
              </div>
           </div>
        </div>

        {/* Bento Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 grid-rows-2 gap-4 h-auto md:h-[800px]">
           
           {/* Large Card: Federated Query */}
           <div className="col-span-1 md:col-span-2 row-span-1 bento-card rounded-2xl p-8 relative overflow-hidden group border border-white/5 bg-[#0b0f19] hover:border-violet-500/20 transition-all">
              <div className="relative z-10 h-full flex flex-col justify-between">
                 <div>
                    <div className="w-10 h-10 rounded-lg bg-violet-500/10 flex items-center justify-center mb-4 border border-violet-500/20">
                       <Database className="w-5 h-5 text-violet-400" />
                    </div>
                    <h4 className="text-2xl font-bold text-white mb-2">联邦查询引擎</h4>
                    <p className="text-slate-400 max-w-md">无需数据搬运。直接在 S3、Postgres 或 Snowflake 原地查询数据。通过智能缓存层实现跨源 Join 的毫秒级响应。</p>
                 </div>
                 
                 {/* Visual: Query Nodes */}
                 <div className="mt-8 flex items-center gap-4">
                     <div className="flex -space-x-2">
                        <div className="w-10 h-10 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] text-slate-400 font-mono">S3</div>
                        <div className="w-10 h-10 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] text-slate-400 font-mono">PG</div>
                        <div className="w-10 h-10 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-[10px] text-slate-400 font-mono">DW</div>
                     </div>
                     <div className="h-0.5 w-12 bg-gradient-to-r from-slate-700 to-violet-500"></div>
                     <div className="px-3 py-1.5 rounded bg-violet-900/20 border border-violet-500/30 text-violet-300 text-xs font-mono">
                        Unified View
                     </div>
                 </div>
              </div>
              <div className="absolute right-0 top-0 h-full w-1/3 bg-gradient-to-l from-violet-900/10 to-transparent pointer-events-none"></div>
           </div>

           {/* Tall Card: Governance */}
           <div className="col-span-1 md:col-span-1 row-span-2 bento-card rounded-2xl p-8 flex flex-col relative overflow-hidden border border-white/5 bg-[#0b0f19] hover:border-cyan-500/20 transition-all">
               <div className="w-10 h-10 rounded-lg bg-cyan-500/10 flex items-center justify-center mb-4 border border-cyan-500/20">
                  <ShieldCheck className="w-5 h-5 text-cyan-400" />
               </div>
               <h4 className="text-2xl font-bold text-white mb-2">企业级合规</h4>
               <p className="text-slate-400 text-sm mb-8">
                  内置 PII 扫描、数据脱敏与审计日志。满足 SOC2 与 GDPR 合规要求。
               </p>
               
               {/* Visual: Policy List */}
               <div className="flex-1 space-y-3 font-mono text-xs">
                  <div className="p-3 rounded bg-white/5 border border-white/5 flex items-center justify-between">
                     <span className="text-slate-300">PII_Detection</span>
                     <span className="text-green-400">Enabled</span>
                  </div>
                  <div className="p-3 rounded bg-white/5 border border-white/5 flex items-center justify-between">
                     <span className="text-slate-300">Audit_Log</span>
                     <span className="text-green-400">Active</span>
                  </div>
                  <div className="p-3 rounded bg-white/5 border border-white/5 flex items-center justify-between">
                     <span className="text-slate-300">Access_Control</span>
                     <span className="text-green-400">RBAC</span>
                  </div>
                  <div className="mt-4 p-4 rounded bg-cyan-950/30 border border-cyan-500/10">
                      <div className="text-[10px] text-cyan-500 mb-1">Security Alert</div>
                      <div className="text-slate-300">SSN pattern detected in `users.notes`. Auto-masking applied.</div>
                  </div>
               </div>
           </div>

           {/* Card: Multi-Tenancy */}
           <div className="col-span-1 md:col-span-1 row-span-1 bento-card rounded-2xl p-8 flex flex-col justify-between group border border-white/5 bg-[#0b0f19] hover:border-emerald-500/20 transition-all cursor-pointer">
               <div>
                  <div className="w-10 h-10 rounded-lg bg-emerald-500/10 flex items-center justify-center mb-4 border border-emerald-500/20">
                     <Users className="w-5 h-5 text-emerald-400" />
                  </div>
                  <h4 className="text-xl font-bold text-white mb-2">原生多租户</h4>
                  <p className="text-slate-400 text-sm">逻辑隔离或物理隔离。通过 Tenant ID 自动过滤数据，确保租户数据安全。</p>
               </div>
           </div>

           {/* Card: API */}
           <div className="col-span-1 md:col-span-1 row-span-1 bento-card rounded-2xl p-8 flex flex-col justify-between group border border-white/5 bg-[#0b0f19] hover:border-orange-500/20 transition-all cursor-pointer">
               <div>
                  <div className="w-10 h-10 rounded-lg bg-orange-500/10 flex items-center justify-center mb-4 border border-orange-500/20">
                     <Zap className="w-5 h-5 text-orange-400" />
                  </div>
                  <h4 className="text-xl font-bold text-white mb-2">即时 API</h4>
                  <p className="text-slate-400 text-sm">将数据集发布为高并发 API。支持 GraphQL 与 REST。内置边缘缓存。</p>
               </div>
           </div>

        </div>
      </div>
    </section>
  );
};