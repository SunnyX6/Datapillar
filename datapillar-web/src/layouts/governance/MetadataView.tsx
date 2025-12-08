import { useMemo, useRef, useState } from 'react'
import { Database, FolderTree, Menu, Search, Server, Table as TableIcon } from 'lucide-react'
import { CatalogOverview } from './metadata/CatalogOverview'
import { Overview } from './metadata/Overview'
import { SchemaOverview } from './metadata/SchemaOverview'
import { TableOverview } from './metadata/TableOverview'
import { type CatalogAsset, type NodeType, type SchemaAsset, type TableAsset } from './metadata/types'
import { panelWidthClassMap } from '@/design-tokens/dimensions'

const MOCK_CATALOGS: CatalogAsset[] = [
  {
    id: 'catalog-prod-hive',
    name: 'prod_hive_dw',
    schemas: [
      {
        id: 'schema-finance',
        name: 'finance_mart',
        catalogId: 'catalog-prod-hive',
        tables: [
          {
            id: 'table-fact-revenue',
            name: 'fact_monthly_revenue',
            description:
              'Consolidated monthly revenue facts aggregated from regional marts; primary source for executive QBR reporting.',
            certification: 'GOLD',
            qualityScore: 94,
            rowCount: 25340000,
            size: '420 GB',
            owner: 'Hugo Liang',
            updatedAt: '2024-12-10',
            domains: ['Finance', 'Revenue', 'Executive'],
            columns: [
              { name: 'month_id', type: 'STRING', isPrimaryKey: true, comment: 'YYYYMM partition key' },
              { name: 'region_id', type: 'STRING', comment: 'Region identifier aligned to sales hierarchy' },
              { name: 'product_sku', type: 'STRING', comment: 'Standardized SKU code' },
              { name: 'gross_revenue', type: 'DECIMAL(18,2)', comment: 'Gross revenue before returns' },
              {
                name: 'net_revenue',
                type: 'DECIMAL(18,2)',
                comment: 'Net revenue after returns',
                piiTag: 'FINANCE_SENSITIVE'
              },
              { name: 'currency', type: 'STRING', comment: '3-letter ISO currency code' },
              { name: 'updated_at', type: 'TIMESTAMP', comment: 'Last successful ingestion timestamp' }
            ]
          },
          {
            id: 'table-dim-region',
            name: 'dim_region',
            description:
              'Reference dimension for region hierarchy used across finance mart; includes geo + sales alignment.',
            certification: 'SILVER',
            qualityScore: 87,
            rowCount: 2400,
            size: '12 MB',
            owner: 'Ada Qi',
            updatedAt: '2024-12-06',
            domains: ['Finance', 'Sales'],
            columns: [
              { name: 'region_id', type: 'STRING', isPrimaryKey: true, comment: 'Region code' },
              { name: 'region_name', type: 'STRING', comment: 'Readable region name' },
              { name: 'country', type: 'STRING' },
              { name: 'director', type: 'STRING', comment: 'Regional leader' }
            ]
          }
        ]
      },
      {
        id: 'schema_growth',
        name: 'growth_ops',
        catalogId: 'catalog-prod-hive',
        tables: [
          {
            id: 'table-fact-events',
            name: 'fact_product_events',
            description: 'Unified product interaction events used by growth experiments and attribution models.',
            certification: 'BRONZE',
            qualityScore: 72,
            rowCount: 188000000,
            size: '1.8 TB',
            owner: 'Wei Sun',
            updatedAt: '2024-12-08',
            domains: ['Growth', 'Product'],
            columns: [
              { name: 'event_id', type: 'BIGINT', isPrimaryKey: true },
              { name: 'user_id', type: 'BIGINT', comment: 'Anonymized user id', piiTag: 'PII_HASH' },
              { name: 'event_name', type: 'STRING' },
              { name: 'event_ts', type: 'TIMESTAMP' },
              { name: 'device', type: 'STRING' },
              { name: 'country', type: 'STRING' }
            ]
          }
        ]
      }
    ]
  },
  {
    id: 'catalog-iceberg',
    name: 'iceberg_marketing',
    schemas: [
      {
        id: 'schema-ads',
        name: 'ads_reporting',
        catalogId: 'catalog-iceberg',
        tables: [
          {
            id: 'table-fact-ads',
            name: 'fact_ads_spend',
            description: 'Daily ad spend facts aggregated by campaign and channel.',
            certification: 'SILVER',
            qualityScore: 90,
            rowCount: 9200000,
            size: '210 GB',
            owner: 'Sara Chen',
            updatedAt: '2024-12-09',
            domains: ['Marketing', 'Spend'],
            columns: [
              { name: 'date', type: 'DATE', isPrimaryKey: true },
              { name: 'campaign_id', type: 'STRING', isPrimaryKey: true },
              { name: 'channel', type: 'STRING' },
              { name: 'spend', type: 'DECIMAL(18,2)' },
              { name: 'impressions', type: 'BIGINT' },
              { name: 'clicks', type: 'BIGINT' }
            ]
          }
        ]
      }
    ]
  }
]

