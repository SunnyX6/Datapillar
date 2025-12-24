import { useState, useMemo } from 'react'
import { Target, Book, Shield, Cpu, Globe, Workflow, Zap } from 'lucide-react'
import { HubAssetCard } from './components'
import { MetricExplorer, WordRootExplorer, StandardsExplorer, ModelsExplorer, ApisExplorer } from './explorer'
import { MetricOverview, WordRootOverview } from './overview'
import type { SemanticCategory, CategoryConfig, Metric, WordRoot } from './types'
import { iconSizeToken } from '@/design-tokens/dimensions'

export function SemanticHubView() {
  const [activeCategory, setActiveCategory] = useState<SemanticCategory>('HOME')
  const [selectedMetric, setSelectedMetric] = useState<Metric | null>(null)
  const [selectedWordRoot, setSelectedWordRoot] = useState<WordRoot | null>(null)

  const categories: CategoryConfig[] = useMemo(
    () => [
      {
        id: 'METRICS',
        label: '指标中心',
        icon: Target,
        color: 'bg-purple-600',
        description: '统一业务口径，沉淀企业原子指标与派生指标体系。',
        count: 0,
        trend: 'NEW'
      },
      {
        id: 'GLOSSARY',
        label: '规范词根',
        icon: Book,
        color: 'bg-blue-600',
        description: '数据标准化命名的基石，规范字段语义与物理命名。',
        count: 0
      },
      {
        id: 'STANDARDS',
        label: '标准规范',
        icon: Shield,
        color: 'bg-emerald-600',
        description: '数据类型标准、值域约束及分级分类安全规范。',
        count: 12
      },
      {
        id: 'MODELS',
        label: 'AI 特征',
        icon: Cpu,
        color: 'bg-orange-500',
        description: '模型特征库与特征血缘，加速 AI 场景数据供给。',
        count: 8
      },
      {
        id: 'APIS',
        label: '数据服务',
        icon: Globe,
        color: 'bg-cyan-500',
        description: 'Data-as-a-Service, 统一管理 API 服务元数据。',
        count: 24
      }
    ],
    []
  )

  const goBack = () => setActiveCategory('HOME')

  const renderHome = () => (
    <div className="flex-1 overflow-auto p-4 @md:p-6 @xl:p-8 custom-scrollbar">
      <div className="animate-in fade-in duration-500">
        <div className="mb-6 @md:mb-8">
          <h1 className="text-heading @md:text-title @xl:text-display font-black text-slate-900 dark:text-slate-100 tracking-tight">
            One Meta <span className="text-blue-600">Semantic</span>
          </h1>
          <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body">企业级语义资产湖，连接业务定义与物理数据。</p>
        </div>

        <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-3 gap-4 @md:gap-6">
          {categories.map((cat) => (
            <HubAssetCard key={cat.id} config={cat} onClick={() => setActiveCategory(cat.id)} />
          ))}
          <div className="@md:col-span-2 @xl:col-span-3 bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 rounded-xl @md:rounded-2xl p-6 @md:p-8 flex items-center justify-between text-white shadow-lg overflow-hidden relative group">
            <div className="relative z-10 max-w-lg">
              <h3 className="text-body @md:text-subtitle @xl:text-title font-bold mb-2 @md:mb-3">想让 AI 更懂你的数据？</h3>
              <p className="text-blue-100 mb-4 @md:mb-6 text-caption @md:text-body-sm opacity-90">
                通过 One Meta 完善表、指标等元数据，可大幅提升企业级 AI Agent 的查询准确率与报告自动生成质量。
              </p>
              <button className="bg-white text-blue-600 font-semibold px-4 @md:px-6 py-2 @md:py-2.5 rounded-lg @md:rounded-xl shadow-md hover:scale-105 transition-all flex items-center gap-2 text-caption @md:text-body-sm">
                <Zap size={iconSizeToken.medium} /> 立即开启语义增强
              </button>
            </div>
            <div className="opacity-10 absolute right-0 top-0 bottom-0 pointer-events-none group-hover:scale-110 transition-transform duration-1000 hidden @md:block">
              <Workflow size={320} className="translate-x-24 -translate-y-8" />
            </div>
          </div>
        </div>
      </div>
    </div>
  )

  const renderContent = () => {
    switch (activeCategory) {
      case 'HOME':
        return renderHome()
      case 'METRICS':
        return <MetricExplorer onBack={goBack} onOpenDrawer={setSelectedMetric} />
      case 'GLOSSARY':
        return <WordRootExplorer onBack={goBack} onOpenDrawer={setSelectedWordRoot} />
      case 'STANDARDS':
        return <StandardsExplorer onBack={goBack} />
      case 'MODELS':
        return <ModelsExplorer onBack={goBack} />
      case 'APIS':
        return <ApisExplorer onBack={goBack} />
      default:
        return renderHome()
    }
  }

  return (
    <div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      {renderContent()}
      {selectedMetric && <MetricOverview metric={selectedMetric} onClose={() => setSelectedMetric(null)} />}
      {selectedWordRoot && <WordRootOverview wordRoot={selectedWordRoot} onClose={() => setSelectedWordRoot(null)} />}
    </div>
  )
}
