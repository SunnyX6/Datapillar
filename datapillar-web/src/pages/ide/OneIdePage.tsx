/**
 * One IDE 页面入口
 * 包含 Hero 页面和编辑器视图
 */

import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  Database,
  Terminal,
  Code2,
  Blocks,
  ArrowUpRight,
  Clock,
  Search,
  Rocket,
  ChevronRight,
  Zap
} from 'lucide-react'
import { contentMaxWidthClassMap, paddingClassMap } from '@/design-tokens/dimensions'

interface LanguageOption {
  id: string
  name: string
  desc: string
  icon: React.ElementType
  color: string
  tag: string
  borderColor: string
}

const LANGUAGES: LanguageOption[] = [
  { id: 'sql', name: 'SQL', desc: '使用标准 SQL 构建数据处理管道', icon: Database, color: 'text-orange-500', tag: 'Query', borderColor: 'hover:border-orange-500/50' },
  { id: 'python', name: 'Python', desc: '复杂逻辑与机器学习模型集成', icon: Terminal, color: 'text-blue-500', tag: 'AI/ML', borderColor: 'hover:border-blue-500/50' },
  { id: 'java', name: 'Java', desc: '构建高吞吐量的核心算子逻辑', icon: Code2, color: 'text-red-500', tag: 'Native', borderColor: 'hover:border-red-500/50' },
  { id: 'shell', name: 'Shell', desc: '环境配置与命令行任务脚本', icon: Blocks, color: 'text-emerald-500', tag: 'Ops', borderColor: 'hover:border-emerald-500/50' },
]

interface RecentFile {
  name: string
  type: string
  time: string
  icon: React.ElementType
  bgColor: string
  iconColor: string
  route: string
}

const RECENT_FILES: RecentFile[] = [
  { name: 'user_behavior_ag...', type: 'SQL', time: '2m ago', icon: Zap, bgColor: 'bg-orange-100', iconColor: 'text-orange-500', route: '/ide/sql' },
  { name: 'fraud_detection_v...', type: 'PYTHON', time: '1h ago', icon: Terminal, bgColor: 'bg-blue-100', iconColor: 'text-blue-500', route: '/ide/python' },
  { name: 'log_extractor.sh', type: 'SHELL', time: '5h ago', icon: Blocks, bgColor: 'bg-emerald-100', iconColor: 'text-emerald-500', route: '/ide/shell' },
]