const INDENT_PX = 18

export function MetadataView() {
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(() => {
    const initial = new Set<string>(['ROOT'])
    const firstCatalog = MOCK_CATALOGS[0]
    if (firstCatalog) {
      initial.add(firstCatalog.id)
      const firstSchema = firstCatalog.schemas[0]
      if (firstSchema) initial.add(firstSchema.id)
    }
    return initial
  })
  const [selectedNodeId, setSelectedNodeId] = useState<string>('ROOT')
  const [selectedNodeType, setSelectedNodeType] = useState<NodeType>('ROOT')
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE'>('OVERVIEW')
  const [searchValue, setSearchValue] = useState('')
  const [isSearchOpen, setIsSearchOpen] = useState(false)
  const searchInputRef = useRef<HTMLInputElement | null>(null)
  const [isSideCollapsed, setIsSideCollapsed] = useState(false)

  const tableLookup = useMemo(() => {
    const map = new Map<string, { table: TableAsset; schema: SchemaAsset; catalog: CatalogAsset }>()
    MOCK_CATALOGS.forEach((catalog) => {
      catalog.schemas.forEach((schema) => {
        schema.tables.forEach((table) => map.set(table.id, { table, schema, catalog }))
      })
    })
    return map
  }, [])

  const catalogLookup = useMemo(() => {
    const map = new Map<string, CatalogAsset>()
    MOCK_CATALOGS.forEach((catalog) => map.set(catalog.id, catalog))
    return map
  }, [])

  const schemaLookup = useMemo(() => {
    const map = new Map<string, { schema: SchemaAsset; catalog: CatalogAsset }>()
    MOCK_CATALOGS.forEach((catalog) => {
      catalog.schemas.forEach((schema) => map.set(schema.id, { schema, catalog }))
    })
    return map
  }, [])

  const filteredCatalogs = useMemo(() => {
    const query = searchValue.trim().toLowerCase()
    if (!query) return MOCK_CATALOGS

    return MOCK_CATALOGS.map((catalog) => {
      const filteredSchemas = catalog.schemas
        .map((schema) => {
          const filteredTables = schema.tables.filter((table) => table.name.toLowerCase().includes(query))
          const matchSchema = schema.name.toLowerCase().includes(query)
          const matchCatalog = catalog.name.toLowerCase().includes(query)
          if (filteredTables.length || matchSchema || matchCatalog) {
            return { ...schema, tables: filteredTables.length ? filteredTables : schema.tables }
          }
          return null
        })
        .filter(Boolean) as SchemaAsset[]

      if (filteredSchemas.length) {
        return { ...catalog, schemas: filteredSchemas }
      }
      return null
    }).filter(Boolean) as CatalogAsset[]
  }, [searchValue])

  const selectedTable = selectedNodeType === 'TABLE' ? tableLookup.get(selectedNodeId) : null
  const selectedSchema = selectedNodeType === 'SCHEMA' ? schemaLookup.get(selectedNodeId) : null
  const selectedCatalog = selectedNodeType === 'CATALOG' ? catalogLookup.get(selectedNodeId) : null

  const handleSelect = (id: string, type: NodeType) => {
    setSelectedNodeId(id)
    setSelectedNodeType(type)
    setActiveTab('OVERVIEW')
  }

  const toggleNode = (id: string) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <div className="relative flex w-full h-full bg-white dark:bg-slate-900 @container">
      <section className="flex-1 min-w-0 flex flex-col overflow-hidden">
        {selectedNodeType === 'TABLE' && selectedTable ? (
          <TableOverview
            activeTab={activeTab}
            onTabChange={setActiveTab}
            table={selectedTable.table}
            breadcrumb={[
              selectedTable.catalog.name,
              selectedTable.schema.name,
              selectedTable.table.name
            ]}
          />
        ) : selectedNodeType === 'SCHEMA' && selectedSchema ? (
          <SchemaOverview schema={selectedSchema.schema} catalog={selectedSchema.catalog} />
        ) : selectedNodeType === 'CATALOG' && selectedCatalog ? (
          <CatalogOverview catalog={selectedCatalog} />
        ) : (
          <Overview />
        )}
      </section>

      {isSideCollapsed && (
        <button
          type="button"
          aria-label="展开元数据侧栏"
          className="absolute right-4 top-4 z-40 p-2 text-slate-600 hover:text-indigo-600 transition-colors"
          onClick={() => setIsSideCollapsed(false)}
        >
          <Menu size={18} />
        </button>
      )}

      <aside
        className={`flex-shrink-0 h-full bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-xl overflow-hidden transition-[width,opacity] duration-300 ${
          isSideCollapsed ? 'w-0 min-w-0 opacity-0 pointer-events-none' : `${panelWidthClassMap.normal} opacity-100`
        }`}
      >
        <div className="p-3 border-b border-slate-200 dark:border-slate-800 flex items-center gap-3 bg-slate-50 dark:bg-slate-900">
          <button
            type="button"
            aria-label="收起元数据侧栏"
            className="p-2 rounded-md text-slate-600 hover:text-indigo-600 hover:bg-white/70 dark:hover:bg-slate-800 transition-colors"
            onClick={() => setIsSideCollapsed(true)}
          >
            <Menu size={18} />
          </button>
          <div className="flex flex-1 justify-end">
            <div
              className={`relative flex items-center transition-[width] duration-300 ease-out ${
                isSearchOpen || searchValue ? 'w-40 @md:w-52' : 'w-8 justify-end'
              }`}
            >
              <input
                value={searchValue}
                onChange={(e) => setSearchValue(e.target.value)}
                placeholder="搜索..."
                ref={searchInputRef}
                onFocus={() => setIsSearchOpen(true)}
                onBlur={() => {
                  if (!searchValue) setIsSearchOpen(false)
                }}
                className={`pr-9 pl-2.5 py-1.5 rounded-md border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-xs text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-[width,opacity] duration-300 ease-out ${
                  isSearchOpen || searchValue ? 'opacity-100 pointer-events-auto w-full' : 'opacity-0 pointer-events-none w-0'
                }`}
              />
              <button
                type="button"
                aria-label="打开搜索"
                className="absolute right-1 inset-y-0 flex items-center justify-center p-2 rounded-md text-slate-500 hover:text-indigo-600"
                onClick={() => {
                  setIsSearchOpen(true)
                  requestAnimationFrame(() => searchInputRef.current?.focus())
                }}
              >
                <Search size={18} />
              </button>
            </div>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto custom-scrollbar py-2">
          <Tree
            catalogs={filteredCatalogs}
            expandedNodes={expandedNodes}
            selectedNodeId={selectedNodeId}
            onToggle={toggleNode}
            onSelect={handleSelect}
          />
        </div>
      </aside>
    </div>
  )
}

