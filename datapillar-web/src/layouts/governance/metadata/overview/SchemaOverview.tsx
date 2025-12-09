import { type ReactNode } from 'react'
import { BarChart3, Database, Layers, Table as TableIcon } from 'lucide-react'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { type CatalogAsset, type SchemaAsset } from '../type/types'

type SchemaOverviewProps = {
  schema: SchemaAsset
  catalog: CatalogAsset
}

export function SchemaOverview({ schema, catalog }: SchemaOverviewProps) {
  const totalTables = schema.tables.length
  const totalColumns = schema.tables.reduce((sum, table) => sum + table.columns.length, 0)

  return (
    <div className="flex-1 overflow-y-auto scrollbar-invisible">
      <div className={`p-6 @md:p-8 space-y-6 ${contentMaxWidthClassMap.full} mx-auto`}>
        <div className="space-y-2">
          <div className={`flex items-center gap-2 ${TYPOGRAPHY.legal} uppercase tracking-widest text-slate-400 dark:text-slate-500`}>
            <span className="flex items-center gap-1">
              <Layers size={12} className="text-indigo-500" />
              {catalog.name}
            </span>
            <span className="text-slate-300">/</span>
            <span className="font-semibold text-slate-600 dark:text-slate-300">{schema.name}</span>
          </div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-white">Schema Overview</h2>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Assets and quality signals for schema <span className="font-semibold text-slate-700 dark:text-slate-200">{schema.name}</span>.
          </p>
        </div>

        <div className="grid grid-cols-1 @md:grid-cols-3 gap-4">
          <StatCard icon={<Database size={18} className="text-blue-600" />} label="Tables" value={totalTables.toString()} />
          <StatCard icon={<TableIcon size={18} className="text-indigo-600" />} label="Columns" value={totalColumns.toString()} />
          <StatCard icon={<BarChart3 size={18} className="text-green-600" />} label="Daily Scans" value="—" helper="Connect to jobs to view" />
        </div>

        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-slate-50 dark:bg-slate-800/40">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
              <TableIcon size={16} className="text-indigo-500" />
              Tables
            </div>
          </div>
          <div className="divide-y divide-slate-100 dark:divide-slate-800">
            {schema.tables.map((table) => (
              <div key={table.id} className="px-6 py-4 flex items-start justify-between gap-4 hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors">
                <div className="space-y-1">
                  <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">{table.name}</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 line-clamp-2">{table.description}</p>
                  <div className={`flex items-center gap-3 ${TYPOGRAPHY.legal} text-slate-500 dark:text-slate-400`}>
                    <span>{table.rowCount.toLocaleString()} rows</span>
                    <span className="h-3 w-px bg-slate-200 dark:bg-slate-700" />
                    <span>{table.columns.length} cols</span>
                  </div>
                </div>
                <span className="text-xs font-semibold text-indigo-600">{table.certification ?? 'BRONZE'}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

function StatCard({
  icon,
  label,
  value,
  helper
}: {
  icon: ReactNode
  label: string
  value: string
  helper?: string
}) {
  return (
    <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-5 shadow-sm">
      <div className="flex items-center gap-3">
        <div className="p-2 rounded-md bg-slate-50 dark:bg-slate-800">{icon}</div>
        <div>
          <p className={`${TYPOGRAPHY.caption} uppercase tracking-widest text-slate-500 dark:text-slate-400`}>{label}</p>
          <p className="text-xl font-bold text-slate-900 dark:text-white">{value}</p>
          {helper && <p className={`${TYPOGRAPHY.legal} text-slate-400 mt-1`}>{helper}</p>}
        </div>
      </div>
    </div>
  )
}