export function OneIdePage() {
  const navigate = useNavigate()

  const handleStart = (lang: string) => {
    navigate(`/ide/${lang}`)
  }

  return (
    <div className="h-full bg-white dark:bg-slate-900 overflow-hidden relative">
      {/* Dynamic Ambient Background */}
      <div className="absolute top-0 right-0 size-[38vw] bg-indigo-50 dark:bg-indigo-950/50 blur-[100px] -translate-y-1/2 translate-x-1/2 rounded-full pointer-events-none" />
      <div className="absolute bottom-0 left-0 size-[25vw] bg-purple-50 dark:bg-purple-950/50 blur-[80px] translate-y-1/2 -translate-x-1/2 rounded-full pointer-events-none" />
      <div className="absolute inset-0 bg-[radial-gradient(#e5e7eb_1px,transparent_1px)] dark:bg-[radial-gradient(#334155_1px,transparent_1px)] [background-size:24px_24px] [mask-image:radial-gradient(ellipse_50%_50%_at_50%_50%,#000_70%,transparent_100%)] opacity-20 pointer-events-none" />

      <div className={`${contentMaxWidthClassMap.full} ${paddingClassMap.md} w-full mx-auto relative z-10`}>

        {/* Header Section - 相对于左侧区域居中 */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-6 lg:mb-10 xl:mb-12 text-center lg:pr-[12%]"
        >
          <div className="flex justify-center items-center gap-2 mb-3">
            <div className="px-3 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-900/50 border border-indigo-100 dark:border-indigo-800 flex items-center gap-1.5">
              <div className="w-1 h-1 rounded-full bg-indigo-500 animate-pulse" />
              <span className="text-nano font-black uppercase tracking-[0.15em] text-indigo-600 dark:text-indigo-400">Polyglot Engine</span>
            </div>
          </div>
          <h1 className="text-4xl lg:text-5xl font-black text-slate-900 dark:text-slate-100 tracking-tighter mb-3 leading-tight">
            ONE<span className="inline-block w-6" /><span className="text-transparent bg-clip-text bg-[linear-gradient(90deg,#4f46e5_0%,#7c3aed_25%,#ec4899_50%,#7c3aed_75%,#4f46e5_100%)] dark:bg-[linear-gradient(90deg,#818cf8_0%,#a78bfa_25%,#f472b6_50%,#a78bfa_75%,#818cf8_100%)] bg-[length:200%_100%] animate-[shimmer_3s_ease-in-out_infinite]">IDE</span>
          </h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 max-w-xl mx-auto leading-relaxed font-medium">
            统一的多引擎数据开发工作室，支持 SQL、Python、Java 多语言开发
          </p>
        </motion.div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 xl:gap-8">

          {/* Main Launch Area */}
          <div className="lg:col-span-8 space-y-4">
            <div className="flex items-center justify-between mb-1">
              <h3 className="text-micro font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest flex items-center gap-1.5">
                <Rocket size={12} /> 新建开发会话
              </h3>
              <button className="text-nano font-black text-indigo-600 dark:text-indigo-400 uppercase tracking-widest hover:underline flex items-center gap-0.5">
                查看模板 <ChevronRight size={9} />
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3 xl:gap-5 2xl:gap-6">
              {LANGUAGES.map((lang, idx) => (
                <motion.button
                  key={lang.id}
                  initial={{ opacity: 0, scale: 0.95 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: idx * 0.05 }}
                  onClick={() => handleStart(lang.id)}
                  className={`group relative xl:min-h-[192px] 2xl:min-h-[208px] p-4 xl:p-5 2xl:p-6 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-2xl text-left hover:shadow-xl hover:shadow-indigo-500/10 dark:hover:shadow-indigo-500/5 transition-all active:scale-[0.98] ${lang.borderColor}`}
                >
                  <div className="w-10 h-10 rounded-xl flex items-center justify-center mb-3 bg-slate-50 dark:bg-slate-700 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/50 transition-colors shadow-sm">
                    <lang.icon size={20} className={`${lang.color} group-hover:scale-110 transition-transform`} />
                  </div>
                  <div className="mb-1 flex items-center justify-between">
                    <h4 className="text-base font-bold text-slate-800 dark:text-slate-100">{lang.name}</h4>
                    <span className="text-tiny font-black px-1.5 py-0.5 rounded-full bg-slate-50 dark:bg-slate-700 text-slate-400 dark:text-slate-500 uppercase tracking-widest border border-slate-100 dark:border-slate-600">{lang.tag}</span>
                  </div>
                  <p className="text-legal text-slate-500 dark:text-slate-400 leading-relaxed font-medium group-hover:text-slate-600 dark:group-hover:text-slate-300">{lang.desc}</p>
                  <div className="absolute bottom-4 right-4 p-1.5 rounded-full bg-indigo-50 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-400 opacity-0 group-hover:opacity-100 transition-all translate-y-1 group-hover:translate-y-0">
                    <ArrowUpRight size={12} strokeWidth={3} />
                  </div>
                </motion.button>
              ))}
            </div>
          </div>

          {/* Side Info Panel - Code Style */}
          <div className="lg:col-span-4">
            <div className="bg-slate-100 dark:bg-slate-800 rounded-2xl p-3.5 space-y-3 border border-slate-200 dark:border-slate-700 flex flex-col h-[320px] md:h-[320px] xl:h-[480px] 2xl:h-[520px]">
              {/* Quick Search */}
              <div className="bg-white dark:bg-slate-900 rounded-lg p-2.5 focus-within:ring-1 focus-within:ring-indigo-500/30 transition-all group border border-slate-200 dark:border-slate-700">
                <div className="flex items-center gap-2 text-slate-400 dark:text-slate-500">
                  <Search size={14} className="group-focus-within:text-indigo-500 dark:group-focus-within:text-indigo-400 transition-colors" />
                  <input type="text" placeholder="Jump to file..." className="bg-transparent border-none focus:ring-0 text-xs text-slate-700 dark:text-slate-200 w-full placeholder:text-slate-400 dark:placeholder:text-slate-500 placeholder:font-normal outline-none" />
                </div>
              </div>

              {/* Resume Coding */}
              <div className="space-y-2.5 flex-1 min-h-0 flex flex-col">
                <h3 className="text-micro font-semibold text-orange-500 uppercase tracking-wider flex items-center gap-1">
                  <Clock size={12} /> Resume Coding
                </h3>
                <div className="space-y-1.5 flex-1 min-h-0 overflow-y-auto custom-scrollbar pr-1">
                  {RECENT_FILES.map((file, i) => (
                    <div key={i} onClick={() => navigate(file.route)} className="flex items-center gap-2.5 p-2.5 rounded-xl bg-white dark:bg-slate-900 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer transition-all border border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 group">
                      <div className={`w-9 h-9 ${file.bgColor} dark:opacity-80 rounded-lg flex items-center justify-center transition-transform group-hover:scale-105`}>
                        <file.icon size={16} className={file.iconColor} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-xs font-semibold text-slate-700 dark:text-slate-200 truncate">{file.name}</div>
                        <div className="flex items-center gap-1 text-micro">
                          <span className="font-medium text-slate-500 dark:text-slate-400">{file.type}</span>
                          <span className="text-slate-300 dark:text-slate-600">•</span>
                          <span className="text-orange-500">{file.time}</span>
                        </div>
                      </div>
                      <div className="w-7 h-7 rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                        <ChevronRight size={14} className="text-slate-500 dark:text-slate-400" />
                      </div>
                    </div>
                  ))}
                </div>

                {/* Full Session History Link */}
                <button className="w-full text-center text-micro font-semibold text-orange-500 hover:text-orange-600 uppercase tracking-wider pt-1 transition-colors">
                  Full Session History
                </button>
              </div>
            </div>
          </div>

        </div>

      </div>
    </div>
  )
}