function Tree({
  catalogs,
  expandedNodes,
  selectedNodeId,
  onToggle,
  onSelect
}: {
  catalogs: CatalogAsset[]
  expandedNodes: Set<string>
  selectedNodeId: string
  onToggle: (id: string) => void
  onSelect: (id: string, type: NodeType) => void
}) {
  return (
    <div className="flex flex-col">
      <TreeNode
        id="ROOT"
        label="One Meta (Enterprise)"
        type="ROOT"
        level={0}
        isActive={selectedNodeId === 'ROOT'}
        isOpen={expandedNodes.has('ROOT')}
        hasChildren
        onClick={() => onSelect('ROOT', 'ROOT')}
        onToggle={() => onToggle('ROOT')}
      />
      {expandedNodes.has('ROOT') &&
        catalogs.map((catalog) => (
          <div key={catalog.id}>
            <TreeNode
              id={catalog.id}
              label={catalog.name}
              type="CATALOG"
              level={1}
              isActive={selectedNodeId === catalog.id}
              isOpen={expandedNodes.has(catalog.id)}
              hasChildren={catalog.schemas.length > 0}
              onClick={() => onSelect(catalog.id, 'CATALOG')}
              onToggle={() => onToggle(catalog.id)}
            />
            {expandedNodes.has(catalog.id) &&
              catalog.schemas.map((schema) => (
                <div key={schema.id}>
                  <TreeNode
                    id={schema.id}
                    label={schema.name}
                    type="SCHEMA"
                    level={2}
                    isActive={selectedNodeId === schema.id}
                    isOpen={expandedNodes.has(schema.id)}
                    hasChildren={schema.tables.length > 0}
                    onClick={() => onSelect(schema.id, 'SCHEMA')}
                    onToggle={() => onToggle(schema.id)}
                  />
                  {expandedNodes.has(schema.id) &&
                    schema.tables.map((table) => (
                      <TreeNode
                        key={table.id}
                        id={table.id}
                        label={table.name}
                        type="TABLE"
                        level={3}
                        isActive={selectedNodeId === table.id}
                        isOpen={false}
                        hasChildren={false}
                        onClick={() => onSelect(table.id, 'TABLE')}
                        onToggle={() => {}}
                      />
                    ))}
                </div>
              ))}
          </div>
        ))}
    </div>
  )
}

