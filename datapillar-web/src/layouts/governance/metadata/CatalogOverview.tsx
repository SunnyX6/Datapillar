import { BarChart3, Database, Layers, ShieldCheck, Table as TableIcon } from 'lucide-react'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { type CatalogAsset } from './types'

const INGESTION_BARS = [
  { name: 'Hive', width: 'w-3/4', tone: 'bg-blue-500', rows: '148k rows' },
  { name: 'Iceberg', width: 'w-7/12', tone: 'bg-indigo-500', rows: '112k rows' },
  { name: 'MySQL', width: 'w-2/5', tone: 'bg-purple-500', rows: '82k rows' },
  { name: 'Kafka', width: 'w-1/3', tone: 'bg-amber-500', rows: '64k rows' },
  { name: 'Postgres', width: 'w-1/4', tone: 'bg-slate-500', rows: '48k rows' }
] as const

type CatalogOverviewProps = {
  catalog: CatalogAsset
}

export function CatalogOverview({ catalog }: CatalogOverviewProps) {
  const schemaCount = catalog.schemas.length
  const tableCount = catalog.schemas.reduce((sum, schema) => sum + schema.tables.length, 0)
  const columnCount = catalog.schemas.reduce(
    (sum, schema) => sum + schema.tables.reduce((acc, table) => acc + table.columns.length, 0),
    0
  )

  const stats = [
    { label: 'Schemas', value: schemaCount, sub: catalog.name, icon: Layers, color: 'text-indigo-600', bg: 'bg-indigo-50' },
    { label: 'Tables', value: tableCount.toLocaleString(), sub: 'Across schemas', icon: TableIcon, color: 'text-blue-600', bg: 'bg-blue-50' },
    { label: 'Columns', value: columnCount.toLocaleString(), sub: 'Aggregated', icon: Database, color: 'text-purple-600', bg: 'bg-purple-50' },
    { label: 'Avg Quality', value: '94%', sub: 'Excellent', icon: ShieldCheck, color: 'text-green-600', bg: 'bg-green-50' }
  ] as const

  return (
    <div className="flex-1 overflow-y-auto scrollbar-invisible">
      <div className={`p-6 @md:p-8 space-y-6 ${contentMaxWidthClassMap.full} mx-auto`}>
        <div className="space-y-1">
          <h2 className="text-xl font-bold text-slate-900 dark:text-white">Catalog Overview</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Assets and quality signals for catalog <span className="font-semibold text-slate-700 dark:text-slate-200">{catalog.name}</span>.
          </p>
        </div>

        <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-4 gap-4">
          {stats.map((stat) => (
            <div
              key={stat.label}
              className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-4 shadow-sm"
            >
              <div className="flex items-start justify-between">
                <div className="space-y-1">
                  <p className={`${TYPOGRAPHY.legal} font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400`}>{stat.label}</p>
                  <p className="text-2xl font-bold text-slate-900 dark:text-white">{stat.value}</p>
                  <p className="text-xs text-slate-400">{stat.sub}</p>
                </div>
                <div className={`p-2 rounded-md ${stat.bg} ${stat.color}`}>
                  <stat.icon size={18} />
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-6 shadow-sm">
          <div className="flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100 mb-6">
            <BarChart3 size={16} className="text-slate-400" />
            Ingestion Volume (Last 24h)
          </div>
          <div className="space-y-3">
            {INGESTION_BARS.map((bar) => (
              <div key={bar.name} className="space-y-1">
                <div className="flex items-center justify-between text-xs text-slate-500">
                  <span>{bar.name}</span>
                  <span className="text-slate-400">+{bar.rows}</span>
                </div>
                <div className="h-2 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                  <div className={`h-full ${bar.width} ${bar.tone} rounded-full`} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
