import { type ReactNode } from 'react'
import { BarChart3, Database, Layers, Table as TableIcon } from 'lucide-react'
import { Card, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'
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
    <div className="flex-1 overflow-auto custom-scrollbar">
      <div className={`p-4 @md:p-6 @xl:p-8 space-y-6 @md:space-y-8 ${contentMaxWidthClassMap.full} mx-auto`}>
        <div className="space-y-2">
          <div className={`flex items-center gap-2 ${TYPOGRAPHY.legal} uppercase tracking-widest text-slate-400 dark:text-slate-500`}>
            <span className="flex items-center gap-1">
              <Layers size={12} className="text-indigo-500" />
              {catalog.name}
            </span>
            <span className="text-slate-300">/</span>
            <span className="font-semibold text-slate-600 dark:text-slate-300">{schema.name}</span>
          </div>
          <h2 className="text-heading @md:text-title @xl:text-display font-black text-slate-900 dark:text-slate-100 tracking-tight">
            Schema Overview
          </h2>
          <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body">
            Assets and quality signals for schema <span className="font-semibold text-slate-700 dark:text-slate-200">{schema.name}</span>.
          </p>
        </div>

        <div className="grid grid-cols-1 @md:grid-cols-3 gap-4 @md:gap-6">
          <StatCard icon={<Database size={18} className="text-blue-600" />} label="Tables" value={totalTables.toString()} />
          <StatCard icon={<TableIcon size={18} className="text-indigo-600" />} label="Columns" value={totalColumns.toString()} />
          <StatCard icon={<BarChart3 size={18} className="text-green-600" />} label="Daily Scans" value="—" helper="Connect to jobs to view" />
        </div>

        <Table minWidth="none">
          <TableHeader className="bg-slate-50 dark:bg-slate-800/40">
            <TableRow>
              <TableHead colSpan={2} className="px-6 py-4 normal-case tracking-normal">
                <div className="flex items-center gap-2 text-body-sm font-semibold text-slate-800 dark:text-slate-100">
                  <TableIcon size={16} className="text-indigo-500" />
                  Tables
                </div>
              </TableHead>
            </TableRow>
          </TableHeader>

          <TableBody className="divide-y divide-slate-100 dark:divide-slate-800">
            {schema.tables.map((table) => (
              <TableRow
                key={table.id}
                className="hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
              >
                <TableCell className="px-6 py-4 align-top">
                  <div className="space-y-1">
                    <p className="text-body-sm font-semibold text-slate-800 dark:text-slate-100">{table.name}</p>
                    <p className="text-caption text-slate-500 dark:text-slate-400 line-clamp-2">{table.description}</p>
                    <div className={`flex items-center gap-3 ${TYPOGRAPHY.legal} text-slate-500 dark:text-slate-400`}>
                      <span>{table.rowCount.toLocaleString()} rows</span>
                      <span className="h-3 w-px bg-slate-200 dark:bg-slate-700" />
                      <span>{table.columns.length} cols</span>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="px-6 py-4 align-top text-right">
                  <span className="text-legal font-semibold text-indigo-600 dark:text-indigo-400">
                    {table.certification ?? 'BRONZE'}
                  </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
    <Card>
      <div className="flex items-center gap-3">
        <div className="p-2 rounded-md bg-slate-50 dark:bg-slate-800">{icon}</div>
        <div>
          <p className={`${TYPOGRAPHY.caption} uppercase tracking-widest text-slate-500 dark:text-slate-400`}>{label}</p>
          <p className="text-xl font-bold text-slate-900 dark:text-white">{value}</p>
          {helper && <p className={`${TYPOGRAPHY.legal} text-slate-400 mt-1`}>{helper}</p>}
        </div>
      </div>
    </Card>
  )
}