function TreeNode({
  label,
  type,
  level,
  isOpen,
  isActive,
  hasChildren,
  onClick,
  onToggle
}: {
  id: string
  label: string
  type: NodeType
  level: number
  isOpen: boolean
  isActive: boolean
  hasChildren: boolean
  onClick: () => void
  onToggle: () => void
}) {
  const paddingLeft = level * INDENT_PX + 12

  const icon = (() => {
    if (type === 'ROOT') return <Server size={14} className="text-indigo-500" />
    if (type === 'CATALOG') return <Database size={14} className="text-blue-600" />
    if (type === 'SCHEMA') return <FolderTree size={14} className="text-amber-500" />
    return <TableIcon size={14} className="text-slate-400" />
  })()

  return (
    <div className="relative">
      {level > 0 && (
        <div
          className="absolute border-l border-slate-200 dark:border-slate-700 h-full"
          style={{ left: `${(level - 1) * INDENT_PX + 16}px` }}
        />
      )}
      <div
        className={`
          group flex items-center gap-2 py-1.5 pr-3 cursor-pointer select-none text-sm transition-colors relative
          ${isActive ? 'bg-blue-50/70 dark:bg-indigo-500/10 text-blue-700 dark:text-indigo-200 font-semibold' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800/60'}
        `}
        style={{ paddingLeft }}
        onClick={(e) => {
          e.stopPropagation()
          onClick()
        }}
      >
        {isActive && <div className="absolute left-0 top-0 bottom-0 w-1 bg-blue-600 rounded-r-sm" />}
        <div
          className={`w-4 h-4 flex items-center justify-center rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors ${!hasChildren ? 'invisible' : ''}`}
          onClick={(e) => {
            e.stopPropagation()
            onToggle()
          }}
        >
          <ChevronRightIcon open={isOpen} />
        </div>
        {icon}
        <span className="truncate flex-1">{label}</span>
      </div>
    </div>
  )
}

function ChevronRightIcon({ open }: { open: boolean }) {
  return (
    <svg
      className={`size-3 text-slate-400 transition-transform duration-200 ${open ? 'rotate-90' : ''}`}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      viewBox="0 0 24 24"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  )
}
