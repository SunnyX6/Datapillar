import React, { useState, useEffect } from 'react';
import { ArrowRight, Play, Database, FileJson, Sparkles, Bot, Check, BarChart3, Terminal, TrendingUp, DollarSign, Send, Paperclip, Mic } from 'lucide-react';

interface HeroProps {
  onRequestAccess: () => void;
}

const SCENARIOS = [
  {
    id: 'integration',
    title: '实时集成 (Zero-ETL)',
    description: '自动构建 MySQL 到 ClickHouse 的同步链路',
    icon: <Database className="w-3.5 h-3.5" />,
    duration: 5000 
  },
  {
    id: 'analytics',
    title: '智能分析 (Text-to-BI)',
    description: '自然语言生成 SQL 查询与可视化图表',
    icon: <BarChart3 className="w-3.5 h-3.5" />,
    duration: 6000 
  }
];

export const Hero: React.FC<HeroProps> = ({ onRequestAccess }) => {
  const [activeScenario, setActiveScenario] = useState(0);

  // Global Tab Switcher Timer
  useEffect(() => {
    const duration = SCENARIOS[activeScenario].duration;
    
    const timer = setInterval(() => {
        setActiveScenario(s => (s + 1) % SCENARIOS.length);
    }, duration);

    return () => clearInterval(timer);
  }, [activeScenario]);

  const handleDotClick = (index: number) => {
    setActiveScenario(index);
  };

  return (
    <div className="relative pt-32 pb-24 lg:pt-48 lg:pb-32 overflow-hidden border-b border-white/5 bg-[#020410]">
      {/* Background Matrix/Grid */}
      <div className="absolute inset-0 bg-cyber-grid opacity-30 pointer-events-none"></div>
      
      {/* Ambient Glows */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-[800px] pointer-events-none">
        <div className="absolute top-[-150px] left-[15%] w-[600px] h-[600px] bg-violet-600/20 rounded-full blur-[120px] animate-pulse"></div>
        <div className="absolute top-[50px] right-[10%] w-[500px] h-[500px] bg-cyan-500/10 rounded-full blur-[100px]"></div>
      </div>

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col items-center text-center z-10">
        {/* Badge */}
        <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-violet-900/30 border border-violet-500/30 mb-8 backdrop-blur-md hover:border-violet-500/50 transition-colors cursor-pointer group shadow-[0_0_15px_rgba(99,102,241,0.3)]">
          <span className="flex h-2 w-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(34,211,238,0.8)] animate-pulse"></span>
          <span className="text-violet-200 text-xs font-mono tracking-wide uppercase">Datapillar 2.4 正式发布</span>
        </div>
        
        {/* Headline */}
        <h1 className="text-5xl md:text-7xl font-bold text-white tracking-tight mb-8 leading-[1.2]">
          构建智能时代的 <br />
          <span className="bg-clip-text text-transparent bg-gradient-to-r from-violet-400 via-violet-200 to-cyan-200 text-glow">企业数据基石</span>
        </h1>
        
        <p className="mt-4 max-w-2xl text-lg md:text-xl text-slate-400 mb-12 leading-relaxed font-light">
          面向 AI 原生时代的 Data Fabric 架构。在一个沉浸式工作台中，实现 <span className="text-cyan-400">自动化集成</span>、<span className="text-cyan-400">实时数仓治理</span> 以及 <span className="text-cyan-400">全链路血缘分析</span>。
        </p>
        
        {/* Buttons */}
        <div className="flex flex-col sm:flex-row gap-5 mb-20 w-full sm:w-auto">
          <button 
            onClick={onRequestAccess}
            className="px-8 py-4 rounded-lg bg-[#5558ff] hover:bg-[#4548e6] text-white font-semibold transition-all flex items-center justify-center gap-2 shadow-[0_0_30px_rgba(85,88,255,0.4)] hover:shadow-[0_0_40px_rgba(85,88,255,0.6)] border border-white/10"
          >
            免费开始试用
            <ArrowRight className="w-4 h-4" />
          </button>
          
          <button className="px-8 py-4 rounded-lg bg-slate-900/50 hover:bg-slate-800/50 text-white font-medium border border-violet-500/30 hover:border-violet-500/60 transition-all flex items-center justify-center gap-2 backdrop-blur-sm group">
            <Play className="w-4 h-4 fill-violet-400 text-violet-400 group-hover:scale-110 transition-transform" />
            观看产品演示
          </button>
        </div>

        {/* VISUALIZATION: Datapillar Studio */}
        <div className="relative w-full max-w-6xl mx-auto perspective-1000 group mb-8">
          
          {/* Main Window Container */}
          <div className="relative rounded-xl bg-[#0b0f19] border border-slate-800 shadow-2xl overflow-hidden aspect-[16/12] md:aspect-[2.2/1] transform transition-transform duration-500 hover:scale-[1.005] neon-border flex flex-col">
            
            {/* Window Header */}
            <div className="h-9 bg-[#151b2e] border-b border-slate-800 flex items-center px-4 justify-between shrink-0">
                <div className="flex items-center gap-2">
                    <div className="flex gap-1.5">
                        <div className="w-2.5 h-2.5 rounded-full bg-slate-600"></div>
                        <div className="w-2.5 h-2.5 rounded-full bg-slate-600"></div>
                        <div className="w-2.5 h-2.5 rounded-full bg-slate-600"></div>
                    </div>
                    <div className="ml-4 text-[10px] text-slate-400 font-mono flex items-center gap-2">
                        <span className="text-violet-400 font-bold">Datapillar Studio</span>
                        <span className="text-slate-600">/</span>
                        <span>Workspace</span>
                        <span className="text-slate-600">/</span>
                        <span>{activeScenario === 0 ? 'pipeline_v2' : 'analytics_dashboard'}</span>
                    </div>
                </div>
                <div className="hidden md:flex items-center gap-3">
                     <div className="flex -space-x-1">
                        <div className="w-5 h-5 rounded-full bg-slate-700 border border-[#151b2e]"></div>
                        <div className="w-5 h-5 rounded-full bg-slate-600 border border-[#151b2e]"></div>
                     </div>
                     <div className="h-3 w-[1px] bg-slate-700"></div>
                     <div className="text-[10px] text-slate-400">Auto-Save: On</div>
                </div>
            </div>

            {/* Content Area - Switches based on Active Scenario */}
            {activeScenario === 0 ? (
                <PipelineView key="pipeline" />
            ) : (
                <AnalyticsView key="analytics" />
            )}

          </div>
        </div>

        {/* DOTS SWITCHER */}
        <div className="flex flex-col items-center justify-center gap-4">
             {/* Dots */}
             <div className="flex items-center gap-3 bg-slate-900/50 p-2 rounded-full border border-white/5 backdrop-blur-sm">
                {SCENARIOS.map((scenario, index) => (
                    <button
                        key={scenario.id}
                        onClick={() => handleDotClick(index)}
                        className="relative group focus:outline-none"
                        aria-label={scenario.title}
                    >
                        <div className={`transition-all duration-300 ease-out rounded-full ${
                            activeScenario === index 
                                ? 'w-8 h-2.5 bg-gradient-to-r from-violet-500 to-cyan-500 shadow-[0_0_12px_rgba(139,92,246,0.6)]' 
                                : 'w-2.5 h-2.5 bg-slate-700 group-hover:bg-slate-500'
                        }`}></div>
                    </button>
                ))}
            </div>

            {/* Fading Label */}
            <div className="h-6 overflow-hidden relative w-64">
                {SCENARIOS.map((scenario, index) => (
                    <div 
                        key={scenario.id}
                        className={`transition-all duration-300 absolute w-full text-center flex items-center justify-center gap-2 ${
                            activeScenario === index 
                                ? 'opacity-100 translate-y-0' 
                                : 'opacity-0 translate-y-4'
                        }`}
                    >
                        <span className="text-slate-400">{scenario.icon}</span>
                        <span className="text-sm text-slate-300 font-medium font-mono tracking-wide">
                            {scenario.title}
                        </span>
                    </div>
                ))}
            </div>
        </div>

      </div>
    </div>
  );
};

// ---------------- SUB-COMPONENTS FOR VIEWS ---------------- //

const PipelineView: React.FC = () => {
    const [step, setStep] = useState(0);

    useEffect(() => {
        // FAST Animation timeline for Pipeline (Compressed to ~2.5s total)
        const timers: ReturnType<typeof setTimeout>[] = [];
        setStep(0);
        timers.push(setTimeout(() => setStep(1), 300));  // AI Typing
        timers.push(setTimeout(() => setStep(2), 800));  // AI Response
        timers.push(setTimeout(() => setStep(3), 1200)); // Node 1 (MySQL)
        timers.push(setTimeout(() => setStep(4), 1600)); // Node 2 (Processing)
        timers.push(setTimeout(() => setStep(5), 2000)); // Node 3 (ClickHouse)
        timers.push(setTimeout(() => setStep(6), 2500)); // Success
        return () => timers.forEach(t => clearTimeout(t));
    }, []);

    return (
        <div className="flex flex-1 overflow-hidden">
            {/* LEFT: Chat */}
            <div className="w-[30%] min-w-[240px] max-w-[300px] bg-[#0f1623] border-r border-slate-800 flex flex-col z-20">
                <div className="p-3 border-b border-slate-800/50 flex justify-between items-center bg-[#151b2e]/50 shrink-0">
                    <div className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                        <Sparkles className="w-3.5 h-3.5 text-violet-400" />
                        AI Copilot
                    </div>
                </div>
                <div className="flex-1 p-4 space-y-4 overflow-y-auto custom-scrollbar">
                     <div className="flex flex-col gap-1 items-end animate-[slideUp_0.3s_ease-out]">
                        <div className="bg-slate-800 text-slate-200 px-3 py-2 rounded-2xl rounded-tr-sm max-w-[95%] border border-slate-700 text-[10px] leading-relaxed">
                            同步 MySQL 订单到 ClickHouse，自动脱敏手机号。
                        </div>
                    </div>
                    {step >= 1 && step < 2 && (
                         <div className="flex flex-col gap-1 items-start animate-[fadeIn_0.2s_ease-out]">
                             <div className="bg-violet-900/10 border border-violet-500/20 px-3 py-2 rounded-2xl rounded-tl-sm">
                                <div className="flex gap-1"><div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce"></div><div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce delay-75"></div><div className="w-1 h-1 bg-violet-400 rounded-full animate-bounce delay-150"></div></div>
                             </div>
                         </div>
                    )}
                    {step >= 2 && (
                        <div className="flex flex-col gap-1 items-start animate-[slideUp_0.3s_ease-out]">
                            <div className="flex items-center gap-1.5 mb-0.5"><Bot className="w-3 h-3 text-violet-400" /><span className="text-violet-400 text-[9px] font-bold">Datapillar AI</span></div>
                            <div className="bg-violet-900/10 border border-violet-500/20 text-slate-200 px-3 py-2 rounded-2xl rounded-tl-sm text-[10px] leading-relaxed">
                                <p>正在构建 Zero-ETL 管道... 策略: 掩码处理。</p>
                            </div>
                        </div>
                    )}
                    {step >= 6 && (
                        <div className="mt-2 bg-[#0b0f19] border border-green-900/30 rounded-lg p-3 animate-[slideUp_0.3s_ease-out_backwards]">
                                <div className="flex items-center gap-2 mb-2">
                                <div className="w-4 h-4 rounded-full bg-green-500/20 flex items-center justify-center"><Check className="w-2.5 h-2.5 text-green-500" /></div>
                                <span className="text-[10px] text-green-400 font-medium">Pipeline Ready</span>
                                </div>
                                <button className="w-full bg-violet-600 text-white text-[10px] font-bold py-1.5 rounded">立即部署</button>
                        </div>
                    )}
                </div>

                {/* ChatGPT-style Input Area */}
                <div className="p-3 border-t border-slate-800/50 bg-[#0f1623] shrink-0">
                    <div className="flex items-center gap-2 bg-[#1e293b] border border-slate-700/50 rounded-lg px-3 py-2">
                         <Paperclip className="w-3 h-3 text-slate-500 cursor-pointer hover:text-slate-300" />
                         <div className="flex-1 text-[10px] text-slate-500 font-mono">Ask AI Copilot...</div>
                         <div className="p-1 bg-violet-600/80 rounded shadow-lg shadow-violet-500/20 cursor-pointer">
                            <ArrowRight className="w-2.5 h-2.5 text-white" />
                         </div>
                    </div>
                </div>
            </div>

            {/* RIGHT: Canvas */}
            <div className="flex-1 relative bg-[#0b0f19] flex flex-col items-center justify-center overflow-hidden">
                <div className="absolute inset-0" style={{backgroundImage: 'radial-gradient(circle, #1e293b 1px, transparent 1px)', backgroundSize: '20px 20px', opacity: 0.3}}></div>
                <div className="relative z-10 flex items-center gap-2 md:gap-4 scale-[0.9] md:scale-100">
                    <div className={`w-28 h-20 bg-[#151b2e] border border-slate-700 rounded-lg shadow-xl flex flex-col items-center justify-center relative transition-all duration-300 ${step >= 3 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
                        <Database className="w-4 h-4 text-cyan-400 mb-1.5" />
                        <div className="text-[10px] text-slate-200 font-bold">MySQL</div>
                    </div>
                    <div className={`w-16 h-4 relative flex items-center transition-all duration-500 ${step >= 4 ? 'opacity-100 max-w-[64px]' : 'opacity-0 max-w-0'}`}>
                        <svg className="w-full h-full overflow-visible"><line x1="0" y1="50%" x2="100%" y2="50%" stroke="#334155" strokeWidth="2" strokeDasharray="4 4"/><line x1="0" y1="50%" x2="100%" y2="50%" stroke="#22d3ee" strokeWidth="2" strokeDasharray="4 4" className="animate-flow"/></svg>
                    </div>
                    <div className={`w-32 h-24 bg-[#151b2e] border border-violet-500 rounded-xl shadow-lg flex flex-col items-center justify-center relative z-20 transition-all duration-300 ${step >= 4 ? 'opacity-100 scale-100' : 'opacity-0 scale-0'}`}>
                         <Sparkles className="w-5 h-5 text-violet-400 mb-2 animate-pulse" />
                         <div className="text-[10px] font-bold text-white">Datapillar AI</div>
                    </div>
                    <div className={`w-16 h-4 relative flex items-center transition-all duration-500 ${step >= 5 ? 'opacity-100 max-w-[64px]' : 'opacity-0 max-w-0'}`}>
                        <svg className="w-full h-full overflow-visible"><line x1="0" y1="50%" x2="100%" y2="50%" stroke="#334155" strokeWidth="2" strokeDasharray="4 4"/><line x1="0" y1="50%" x2="100%" y2="50%" stroke="#a78bfa" strokeWidth="2" strokeDasharray="4 4" className="animate-flow"/></svg>
                    </div>
                    <div className={`w-28 h-20 bg-[#151b2e] border border-slate-700 rounded-lg shadow-xl flex flex-col items-center justify-center relative transition-all duration-300 ${step >= 5 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
                        <FileJson className="w-4 h-4 text-green-400 mb-1.5" />
                        <div className="text-[10px] text-slate-200 font-bold">ClickHouse</div>
                    </div>
                </div>
            </div>
        </div>
    );
};

const AnalyticsView: React.FC = () => {
    const [step, setStep] = useState(0);

    // Mock data for 14 days
    const mockData = [35, 42, 38, 55, 62, 58, 70, 65, 85, 75, 90, 82, 95, 88];

    // Helper to generate a smooth curve path for SVG
    const generateSmoothPath = (data: number[], width: number, height: number) => {
        if (data.length === 0) return "";
        const maxVal = Math.max(...data) * 1.4; // headroom
        const points = data.map((val, i) => ({
            x: (i / (data.length - 1)) * width,
            y: height - (val / maxVal) * height
        }));

        return points.reduce((acc, point, i, a) => {
            if (i === 0) return `M ${point.x},${point.y}`;
            const prev = a[i - 1];
            // Simple Bezier control points calculation
            const cp1x = prev.x + (point.x - prev.x) / 2;
            const cp1y = prev.y;
            const cp2x = prev.x + (point.x - prev.x) / 2;
            const cp2y = point.y;
            return `${acc} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${point.x},${point.y}`;
        }, "");
    };

    useEffect(() => {
        // FAST Animation timeline for Analytics (Compressed to ~1.8s total)
        const timers: ReturnType<typeof setTimeout>[] = [];
        setStep(0);
        timers.push(setTimeout(() => setStep(1), 300));  // AI Typing
        timers.push(setTimeout(() => setStep(2), 1000)); // SQL Generated
        timers.push(setTimeout(() => setStep(3), 1800)); // Chart rendered
        return () => timers.forEach(t => clearTimeout(t));
    }, []);

    return (
        <div className="flex flex-1 overflow-hidden">
             {/* LEFT: Chat */}
             <div className="w-[30%] min-w-[240px] max-w-[300px] bg-[#0f1623] border-r border-slate-800 flex flex-col z-20">
                <div className="p-3 border-b border-slate-800/50 flex justify-between items-center bg-[#151b2e]/50 shrink-0">
                    <div className="text-xs font-semibold text-slate-300 flex items-center gap-2">
                        <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                        AI Analyst
                    </div>
                </div>
                <div className="flex-1 p-4 space-y-4 overflow-y-auto custom-scrollbar">
                     <div className="flex flex-col gap-1 items-end animate-[slideUp_0.3s_ease-out]">
                        <div className="bg-slate-800 text-slate-200 px-3 py-2 rounded-2xl rounded-tr-sm max-w-[95%] border border-slate-700 text-[10px] leading-relaxed">
                            分析近14天华东地区销售趋势。
                        </div>
                    </div>
                    {step >= 1 && step < 2 && (
                         <div className="flex flex-col gap-1 items-start animate-[fadeIn_0.2s_ease-out]">
                             <div className="bg-cyan-900/10 border border-cyan-500/20 px-3 py-2 rounded-2xl rounded-tl-sm">
                                <div className="flex gap-1"><div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce"></div><div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce delay-75"></div><div className="w-1 h-1 bg-cyan-400 rounded-full animate-bounce delay-150"></div></div>
                             </div>
                         </div>
                    )}
                    {step >= 2 && (
                        <div className="flex flex-col gap-1 items-start animate-[slideUp_0.3s_ease-out]">
                            <div className="flex items-center gap-1.5 mb-0.5"><Bot className="w-3 h-3 text-cyan-400" /><span className="text-cyan-400 text-[9px] font-bold">Datapillar AI</span></div>
                            <div className="bg-cyan-900/10 border border-cyan-500/20 text-slate-200 px-3 py-2 rounded-2xl rounded-tl-sm text-[10px] leading-relaxed">
                                <p>SQL 查询已生成。正在渲染可视化...</p>
                            </div>
                        </div>
                    )}
                </div>

                {/* ChatGPT-style Input Area */}
                <div className="p-3 border-t border-slate-800/50 bg-[#0f1623] shrink-0">
                    <div className="flex items-center gap-2 bg-[#1e293b] border border-slate-700/50 rounded-lg px-3 py-2">
                         <Paperclip className="w-3 h-3 text-slate-500 cursor-pointer hover:text-slate-300" />
                         <div className="flex-1 text-[10px] text-slate-500 font-mono">Explain this chart...</div>
                         <div className="p-1 bg-cyan-600/80 rounded shadow-lg shadow-cyan-500/20 cursor-pointer">
                            <ArrowRight className="w-2.5 h-2.5 text-white" />
                         </div>
                    </div>
                </div>
            </div>

            {/* RIGHT: SQL & Chart Stage */}
            <div className="flex-1 relative bg-[#0b0f19] flex flex-col p-6 overflow-hidden">
                <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:20px_20px]"></div>
                
                <div className="relative z-10 w-full h-full flex flex-col gap-4">
                     {/* Code Block */}
                     <div className={`w-full bg-[#020410] border border-slate-700 rounded-lg p-3 font-mono text-[9px] transition-all duration-300 ${step >= 2 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'}`}>
                        <div className="flex items-center justify-between mb-2 pb-2 border-b border-slate-800">
                             <div className="text-cyan-400 flex items-center gap-1"><Terminal className="w-3 h-3"/> Generated SQL</div>
                             <div className="text-slate-500">Execution: 24ms</div>
                        </div>
                        <div className="text-slate-300 space-y-0.5">
                            <div><span className="text-violet-400">SELECT</span> DATE(order_date) <span className="text-violet-400">AS</span> day, <span className="text-yellow-400">SUM</span>(amount)</div>
                            <div><span className="text-violet-400">FROM</span> orders <span className="text-violet-400">WHERE</span> region = <span className="text-green-400">'East_China'</span></div>
                            <div><span className="text-violet-400">GROUP BY</span> 1 <span className="text-violet-400">ORDER BY</span> 1;</div>
                        </div>
                     </div>

                     {/* Professional Chart Dashboard */}
                     <div className={`flex-1 bg-[#151b2e]/50 border border-slate-700/50 rounded-lg p-4 flex flex-col transition-all duration-500 delay-100 ${step >= 3 ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8'}`}>
                         
                         {/* Dashboard Header - Added Value */}
                         <div className="flex items-start justify-between mb-4">
                             <div>
                                <div className="text-[10px] text-slate-400 uppercase tracking-wider font-mono mb-1">Total Revenue</div>
                                <div className="flex items-baseline gap-2">
                                    <span className="text-xl font-bold text-white">¥1,245,800</span>
                                    <div className="flex items-center text-green-400 text-[10px] font-bold bg-green-900/20 px-1.5 py-0.5 rounded">
                                        <TrendingUp className="w-3 h-3 mr-1" />
                                        +12.5%
                                    </div>
                                </div>
                             </div>
                             <div className="text-[10px] text-slate-500 flex flex-col items-end">
                                 <span>Last 14 Days</span>
                                 <span className="flex items-center gap-1 mt-1"><div className="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_5px_rgba(34,211,238,0.8)]"></div> Live</span>
                             </div>
                         </div>

                         {/* Chart Area */}
                         <div className="flex-1 relative flex items-end justify-between gap-1.5 px-1 border-b border-slate-700/50 pb-2">
                              {/* Background Grid Lines */}
                              <div className="absolute inset-x-0 inset-y-0 flex flex-col justify-between pointer-events-none opacity-20 pb-2">
                                  <div className="w-full h-[1px] bg-slate-500 border-t border-dashed border-slate-500"></div>
                                  <div className="w-full h-[1px] bg-slate-500 border-t border-dashed border-slate-500"></div>
                                  <div className="w-full h-[1px] bg-slate-500 border-t border-dashed border-slate-500"></div>
                                  <div className="w-full h-[1px] bg-transparent"></div>
                              </div>

                              {/* SVG Curve Overlay (Trend Line) */}
                              <svg className="absolute inset-0 w-full h-full pointer-events-none z-20 overflow-visible" preserveAspectRatio="none">
                                  <path 
                                    d={generateSmoothPath(mockData, 100, 100).replace(/([\d.]+) ([\d.]+)/g, (match, x, y) => {
                                        return match;
                                    })} 
                                    fill="none" 
                                    stroke="#fbbf24" // Amber/Yellow Trend Line
                                    strokeWidth="1.5"
                                    strokeDasharray="4 4"
                                    vectorEffect="non-scaling-stroke"
                                    viewBox="0 0 100 100"
                                    className="opacity-60"
                                  />
                                   <path 
                                     d={generateSmoothPath(mockData, 1000, 100)} // Assume 1000 width for relative calculation
                                     fill="none" 
                                     stroke="#f59e0b"
                                     strokeWidth="2"
                                     vectorEffect="non-scaling-stroke"
                                     className="drop-shadow-[0_0_4px_rgba(245,158,11,0.5)]"
                                   />
                              </svg>

                              {mockData.map((h, i) => {
                                  // Gradient Logic: Left (Violet) -> Right (Cyan)
                                  const hueRotate = (i / mockData.length) * 60; // 0 to 60 deg shift
                                  return (
                                    <div key={i} className="flex-1 h-full flex items-end justify-center group relative cursor-crosshair z-10">
                                        {/* Wider Bar with Gradient */}
                                        <div 
                                            className="w-3 md:w-4 rounded-t-sm shadow-[0_0_10px_rgba(34,211,238,0)] group-hover:shadow-[0_0_15px_rgba(99,102,241,0.5)] transition-all duration-300 relative"
                                            style={{
                                                height: step >= 3 ? `${h}%` : '0%',
                                                opacity: step >= 3 ? 0.9 : 0,
                                                background: `linear-gradient(to top, #4f46e5, hsl(${180 + hueRotate}, 100%, 50%))` 
                                                // Starts at Violet (Indigo 600), goes to Cyan/Blue-ish
                                            }}
                                        ></div>
                                        
                                        {/* Hover Glow Background */}
                                        <div className="absolute inset-x-0 bottom-0 top-0 bg-white/5 opacity-0 group-hover:opacity-100 transition-opacity rounded-sm"></div>

                                        {/* Tooltip */}
                                        <div className="absolute -top-10 left-1/2 -translate-x-1/2 bg-slate-900 border border-slate-700 text-white text-[9px] py-1 px-2 rounded shadow-xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-30 whitespace-nowrap">
                                            <span className="text-slate-300">Day {i + 1}:</span> <span className="text-cyan-400 font-mono font-bold">¥{(h * 1024).toLocaleString()}</span>
                                        </div>
                                    </div>
                                  )
                              })}
                         </div>
                         
                         {/* X Axis */}
                         <div className="flex justify-between mt-2 text-[8px] text-slate-500 font-mono px-1">
                             <span>Day 1</span>
                             <span>Day 7</span>
                             <span>Day 14</span>
                         </div>
                     </div>
                </div>
            </div>
        </div>
    );
};