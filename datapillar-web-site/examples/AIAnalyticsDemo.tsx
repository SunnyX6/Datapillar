import React, { useState, useEffect } from 'react';
import { Sparkles, Terminal, BarChart3, Search, Code2, PieChart, ArrowRight, TrendingUp } from 'lucide-react';

const DEMO_STEPS = [
  {
    id: 'ask',
    title: '自然语言提问',
    description: '无需学习 SQL。直接用中文询问业务问题，AI 引擎瞬间理解意图。',
    icon: <Search className="w-5 h-5" />,
    duration: 4000
  },
  {
    id: 'sql',
    title: 'SQL 自动生成',
    description: '透明可控。AI 生成标准 ANSI SQL，你可以随时审查、优化或复制。',
    icon: <Code2 className="w-5 h-5" />,
    duration: 3000
  },
  {
    id: 'visualize',
    title: '智能可视化',
    description: '自动选择最合适的图表类型。发现趋势，一键分享给团队。',
    icon: <BarChart3 className="w-5 h-5" />,
    duration: 5000
  }
];

export const AIAnalyticsDemo: React.FC = () => {
  const [activeStep, setActiveStep] = useState(0);
  const [progress, setProgress] = useState(0);

  // More data points for a professional look (28 days)
  const chartData = [
      40, 45, 30, 50, 65, 55, 70, 
      60, 75, 68, 85, 90, 80, 95,
      85, 75, 90, 100, 95, 85, 90,
      80, 70, 85, 95, 105, 90, 110
  ];

  // Helper to generate a smooth curve path for SVG (normalized 0-100)
  const generateSmoothPath = (data: number[]) => {
      const width = 1000; // arbitrary internal width
      const height = 100;
      const maxVal = Math.max(...data) * 1.1; // headroom

      const points = data.map((val, i) => ({
          x: (i / (data.length - 1)) * width,
          y: height - (val / maxVal) * height
      }));

      return points.reduce((acc, point, i, a) => {
          if (i === 0) return `M ${point.x},${point.y}`;
          const prev = a[i - 1];
          const cp1x = prev.x + (point.x - prev.x) / 2;
          const cp1y = prev.y;
          const cp2x = prev.x + (point.x - prev.x) / 2;
          const cp2y = point.y;
          return `${acc} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${point.x},${point.y}`;
      }, "");
  };

  useEffect(() => {
    const stepDuration = DEMO_STEPS[activeStep].duration;
    const intervalTime = 50; // Update every 50ms
    const steps = stepDuration / intervalTime;
    let currentStep = 0;

    const timer = setInterval(() => {
      currentStep++;
      const newProgress = (currentStep / steps) * 100;
      
      if (newProgress >= 100) {
        setProgress(0);
        setActiveStep((prev) => (prev + 1) % DEMO_STEPS.length);
        currentStep = 0;
      } else {
        setProgress(newProgress);
      }
    }, intervalTime);

    return () => clearInterval(timer);
  }, [activeStep]);

  return (
    <section className="py-24 bg-[#020410] relative overflow-hidden">
      {/* Background Decor */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[500px] bg-cyan-900/10 blur-[100px] rounded-full pointer-events-none"></div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="text-center mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-950/30 border border-cyan-500/30 text-cyan-400 text-xs font-mono mb-6">
            <Sparkles className="w-3 h-3" />
            <span>Datapillar Intelligence Engine</span>
          </div>
          <h2 className="text-3xl md:text-5xl font-bold text-white mb-6">
            对话即分析。<br />
            <span className="text-slate-500">从提问到洞察，只需几秒钟。</span>
          </h2>
        </div>

        {/* Demo Container */}
        <div className="max-w-5xl mx-auto">
          {/* Main Screen Area */}
          <div className="bg-[#0b0f19] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl min-h-[400px] md:min-h-[500px] relative flex flex-col">
            {/* Header Bar */}
            <div className="h-10 bg-[#151b2e] border-b border-slate-800 flex items-center px-4 gap-2">
              <div className="w-3 h-3 rounded-full bg-red-500/20 border border-red-500/50"></div>
              <div className="w-3 h-3 rounded-full bg-yellow-500/20 border border-yellow-500/50"></div>
              <div className="w-3 h-3 rounded-full bg-green-500/20 border border-green-500/50"></div>
              <div className="ml-auto text-[10px] text-slate-500 font-mono">AI Analysis Mode</div>
            </div>

            {/* Content Body - Changes based on Active Step */}
            <div className="flex-1 p-8 md:p-12 flex items-center justify-center relative bg-gradient-to-b from-[#0b0f19] to-[#0f1623]">
              
              {/* STEP 1: Ask */}
              <div className={`absolute inset-0 flex items-center justify-center transition-all duration-700 transform ${activeStep === 0 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'}`}>
                 <div className="w-full max-w-2xl">
                    <div className="flex items-center gap-4 mb-8 justify-center">
                        <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-cyan-500 to-violet-500 flex items-center justify-center shadow-lg shadow-cyan-500/20">
                            <Sparkles className="w-6 h-6 text-white animate-pulse" />
                        </div>
                        <h3 className="text-2xl text-slate-200 font-light">你想了解什么数据？</h3>
                    </div>
                    <div className="relative group">
                        <input 
                            type="text" 
                            readOnly
                            value="统计过去30天华东地区的销售额趋势，按天分组" 
                            className="w-full bg-[#151b2e] border border-slate-700 text-slate-200 text-lg rounded-2xl px-6 py-5 pl-14 shadow-xl focus:outline-none focus:border-cyan-500/50 transition-colors"
                        />
                        <Search className="absolute left-5 top-1/2 -translate-y-1/2 text-slate-500 w-6 h-6" />
                        <div className="absolute right-4 top-1/2 -translate-y-1/2">
                            <button className="bg-cyan-600 hover:bg-cyan-500 text-white p-2 rounded-xl transition-colors shadow-lg shadow-cyan-900/20">
                                <ArrowRight className="w-5 h-5" />
                            </button>
                        </div>
                    </div>
                 </div>
              </div>

              {/* STEP 2: SQL */}
              <div className={`absolute inset-0 flex items-center justify-center transition-all duration-700 transform ${activeStep === 1 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'}`}>
                 <div className="w-full max-w-3xl bg-[#020410] border border-slate-700 rounded-lg shadow-2xl overflow-hidden font-mono text-sm">
                    <div className="bg-[#1e293b] px-4 py-2 flex justify-between items-center border-b border-slate-700">
                        <span className="text-cyan-400 text-xs flex items-center gap-2">
                            <Code2 className="w-3 h-3" /> Generated SQL
                        </span>
                        <span className="text-[10px] text-slate-500">24ms execution</span>
                    </div>
                    <div className="p-6 space-y-2">
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">1</span>
                            <span className="text-violet-400">SELECT</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">2</span>
                            <span className="text-white pl-4">DATE(order_date) <span className="text-violet-400">AS</span> day,</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">3</span>
                            <span className="text-white pl-4"><span className="text-yellow-400">SUM</span>(total_amount) <span className="text-violet-400">AS</span> revenue</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">4</span>
                            <span className="text-violet-400">FROM</span> <span className="text-green-400">orders</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">5</span>
                            <span className="text-violet-400">WHERE</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">6</span>
                            <span className="text-white pl-4">region = <span className="text-orange-400">'East_China'</span></span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">7</span>
                            <span className="text-white pl-4"><span className="text-violet-400">AND</span> order_date >= <span className="text-violet-400">CURRENT_DATE</span> - INTERVAL <span className="text-orange-400">'30 days'</span></span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">8</span>
                            <span className="text-violet-400">GROUP BY</span> <span className="text-white">1</span>
                        </div>
                        <div className="flex">
                            <span className="text-slate-600 select-none mr-4">9</span>
                            <span className="text-violet-400">ORDER BY</span> <span className="text-white">1;</span>
                        </div>
                    </div>
                 </div>
              </div>

              {/* STEP 3: Visualize (NEW DESIGN) */}
              <div className={`absolute inset-0 flex items-center justify-center p-4 md:p-12 transition-all duration-700 transform ${activeStep === 2 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'}`}>
                  <div className="w-full h-full flex flex-col bg-[#151b2e]/30 border border-slate-700/50 rounded-xl p-6 backdrop-blur-sm">
                      {/* Chart Header */}
                      <div className="mb-6 flex justify-between items-start">
                          <div>
                              <h4 className="text-lg font-bold text-white flex items-center gap-2">
                                华东地区销售趋势
                                <span className="px-2 py-0.5 rounded bg-cyan-900/30 text-cyan-400 text-[10px] border border-cyan-700/30">Live Data</span>
                              </h4>
                              <p className="text-sm text-slate-500 mt-1">Source: <code className="text-slate-400">prod.orders</code></p>
                          </div>
                          <div className="text-right">
                              <div className="text-2xl font-bold text-white">¥3,240,000</div>
                              <div className="flex items-center justify-end gap-1 text-green-400 text-xs font-medium">
                                <TrendingUp className="w-3 h-3" />
                                <span>+24.5% vs prev 30 days</span>
                              </div>
                          </div>
                      </div>
                      
                      {/* CSS Chart - Wider Bars & Colors */}
                      <div className="flex-1 flex items-end justify-between gap-1 pb-4 border-b border-l border-slate-700/50 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:20px_20px] px-2 relative">
                           {/* Decorative grid lines */}
                           <div className="absolute inset-0 pointer-events-none flex flex-col justify-between py-4 opacity-10 z-0">
                              <div className="w-full h-px bg-slate-400 border-t border-dashed"></div>
                              <div className="w-full h-px bg-slate-400 border-t border-dashed"></div>
                              <div className="w-full h-px bg-slate-400 border-t border-dashed"></div>
                              <div className="w-full h-px bg-transparent"></div>
                           </div>

                           {/* SVG Curve Overlay (Trend Line) */}
                           <svg className="absolute inset-0 w-full h-full pointer-events-none z-20" viewBox="0 0 1000 100" preserveAspectRatio="none">
                               <path 
                                 d={generateSmoothPath(chartData)} 
                                 fill="none" 
                                 stroke="#22d3ee"
                                 strokeWidth="2"
                                 vectorEffect="non-scaling-stroke"
                                 className="opacity-40 drop-shadow-[0_0_8px_rgba(34,211,238,0.3)]"
                               />
                           </svg>

                          {chartData.map((val, i) => {
                              // Normalize height
                              const h = Math.min(100, (val / 110) * 100); 
                              // Dynamic Color based on value relative to max (110)
                              // Low (<60): Blue/Slate
                              // Mid (60-90): Cyan
                              // High (>90): Bright Cyan/White
                              const isPeak = val >= 100;
                              const isHigh = val > 80;
                              
                              let barColorClass = "bg-slate-600"; // default fallback
                              let gradient = "";
                              
                              if (isPeak) {
                                  gradient = "linear-gradient(to top, #7c3aed, #f472b6)"; // Purple to Pink
                              } else if (isHigh) {
                                  gradient = "linear-gradient(to top, #0891b2, #22d3ee)"; // Cyan to Bright Cyan
                              } else {
                                  gradient = "linear-gradient(to top, #1e3a8a, #3b82f6)"; // Dark Blue to Blue
                              }

                              return (
                                  <div key={i} className="flex-1 h-full flex items-end justify-center group relative cursor-crosshair z-10">
                                      <div 
                                        className={`w-2 md:w-2.5 rounded-t-sm transition-all duration-300 relative ${isPeak ? 'shadow-[0_0_15px_rgba(244,114,182,0.6)]' : ''}`}
                                        style={{
                                            height: activeStep === 2 ? `${h}%` : '0%',
                                            transitionDelay: `${i * 30}ms`,
                                            background: gradient
                                        }}
                                      ></div>
                                      
                                      {/* Hover Glow Background */}
                                      <div className="absolute inset-x-0 bottom-0 top-0 bg-white/5 opacity-0 group-hover:opacity-100 transition-opacity rounded-sm"></div>

                                      {/* Tooltip */}
                                      <div className="absolute -top-12 left-1/2 -translate-x-1/2 bg-slate-900 border border-slate-700 text-white text-[10px] py-1.5 px-2.5 rounded shadow-xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-30 whitespace-nowrap flex flex-col items-center">
                                          <span className="text-slate-400 text-[8px] uppercase">Nov {i+1}</span>
                                          <span className="text-cyan-400 font-mono font-bold">¥{(val * 1000).toLocaleString()}</span>
                                      </div>
                                  </div>
                              );
                          })}
                      </div>
                      <div className="flex justify-between text-[10px] text-slate-500 mt-3 font-mono">
                          <span>Nov 1</span>
                          <span>Nov 15</span>
                          <span>Nov 30</span>
                      </div>
                  </div>
              </div>

            </div>
          </div>

          {/* Invisible Bottom Tabs / Progress Indicators */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mt-8">
            {DEMO_STEPS.map((step, index) => {
              const isActive = index === activeStep;
              return (
                <button
                  key={step.id}
                  onClick={() => {
                    setActiveStep(index);
                    setProgress(0);
                  }}
                  className={`text-left group relative p-4 rounded-xl transition-all duration-300 ${isActive ? 'bg-white/5' : 'hover:bg-white/5'}`}
                >
                  {/* Progress Line */}
                  <div className="absolute bottom-0 left-0 w-full h-[2px] bg-slate-800 rounded-b-xl overflow-hidden">
                    <div 
                      className="h-full bg-cyan-400 transition-all duration-75 ease-linear"
                      style={{ 
                        width: isActive ? `${progress}%` : '0%',
                        opacity: isActive ? 1 : 0
                      }}
                    ></div>
                  </div>

                  <div className="flex items-start gap-4">
                    <div className={`p-2 rounded-lg transition-colors ${isActive ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/30' : 'bg-slate-800 text-slate-500 group-hover:text-slate-300'}`}>
                      {step.icon}
                    </div>
                    <div>
                      <h4 className={`text-sm font-bold mb-1 transition-colors ${isActive ? 'text-white' : 'text-slate-400 group-hover:text-slate-200'}`}>
                        {step.title}
                      </h4>
                      <p className={`text-xs leading-relaxed transition-colors ${isActive ? 'text-cyan-100/70' : 'text-slate-600'}`}>
                        {step.description}
                      </p>
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

      </div>
    </section>
  );
};