import { useEffect, useState, useMemo } from 'react'
import {
  Activity,
  Book,
  CheckCircle2,
  Clock,
  Fingerprint,
  Key,
  Lock,
  Medal,
  Table as TableIcon,
  User
} from 'lucide-react'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { type TableAsset } from '../type/types'
import { getTable } from '@/services/oneMetaService'
import type { GravitinoIndexDTO } from '@/types/oneMeta'

const QUALITY_RULES = [
  { name: 'unique_region_id', type: 'Uniqueness', status: 'PASS', value: '100%' },
  { name: 'amount_positive', type: 'Validity', status: 'PASS', value: '100%' }
] as const

const QUALITY_BADGE_COLOR: Record<'PASS', string> = {
  PASS: 'text-green-600 bg-green-50'
}

const QUALITY_SCORE_COLOR = (score: number) => {
  if (score < 70) return 'bg-rose-100 text-rose-700 border-rose-200'
  if (score < 90) return 'bg-amber-100 text-amber-700 border-amber-200'
  return 'bg-emerald-100 text-emerald-700 border-emerald-200'
}

// 格式化字节大小
const formatBytes = (bytes: number): string => {
  if (isNaN(bytes) || bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

type TableOverviewProps = {
  table: TableAsset
  provider?: string
  breadcrumb: string[]
  activeTab: 'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE'
  onTabChange: (tab: 'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE') => void
}

/**
 * 判断是否为支持索引的 JDBC 类型 catalog
 */
function isJdbcProvider(provider?: string): boolean {
  if (!provider) return false
  return provider.startsWith('jdbc-')
}

export function TableOverview({ table, provider, breadcrumb, activeTab, onTabChange }: TableOverviewProps) {
  const [owner, setOwner] = useState<string>(table.owner)
  const [updatedAt, setUpdatedAt] = useState<string>(table.updatedAt)
  const [description, setDescription] = useState<string>(table.description)
  const [columns, setColumns] = useState(table.columns)
  const [indexes, setIndexes] = useState<GravitinoIndexDTO[]>([])
  // 动态展示的属性，根据不同 catalog 返回的 properties 动态生成
  const [tableSpecs, setTableSpecs] = useState<{ label: string; value: string }[]>([])

  // 构建列名到索引类型的映射（仅对 JDBC 类型的 catalog 有效）
  const columnIndexMap = useMemo(() => {
    const map = new Map<string, Set<'PRIMARY_KEY' | 'UNIQUE_KEY'>>()
    if (!isJdbcProvider(provider)) return map

    indexes.forEach((index) => {
      index.fieldNames.forEach((fieldNamePath) => {
        // fieldNames 是二维数组，取第一个元素作为列名
        const columnName = fieldNamePath[0]
        if (!map.has(columnName)) {
          map.set(columnName, new Set())
        }
        map.get(columnName)!.add(index.indexType)
      })
    })
    return map
  }, [indexes, provider])

  useEffect(() => {
    // 从table.id解析catalogName, schemaName, tableName
    const parts = table.id.split('.')
    if (parts.length === 3) {
      const [catalogName, schemaName, tableName] = parts

      // 调用 getTable 获取完整数据
      getTable(catalogName, schemaName, tableName)
        .then((detail) => {
          // 更新description
          if (detail.comment) {
            setDescription(detail.comment)
          }

          // 更新columns
          if (detail.columns) {
            setColumns(detail.columns)
          }

          // 更新indexes（仅对 JDBC 类型有效）
          if (detail.indexes) {
            setIndexes(detail.indexes)
          }

          // 从audit中提取owner和updatedAt
          if (detail.audit) {
            if (detail.audit.creator) {
              setOwner(detail.audit.creator)
            }
            if (detail.audit.lastModifiedTime) {
              const date = new Date(detail.audit.lastModifiedTime)
              setUpdatedAt(date.toLocaleDateString())
            }
          }

          // 根据 properties 动态构建展示字段
          if (detail.properties) {
            const props = detail.properties
            const specs: { label: string; value: string }[] = []

            // Hive 特有: table-type
            if (props['table-type']) {
              specs.push({ label: 'Type', value: props['table-type'] })
            }

            // Hive 特有: format (从 input-format 推断)
            const inputFormat = props['input-format'] || ''
            if (inputFormat) {
              let format = '-'
              if (inputFormat.toLowerCase().includes('parquet')) {
                format = 'PARQUET'
              } else if (inputFormat.toLowerCase().includes('orc')) {
                format = 'ORC'
              } else if (inputFormat.toLowerCase().includes('text')) {
                format = 'TEXT'
              } else if (inputFormat.toLowerCase().includes('avro')) {
                format = 'AVRO'
              } else {
                const parts = inputFormat.split('.')
                format = parts[parts.length - 1].replace('InputFormat', '').toUpperCase()
              }
              specs.push({ label: 'Format', value: format })
            }

            // MySQL 特有: engine
            if (props['engine']) {
              specs.push({ label: 'Engine', value: props['engine'] })
            }

            // 通用: numRows
            if (props.numRows) {
              const rows = parseInt(props.numRows, 10)
              specs.push({ label: 'Rows', value: isNaN(rows) ? '-' : rows.toLocaleString() })
            }

            // 通用: rawDataSize (数据大小)
            if (props.rawDataSize) {
              specs.push({ label: 'Data Size', value: formatBytes(parseInt(props.rawDataSize, 10)) })
            }

            // MySQL 特有: indexSize
            if (props.indexSize && props.indexSize !== '0') {
              specs.push({ label: 'Index Size', value: formatBytes(parseInt(props.indexSize, 10)) })
            }

            // 通用: totalSize
            if (props.totalSize) {
              specs.push({ label: 'Total Size', value: formatBytes(parseInt(props.totalSize, 10)) })
            }

            // Hive 特有: numFiles
            if (props.numFiles) {
              const files = parseInt(props.numFiles, 10)
              specs.push({ label: 'Files', value: isNaN(files) ? '-' : files.toLocaleString() })
            }

            setTableSpecs(specs)
          }
        })
        .catch((error) => {
          console.error('获取table详情失败:', error)
        })
    }
  }, [table.id])

  return (
    <div className="flex flex-col h-full">
      <div className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-6 @md:px-8 py-4 shadow-sm">
        <div className={`flex items-center gap-2 ${TYPOGRAPHY.legal} uppercase tracking-widest text-slate-400 dark:text-slate-500 mb-3`}>
          {breadcrumb.map((crumb, index) => (
            <span key={crumb} className="flex items-center gap-2">
              {index > 0 && <span className="text-slate-300">/</span>}
              <span className="hover:text-indigo-600 cursor-pointer">{crumb}</span>
            </span>
          ))}
        </div>

        <div className="flex flex-col gap-4 @md:flex-row @md:items-start @md:justify-between">
          <div className="flex gap-4">
            <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 text-white flex items-center justify-center shadow-md shadow-blue-200 dark:shadow-blue-900/40">
              <TableIcon size={26} />
            </div>
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                <h2 className="text-xl font-bold text-slate-900 dark:text-white">{table.name}</h2>
                {table.certification && (
                  <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full ${TYPOGRAPHY.micro} font-bold uppercase border border-amber-200 bg-amber-50 text-amber-600`}>
                    <Medal size={10} />
                    {table.certification}
                  </span>
                )}
              </div>
              <p className="text-sm text-slate-500 dark:text-slate-400 max-w-3xl leading-relaxed">
                {description}
              </p>
              <div className="flex flex-wrap items-center gap-4 text-xs text-slate-600 dark:text-slate-300">
                <QualityBadge score={table.qualityScore} />
                <div className="h-4 w-px bg-slate-200 dark:bg-slate-700" />
                <div className="flex items-center gap-2">
                  <User size={12} className="text-slate-400" />
                  <span>Owner: <span className="font-semibold text-slate-800 dark:text-slate-200">{owner || '-'}</span></span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock size={12} className="text-slate-400" />
                  <span>Updated: <span className="font-semibold text-slate-800 dark:text-slate-200">{updatedAt || '-'}</span></span>
                </div>
              </div>
              <div className="flex items-center flex-wrap gap-2">
                {table.domains.map((d) => (
                  <span
                    key={d}
                    className={`px-2 py-1 ${TYPOGRAPHY.legal} rounded-lg bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 font-medium text-slate-600 dark:text-slate-200`}
                  >
                    {d}
                  </span>
                ))}
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <button className="px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700 text-sm font-medium text-slate-600 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800">
              Share
            </button>
            <button className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium shadow-sm">
              Query
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-4 @md:px-8">
        <div className="flex gap-1">
          {(['OVERVIEW', 'COLUMNS', 'QUALITY', 'LINEAGE'] as const).map((tab) => (
            <button
              key={tab}
              type="button"
              onClick={() => onTabChange(tab)}
              className={`px-4 py-3 text-sm font-semibold border-b-2 transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
            >
              {tab.charAt(0) + tab.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-invisible">
        <div className={`p-6 @md:p-8 ${contentMaxWidthClassMap.full} mx-auto space-y-6`}>
          {activeTab === 'OVERVIEW' && (
            <div className="space-y-6">
              <div className="grid grid-cols-1 @md:grid-cols-3 gap-4">
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-5 shadow-sm">
                  <h4 className={`${TYPOGRAPHY.legal} font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-4`}>Technical Specs</h4>
                  <div className="space-y-3 text-sm text-slate-600 dark:text-slate-300">
                    {tableSpecs.length > 0 ? (
                      tableSpecs.map((spec) => (
                        <SpecRow key={spec.label} label={spec.label} value={spec.value} />
                      ))
                    ) : (
                      <span className="text-slate-400">Loading...</span>
                    )}
                  </div>
                </div>
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-5 shadow-sm @md:col-span-2">
                  <h4 className={`${TYPOGRAPHY.legal} font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-4`}>Governance & Usage</h4>
                  <div className="grid grid-cols-3 gap-4">
                    <UsageStat label="Weekly Queries" value="1.2k" />
                    <UsageStat label="Downstream Jobs" value="42" />
                    <UsageStat label="SLA Met" value="99.9%" tone="text-green-600" />
                  </div>
                </div>
              </div>

              <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden shadow-sm">
                <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-slate-50 dark:bg-slate-800/50">
                  <div className="flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
                    <Book size={16} className="text-slate-400" />
                    Documentation
                  </div>
                  <button className="text-xs text-blue-600 hover:underline">Edit</button>
                </div>
                <div className="p-6 text-sm text-slate-600 dark:text-slate-300 space-y-3 leading-relaxed">
                  <p>
                    This dataset contains the consolidated monthly revenue figures aggregated from regional sales marts.
                    It is the <strong>official</strong> source for Executive Quarterly Business Reviews (QBR).
                  </p>
                  <h4 className="text-slate-800 dark:text-slate-100 font-semibold">Key Business Rules</h4>
                  <ul className="list-disc pl-5 space-y-1">
                    <li>Revenue is recognized upon shipment, not order placement.</li>
                    <li>All currencies are converted to USD using the daily spot rate at <code className={`px-1 py-0.5 bg-slate-100 dark:bg-slate-800 rounded ${TYPOGRAPHY.legal}`}>close_of_business</code>.</li>
                    <li>Returns are processed in <code className={`px-1 py-0.5 bg-slate-100 dark:bg-slate-800 rounded ${TYPOGRAPHY.legal}`}>fact_returns</code> and must be joined for net revenue.</li>
                  </ul>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'COLUMNS' && (
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 dark:bg-slate-800 text-slate-500 dark:text-slate-300 font-semibold border-b border-slate-200 dark:border-slate-700">
                  <tr>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Column Name</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Data Type</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Description</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Tags</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {columns.map((col) => {
                    const indexTypes = columnIndexMap.get(col.name)
                    const isPrimaryKey = indexTypes?.has('PRIMARY_KEY') ?? false
                    const isUniqueKey = indexTypes?.has('UNIQUE_KEY') ?? false

                    return (
                      <tr key={col.name} className="hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors">
                        <td className="px-6 py-3 font-mono text-xs text-slate-800 dark:text-slate-100">
                          <div className="flex items-center gap-2">
                            {col.name}
                            {isPrimaryKey && (
                              <span title="Primary Key">
                                <Key size={12} className="text-amber-500" />
                              </span>
                            )}
                            {isUniqueKey && !isPrimaryKey && (
                              <span title="Unique Key">
                                <Fingerprint size={12} className="text-blue-500" />
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-6 py-3 font-mono text-xs text-slate-500 dark:text-slate-300">{col.type}</td>
                        <td className="px-6 py-3 text-slate-600 dark:text-slate-300">{col.comment ?? '-'}</td>
                        <td className="px-6 py-3">
                          {col.piiTag && (
                            <span className={`inline-flex items-center gap-1 px-2 py-1 rounded ${TYPOGRAPHY.micro} font-bold bg-rose-50 text-rose-600 border border-rose-100`}>
                              <Lock size={10} />
                              {col.piiTag}
                            </span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          {activeTab === 'QUALITY' && (
            <div className="space-y-6">
              <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl p-6 shadow-sm">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-slate-800 dark:text-slate-100">Quality Metrics Trend</h3>
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400 px-2 py-1 bg-slate-100 dark:bg-slate-800 rounded">Last 30 Days</span>
                </div>
                <div className="h-56 rounded-lg border border-dashed border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60 flex items-center justify-center text-sm text-slate-400 dark:text-slate-500">
                  Trend chart placeholder
                </div>
              </div>

              <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm overflow-hidden">
                <table className="w-full text-sm">
                <thead className="bg-slate-50 dark:bg-slate-800 text-slate-500 dark:text-slate-300 font-semibold">
                  <tr>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Rule Name</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Type</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Status</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Value</th>
                    <th className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>History</th>
                  </tr>
                </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {QUALITY_RULES.map((rule) => (
                      <tr key={rule.name} className="hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors">
                        <td className="px-6 py-4 font-medium text-slate-700 dark:text-slate-200">{rule.name}</td>
                        <td className="px-6 py-4 text-slate-500 dark:text-slate-300">{rule.type}</td>
                        <td className="px-6 py-4">
                          <span className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded ${QUALITY_BADGE_COLOR[rule.status]}`}>
                            <CheckCircle2 size={12} />
                            {rule.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 font-mono text-slate-600 dark:text-slate-200">{rule.value}</td>
                        <td className="px-6 py-4">
                          <div className="flex items-end gap-0.5">
                            {Array.from({ length: 8 }).map((_, idx) => (
                              <div key={idx} className="w-1 h-4 rounded-sm bg-emerald-400" />
                            ))}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {activeTab === 'LINEAGE' && (
            <div className="h-[420px] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm flex items-center justify-center relative overflow-hidden">
              <div className="absolute inset-0 bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] dark:bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:18px_18px] opacity-50" />
              <div className="z-10 text-sm text-slate-500 dark:text-slate-400">Lineage graph placeholder</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function SpecRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-slate-500 dark:text-slate-400">{label}</span>
      <span className="font-medium text-slate-800 dark:text-slate-100">{value}</span>
    </div>
  )
}

function UsageStat({ label, value, tone = 'text-slate-800' }: { label: string; value: string; tone?: string }) {
  return (
    <div className="text-center p-3 bg-slate-50 dark:bg-slate-800/60 rounded-lg border border-slate-200 dark:border-slate-700">
      <div className={`text-2xl font-bold ${tone}`}>{value}</div>
      <div className="text-xs text-slate-500 dark:text-slate-400 mt-1">{label}</div>
    </div>
  )
}

function QualityBadge({ score }: { score: number }) {
  return (
    <div className={`flex items-center gap-1.5 px-3 py-1 rounded-md border text-xs font-semibold ${QUALITY_SCORE_COLOR(score)}`}>
      <Activity size={12} />
      <span>{score} / 100 Quality</span>
    </div>
  )
}
