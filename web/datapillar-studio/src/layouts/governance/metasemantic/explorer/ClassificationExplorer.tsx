import { ArrowLeft, Plus, Shield, Stamp } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'

/** 安全等级类型 */
type SecurityLevel = 'L1' | 'L2' | 'L3' | 'L4'

/** 分级分类数据结构 */
interface Classification {
  id: string
  level: SecurityLevel
  name: string
  description: string
  piiCategories: string[]
  protectedAssets: number
}

/** Mock 数据 */
const MOCK_CLASSIFICATIONS: Classification[] = [
  {
    id: '1',
    level: 'L1',
    name: '公开数据',
    description: '可向全社会公开的数据，不涉及秘密。',
    piiCategories: ['通用信息', '公开文档'],
    protectedAssets: 124
  },
  {
    id: '2',
    level: 'L2',
    name: '内部数据',
    description: '仅限公司员工查看的数据，泄露会造成轻微损失。',
    piiCategories: ['内部项目ID', '非敏感备注'],
    protectedAssets: 124
  },
  {
    id: '3',
    level: 'L3',
    name: '敏感数据',
    description: '涉及用户隐私，泄露会造成较大法律风险或经济损失。',
    piiCategories: ['手机号', '电子邮箱', '地址', '真实姓名'],
    protectedAssets: 124
  },
  {
    id: '4',
    level: 'L4',
    name: '极度机密',
    description: '涉及公司核心商业秘密或用户财务安全，严禁外泄。',
    piiCategories: ['银行卡号', '身份证号', '密码哈希', '生物特征'],
    protectedAssets: 124
  }
]

/** 等级配置 - 档案袋风格 */
const LEVEL_CONFIG: Record<SecurityLevel, {
  stampColor: string
  stampBorder: string
  label: string
  folderTab: string
  tagStyle: string
  hoverBorder: string
}> = {
  L1: {
    stampColor: 'text-emerald-600 dark:text-emerald-400',
    stampBorder: 'border-emerald-400',
    label: '公开',
    folderTab: 'bg-emerald-500',
    tagStyle: 'text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800',
    hoverBorder: 'hover:border-emerald-400 dark:hover:border-emerald-400'
  },
  L2: {
    stampColor: 'text-blue-600 dark:text-blue-400',
    stampBorder: 'border-blue-400',
    label: '内部',
    folderTab: 'bg-blue-500',
    tagStyle: 'text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800',
    hoverBorder: 'hover:border-blue-400 dark:hover:border-blue-400'
  },
  L3: {
    stampColor: 'text-amber-600 dark:text-amber-400',
    stampBorder: 'border-amber-400',
    label: '敏感',
    folderTab: 'bg-amber-500',
    tagStyle: 'text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800',
    hoverBorder: 'hover:border-amber-400 dark:hover:border-amber-400'
  },
  L4: {
    stampColor: 'text-rose-600 dark:text-rose-400',
    stampBorder: 'border-rose-400',
    label: '机密',
    folderTab: 'bg-rose-600',
    tagStyle: 'text-rose-700 dark:text-rose-300 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800',
    hoverBorder: 'hover:border-rose-500 dark:hover:border-rose-500'
  }
}

interface ClassificationExplorerProps {
  onBack: () => void
}

