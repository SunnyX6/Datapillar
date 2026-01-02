
import React, { useState, useRef, useEffect } from 'react';
import { Send, Bot, User, Sparkles, Loader2, ArrowUp, Lightbulb, PanelLeftClose, Network, Database, CheckCircle2, Terminal, Globe, Search, Code2, Cpu, Activity, ChevronDown, ChevronUp } from 'lucide-react';
import { useStore } from '../store';
import { generateWorkflowFromPrompt } from '../services/geminiService';
import { motion, AnimatePresence } from 'framer-motion';

const SUGGESTIONS = [
  "Scan One Metadata for PII compliance",
  "Import new Business Glossary via Excel",
  "Analyze lineage for 'Monthly Revenue' metric"
];

interface AgentActivity {
  id: string;
  type: 'thought' | 'tool' | 'result' | 'error';
  agent: string;
  message: string;
  toolName?: string;
  timestamp: number;
}

interface ChatInterfaceProps {
  onCollapse: () => void;
}

export const ChatInterface: React.FC<ChatInterfaceProps> = ({ onCollapse }) => {
  const [input, setInput] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);
  const [currentActivities, setCurrentActivities] = useState<AgentActivity[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  
  const { messages, addMessage, isGenerating, setGenerating, setWorkflowData } = useStore();

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, isGenerating, currentActivities]);

  const addActivity = (activity: Omit<AgentActivity, 'id' | 'timestamp'>) => {
    setCurrentActivities(prev => [...prev, {
      ...activity,
      id: Math.random().toString(36).substr(2, 9),
      timestamp: Date.now()
    }]);
  };

  const simulateAgentReasoning = async (userMsg: string) => {
    setGenerating(true);
    setCurrentActivities([]);
    
    // 模拟思考过程
    addActivity({ type: 'thought', agent: 'Router_Agent', message: 'Analyzing user intent...' });
    await new Promise(r => setTimeout(r, 600));

    addActivity({ 
      type: 'tool', 
      agent: 'Metadata_Explorer', 
      message: 'Searching One Metadata layer...',
      toolName: 'gravitino_search_v2'
    });
    await new Promise(r => setTimeout(r, 800));
    addActivity({ 
      type: 'result', 
      agent: 'Metadata_Explorer', 
      message: 'Found verified assets: [sales_fact, user_dim].' 
    });

    addActivity({ type: 'thought', agent: 'Workflow_Architect', message: 'Synthesizing DAG structure...' });
    
    try {
      const data = await generateWorkflowFromPrompt(userMsg);
      setWorkflowData(data);

      addActivity({ 
        type: 'tool', 
        agent: 'Governance_Guard', 
        message: 'Running compliance policy check...',
        toolName: 'policy_engine_v1'
      });
      await new Promise(r => setTimeout(r, 700));

      // 关键：将当前所有的思考记录和最终结果一起存入 Message 对象中
      addMessage({
        id: Date.now().toString(),
        role: 'assistant',
        content: `I've successfully synthesized the workflow. The pipeline has been optimized for the **Gravitino** metadata layer and verified for PII compliance.`,
        timestamp: new Date(),
        // 扩展消息类型以支持携带思考历史（如果 store 支持，这里简单通过 metadata 模拟）
        activities: [...currentActivities, { 
          id: 'final', 
          type: 'result', 
          agent: 'System', 
          message: 'Graph synthesis complete.', 
          timestamp: Date.now() 
        }] as any
      });
    } catch (error) {
      addActivity({ type: 'error', agent: 'System', message: 'Synthesis failed.' });
    } finally {
      setGenerating(false);
      setCurrentActivities([]); // 清空当前活跃的流，因为它已经被存入 message 了
    }
  };

  const handleSend = async (text: string = input) => {
    if (!text.trim() || isGenerating) return;
    setInput('');
    addMessage({ id: Date.now().toString(), role: 'user', content: text, timestamp: new Date() });
    simulateAgentReasoning(text);
  };

  return (
    <div className="flex flex-col h-full relative border-r border-slate-200/60 bg-white">
      {/* Header */}
      <div className="h-14 px-4 flex items-center justify-between border-b border-slate-100 shrink-0 bg-white/80 backdrop-blur-md sticky top-0 z-10">
        <div className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-xl bg-slate-900 flex items-center justify-center">
            <Cpu size={14} className="text-white" />
          </div>
          <span className="text-[11px] font-black text-slate-800 tracking-wider uppercase">Architecture Agent</span>
        </div>
        <button onClick={onCollapse} className="p-1.5 text-slate-400 hover:text-slate-900 hover:bg-slate-50 rounded-lg transition-all">
          <PanelLeftClose size={16} />
        </button>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-5 pt-6 pb-48 space-y-8 scrollbar-hide" ref={scrollRef}>
        {messages.map((msg: any) => (
          <div key={msg.id} className={`flex flex-col gap-2 ${msg.role === 'user' ? 'items-end' : 'items-start'} animate-fade-in`}>
            <div className="px-1 flex items-center gap-2">
              <span className={`text-[9px] font-black uppercase tracking-widest ${msg.role === 'assistant' ? 'text-indigo-500' : 'text-slate-400'}`}>
                {msg.role === 'assistant' ? 'AI Architect' : 'Local User'}
              </span>
            </div>

            {/* Assistant Bubble with integrated Activities */}
            <div className={`w-[90%] flex flex-col ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
              <div className={`px-5 py-4 text-[13px] leading-relaxed w-full transition-all duration-500 ${
                msg.role === 'assistant' 
                  ? 'bg-white text-slate-700 rounded-3xl rounded-tl-none border border-slate-200/60 shadow-sm' 
                  : 'bg-indigo-600 text-white rounded-3xl rounded-tr-none shadow-md font-semibold w-auto inline-block'
              }`}>
                
                {/* 如果是 AI，先展示思考过程（已完成的思考） */}
                {msg.role === 'assistant' && msg.activities && (
                  <div className="mb-4 space-y-3 pb-4 border-b border-slate-50">
                    <div className="flex items-center justify-between">
                       <span className="text-[9px] font-bold text-slate-400 uppercase tracking-widest">Thought Process</span>
                       <CheckCircle2 size={12} className="text-emerald-500" />
                    </div>
                    {msg.activities.map((act: AgentActivity) => (
                      <div key={act.id} className="flex gap-3 opacity-60 hover:opacity-100 transition-opacity">
                        <div className="shrink-0 pt-0.5">
                          {act.type === 'tool' ? <Search size={10} className="text-amber-500" /> : <Activity size={10} className="text-indigo-400" />}
                        </div>
                        <div className="min-w-0">
                          <span className="text-[10px] font-bold text-slate-900 mr-2">{act.agent}</span>
                          <span className="text-[10px] text-slate-500">{act.message}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {msg.content}
              </div>
            </div>
          </div>
        ))}
        
        {/* Active Thought Stream - 仅在生成中且尚未存入消息时展示 */}
        {isGenerating && (
          <div className="flex flex-col gap-2 items-start animate-fade-in w-full">
             <div className="px-1 flex items-center gap-2">
                <span className="text-[9px] font-black text-indigo-500 uppercase tracking-widest flex items-center gap-2">
                   <Loader2 size={10} className="animate-spin" /> Neural Activity
                </span>
             </div>

             <div className="bg-white border border-slate-200/60 rounded-3xl rounded-tl-none p-5 w-[90%] shadow-sm space-y-4">
               <AnimatePresence mode="popLayout">
                 {currentActivities.map((act) => (
                   <motion.div 
                     key={act.id}
                     initial={{ opacity: 0, y: 5 }}
                     animate={{ opacity: 1, y: 0 }}
                     className="flex gap-4"
                   >
                      <div className="flex flex-col items-center shrink-0">
                         <div className={`w-6 h-6 rounded-lg border flex items-center justify-center shrink-0 ${
                           act.type === 'tool' ? 'bg-amber-50 border-amber-100 text-amber-500' : 
                           act.type === 'result' ? 'bg-emerald-50 border-emerald-100 text-emerald-500' : 
                           'bg-slate-50 border-slate-100 text-slate-400'
                         }`}>
                           {act.type === 'tool' ? <Search size={10} strokeWidth={3} /> : 
                            act.type === 'result' ? <CheckCircle2 size={10} strokeWidth={3} /> : 
                            <Activity size={10} strokeWidth={3} />}
                         </div>
                         <div className="w-[1px] h-full bg-slate-100 mt-1 min-h-[12px]" />
                      </div>
                      
                      <div className="flex-1 min-w-0 pt-0.5">
                         <div className="flex items-center gap-2 mb-0.5">
                            <span className="text-[10px] font-black text-slate-900 uppercase tracking-tighter">{act.agent}</span>
                            {act.toolName && (
                              <span className="text-[8px] font-mono bg-slate-50 px-1.5 py-0.5 rounded border border-slate-100 text-slate-400">
                                {act.toolName}
                              </span>
                            )}
                         </div>
                         <p className={`text-[11px] font-medium leading-relaxed ${act.type === 'result' ? 'text-slate-900' : 'text-slate-400'}`}>
                           {act.message}
                         </p>
                      </div>
                   </motion.div>
                 ))}
               </AnimatePresence>
               
               <div className="flex items-center gap-3 pt-1">
                  <div className="flex space-x-1">
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '200ms' }} />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '400ms' }} />
                  </div>
                  <span className="text-[10px] font-bold text-indigo-500 tracking-tight italic">Agent is thinking...</span>
               </div>
             </div>
          </div>
        )}
      </div>

      {/* Footer / Input Area */}
      <div className="absolute bottom-0 left-0 right-0 p-5 bg-white/90 backdrop-blur-md border-t border-slate-100 z-10">
        {!isGenerating && messages.length < 3 && (
          <div className="flex gap-2 overflow-x-auto pb-4 scrollbar-hide">
            {SUGGESTIONS.map((suggestion, i) => (
              <button key={i} onClick={() => handleSend(suggestion)} className="whitespace-nowrap px-4 py-2 rounded-full bg-slate-50 hover:bg-indigo-50 text-[11px] font-bold text-slate-500 hover:text-indigo-600 border border-slate-200 transition-all flex items-center gap-2">
                <Lightbulb size={12} className="text-indigo-400" />
                {suggestion}
              </button>
            ))}
          </div>
        )}

        <div className="relative group">
           <div className="relative flex items-center bg-slate-50 border border-slate-200 rounded-2xl shadow-sm transition-all focus-within:border-indigo-500 focus-within:bg-white focus-within:shadow-xl focus-within:shadow-indigo-500/10">
              <input
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
                placeholder="Ask Architect Agent..."
                className="flex-1 bg-transparent border-none focus:ring-0 text-sm text-slate-700 h-12 px-5 min-w-0"
                disabled={isGenerating}
              />
              <button 
                onClick={() => handleSend()}
                disabled={!input.trim() || isGenerating}
                className={`mr-2 w-9 h-9 rounded-xl flex items-center justify-center transition-all ${
                  input.trim() && !isGenerating ? 'bg-slate-900 text-white shadow-lg' : 'bg-transparent text-slate-300'
                }`}
              >
                {isGenerating ? <Loader2 size={16} className="animate-spin" /> : <ArrowUp size={20} strokeWidth={2.5} />}
              </button>
           </div>
        </div>
      </div>
    </div>
  );
}