/** 档案袋卡片组件 */
function ClassificationCard({ item }: { item: Classification }) {
  const config = LEVEL_CONFIG[item.level]

  return (
    <div className="group relative h-80">
      {/* 档案袋主体 - 高挑造型 */}
      <div className={`relative h-full bg-white dark:bg-slate-900 border-2 border-slate-300 dark:border-slate-600 rounded-t-sm rounded-b-lg overflow-hidden shadow-sm hover:shadow-lg transition-all duration-300 ${config.hoverBorder}`}>
        {/* 顶部封口 - 三角形折叠效果 */}
        <div className="absolute top-0 left-0 right-0">
          <div className="h-8 bg-slate-100 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700" />
          <div className="absolute top-8 left-1/2 -translate-x-1/2 w-0 h-0 border-l-[20px] border-r-[20px] border-t-[12px] border-l-transparent border-r-transparent border-t-slate-100 dark:border-t-slate-800" />
        </div>

        {/* 文件夹索引标签 */}
        <div className={`absolute -top-1 left-4 w-14 h-7 ${config.folderTab} rounded-b-md shadow flex items-end justify-center pb-1`}>
          <span className="text-white text-micro font-bold">{item.level}</span>
        </div>

        {/* 安全等级印章 */}
        <div className={`absolute top-14 right-3 w-14 h-14 rounded-full border-2 ${config.stampBorder} flex items-center justify-center rotate-[-15deg] opacity-60`}>
          <div className="text-center">
            <Stamp size={16} className={config.stampColor} />
            <span className={`block text-micro font-bold ${config.stampColor} mt-0.5`}>{config.label}</span>
          </div>
        </div>

        {/* 内容区域 */}
        <div className="pt-14 px-4 pb-4 h-full flex flex-col">
          {/* 标题 */}
          <h3 className="font-bold text-slate-800 dark:text-slate-100 text-body-sm mb-2">{item.name}</h3>

          {/* 描述 */}
          <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed mb-4 line-clamp-2">{item.description}</p>

          {/* 分隔线 */}
          <div className="border-t border-dashed border-slate-200 dark:border-slate-700 my-3" />

          {/* PII 识别类别 */}
          <div className="mb-2">
            <span className="text-micro text-slate-400 dark:text-slate-500 font-medium flex items-center gap-1">
              <Shield size={12} />
              PII 识别类别
            </span>
          </div>

          {/* 标签组 */}
          <div className="flex flex-wrap gap-1.5 flex-1">
            {item.piiCategories.map((category) => (
              <span
                key={category}
                className={`px-2 py-0.5 text-micro rounded h-fit ${config.tagStyle}`}
              >
                {category}
              </span>
            ))}
          </div>

          {/* 底部信息 */}
          <div className="flex items-center justify-between pt-3 border-t border-slate-100 dark:border-slate-800 mt-auto">
            <span className="text-micro text-slate-500 dark:text-slate-400">
              已保护 <span className="font-bold text-slate-700 dark:text-slate-200">{item.protectedAssets}</span>
            </span>
            <button className={`text-micro font-medium transition-colors ${config.stampColor} hover:opacity-80`}>
              配置 →
            </button>
          </div>
        </div>

        {/* 左侧装订孔 */}
        <div className="absolute left-2 top-1/2 -translate-y-1/2 flex flex-col gap-6">
          <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
          <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
          <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
        </div>
      </div>
    </div>
  )
}

export function ClassificationExplorer({ onBack }: ClassificationExplorerProps) {
  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50/40 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
      {/* 顶部导航栏 */}
      <div className="h-12 @md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 @md:px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
        <div className="flex items-center gap-2 @md:gap-3">
          <button
            onClick={onBack}
            className="p-1 @md:p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all"
          >
            <ArrowLeft size={iconSizeToken.large} />
          </button>
          <h2 className="text-body-sm @md:text-subtitle font-semibold text-slate-800 dark:text-slate-100">
            分级分类安全规范 <span className="font-normal text-slate-400">(Classification)</span>
          </h2>
        </div>
        <button className="bg-slate-900 dark:bg-blue-600 text-white px-3 @md:px-4 py-1 @md:py-1.5 rounded-lg text-caption @md:text-body-sm font-medium flex items-center gap-1 @md:gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all">
          <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">新增等级</span>
        </button>
      </div>

      {/* 卡片列表 */}
      <div className="flex-1 min-h-0 p-4 @md:p-6 overflow-auto custom-scrollbar">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 @md:gap-5">
          {MOCK_CLASSIFICATIONS.map((item) => (
            <ClassificationCard key={item.id} item={item} />
          ))}
        </div>
      </div>
    </div>
  )
}
