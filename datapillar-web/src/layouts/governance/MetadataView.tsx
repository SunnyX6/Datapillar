import { useEffect, useMemo, useRef, useState } from 'react'
import { Database, FolderTree, Menu, MoreVertical, Search, Server, Table as TableIcon, Folder, Activity, Box } from 'lucide-react'
import {
  SiApachehive,
  SiApachespark,
  SiApachekafka,
  SiApacheflink,
  SiApachehadoop,
  SiClickhouse,
  SiSnowflake,
  SiDatabricks,
  SiElasticsearch,
  SiMongodb,
  SiPostgresql,
  SiMysql,
  SiRedis
} from 'react-icons/si'
import { toast } from 'sonner'
import { CatalogOverview } from './metadata/overview/CatalogOverview'
import { Overview } from './metadata/overview/Overview'
import { SchemaOverview } from './metadata/overview/SchemaOverview'
import { TableOverview } from './metadata/overview/TableOverview'
import { CreateCatalogForm, CreateSchemaForm, CreateTableForm } from './metadata/form'
import { type CatalogFormHandle } from './metadata/form/CatalogForm'
import { type SchemaFormHandle } from './metadata/form/SchemaForm'
import { type CatalogAsset, type NodeType, type SchemaAsset, type TableAsset } from './metadata/type/types'
import { Modal } from '@/components/Modal'
import {
  fetchCatalogs,
  fetchSchemas,
  fetchTables,
  createCatalog,
  createSchema,
  getSchema,
  getTable,
  mapProviderToIcon,
  type CatalogItem,
  type SchemaItem,
  type TableItem
} from '@/services/oneMetaService'

const INDENT_PX = 18

export function MetadataView() {
  const [catalogs, setCatalogs] = useState<CatalogItem[]>([])
  const [schemasMap, setSchemasMap] = useState<Map<string, SchemaItem[]>>(new Map())
  const [tablesMap, setTablesMap] = useState<Map<string, TableItem[]>>(new Map())
  const [isLoading, setIsLoading] = useState(false)
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(() => new Set<string>(['ROOT']))
  const [selectedNodeId, setSelectedNodeId] = useState<string>('ROOT')
  const [selectedNodeType, setSelectedNodeType] = useState<NodeType>('ROOT')
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE'>('OVERVIEW')
  const [searchValue, setSearchValue] = useState('')
  const [isSearchOpen, setIsSearchOpen] = useState(false)
  const searchInputRef = useRef<HTMLInputElement | null>(null)
  const [isSideCollapsed, setIsSideCollapsed] = useState(true)
  const catalogFormRef = useRef<CatalogFormHandle>(null)
  const schemaFormRef = useRef<SchemaFormHandle>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [loadedMetadata, setLoadedMetadata] = useState<Set<string>>(() => new Set<string>())

  useEffect(() => {
    loadCatalogs()
  }, [])

  async function loadCatalogs() {
    try {
      setIsLoading(true)
      const catalogsData = await fetchCatalogs()
      setCatalogs(catalogsData)
    } catch (error) {
      console.error('加载 Catalog 列表失败:', error)
      toast.error(error instanceof Error ? error.message : '加载 Catalog 列表失败')
    } finally {
      setIsLoading(false)
    }
  }

  async function loadSchemas(catalogId: string) {
    if (schemasMap.has(catalogId)) return

    try {
      const schemas = await fetchSchemas(catalogId)
      setSchemasMap((prev) => new Map(prev).set(catalogId, schemas))
    } catch (error) {
      console.error(`加载 Schema 列表失败 (catalog=${catalogId}):`, error)
      toast.error(error instanceof Error ? error.message : `加载 Schema 列表失败`)
    }
  }

  async function loadTables(catalogId: string, schemaId: string) {
    const fullSchemaId = `${catalogId}.${schemaId}`
    if (tablesMap.has(fullSchemaId)) return

    try {
      const tables = await fetchTables(catalogId, schemaId)
      setTablesMap((prev) => new Map(prev).set(fullSchemaId, tables))
    } catch (error) {
      console.error(`加载 Table 列表失败 (catalog=${catalogId}, schema=${schemaId}):`, error)
      toast.error(error instanceof Error ? error.message : `加载 Table 列表失败`)
    }
  }

  async function handleSubmit() {
    if (isSubmitting) return

    try {
      setIsSubmitting(true)

      if (modalState.nodeType === 'ROOT') {
        if (!catalogFormRef.current?.validate()) return

        const formData = catalogFormRef.current.getData()
        await createCatalog({
          name: formData.name,
          type: formData.type,
          provider: formData.provider,
          comment: formData.comment,
          properties: formData.properties
        })

        await loadCatalogs()

        const newCatalogId = formData.name
        setExpandedNodes((prev) => new Set(prev).add(newCatalogId))
        await loadSchemas(newCatalogId)

        closeModal()
      } else if (modalState.nodeType === 'CATALOG') {
        if (!schemaFormRef.current?.validate()) return

        const formData = schemaFormRef.current.getData()
        await createSchema(modalState.nodeId, {
          name: formData.name,
          comment: formData.comment
        })

        setSchemasMap((prev) => {
          const next = new Map(prev)
          next.delete(modalState.nodeId)
          return next
        })
        await loadSchemas(modalState.nodeId)
        closeModal()
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : '创建失败'
      toast.error(errorMessage)
    } finally {
      setIsSubmitting(false)
    }
  }

  const [modalState, setModalState] = useState<{
    isOpen: boolean
    nodeId: string
    nodeType: NodeType
    nodeName: string
    provider?: string
  }>({
    isOpen: false,
    nodeId: '',
    nodeType: 'ROOT',
    nodeName: '',
    provider: undefined
  })
  const [footerLeft, setFooterLeft] = useState<React.ReactNode>(null)
  const [contentOverlay, setContentOverlay] = useState<React.ReactNode>(null)

  const catalogsWithSchemas = useMemo<CatalogAsset[]>(() => {
    return catalogs.map((catalog) => {
      const schemas = schemasMap.get(catalog.id) || []
      return {
        id: catalog.id,
        name: catalog.name,
        icon: mapProviderToIcon(catalog.provider), // 前端动态计算图标
        provider: catalog.provider,
        schemas: schemas.map((schema) => {
          const tables = tablesMap.get(schema.id) || []
          return {
            id: schema.id,
            name: schema.name,
            catalogId: schema.catalogId,
            tables: tables.map((table) => ({
              id: table.id,
              name: table.name,
              description: table.comment || '',
              certification: undefined,
              qualityScore: 0,
              rowCount: 0,
              size: '',
              owner: '',
              updatedAt: '',
              domains: [],
              columns: table.columns || []
            }))
          }
        })
      }
    })
  }, [catalogs, schemasMap, tablesMap])

  const filteredCatalogs = useMemo(() => {
    const query = searchValue.trim().toLowerCase()
    if (!query) return catalogsWithSchemas

    return catalogsWithSchemas.filter((catalog) => {
      const matchCatalog = catalog.name.toLowerCase().includes(query)
      const hasMatchingSchema = catalog.schemas.some((schema) => {
        const matchSchema = schema.name.toLowerCase().includes(query)
        const hasMatchingTable = schema.tables.some((table) => table.name.toLowerCase().includes(query))
        return matchSchema || hasMatchingTable
      })
      return matchCatalog || hasMatchingSchema
    })
  }, [catalogsWithSchemas, searchValue])

  const catalogLookup = useMemo(() => {
    const map = new Map<string, CatalogAsset>()
    catalogsWithSchemas.forEach((catalog) => map.set(catalog.id, catalog))
    return map
  }, [catalogsWithSchemas])

  const schemaLookup = useMemo(() => {
    const map = new Map<string, { schema: SchemaAsset; catalog: CatalogAsset }>()
    catalogsWithSchemas.forEach((catalog) => {
      catalog.schemas.forEach((schema) => map.set(schema.id, { schema, catalog }))
    })
    return map
  }, [catalogsWithSchemas])

  const tableLookup = useMemo(() => {
    const map = new Map<string, { table: TableAsset; schema: SchemaAsset; catalog: CatalogAsset }>()
    catalogsWithSchemas.forEach((catalog) => {
      catalog.schemas.forEach((schema) => {
        schema.tables.forEach((table) => map.set(table.id, { table, schema, catalog }))
      })
    })
    return map
  }, [catalogsWithSchemas])

  const selectedTable = selectedNodeType === 'TABLE' ? tableLookup.get(selectedNodeId) : null
  const selectedSchema = selectedNodeType === 'SCHEMA' ? schemaLookup.get(selectedNodeId) : null
  const selectedCatalog = selectedNodeType === 'CATALOG' ? catalogLookup.get(selectedNodeId) : null

  const handleSelect = async (id: string, type: NodeType) => {
    setSelectedNodeId(id)
    setSelectedNodeType(type)
    setActiveTab('OVERVIEW')

    // 触发元数据加载（导入到 gravitino 数据库）
    if (loadedMetadata.has(id)) {
      return
    }

    try {
      if (type === 'SCHEMA') {
        const parts = id.split('.')
        if (parts.length === 2) {
          const [catalogId, schemaName] = parts
          await getSchema(catalogId, schemaName)
          setLoadedMetadata((prev) => new Set(prev).add(id))
        }
      } else if (type === 'TABLE') {
        const parts = id.split('.')
        if (parts.length === 3) {
          const [catalogId, schemaName, tableName] = parts
          await getTable(catalogId, schemaName, tableName)
          setLoadedMetadata((prev) => new Set(prev).add(id))
        }
      }
    } catch (error) {
      console.error(`加载元数据失败 (${type}=${id}):`, error)
    }
  }

  const toggleNode = async (id: string, type: NodeType) => {
    const isExpanding = !expandedNodes.has(id)

    setExpandedNodes((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })

    if (isExpanding) {
      if (type === 'CATALOG') {
        await loadSchemas(id)
      } else if (type === 'SCHEMA') {
        const parts = id.split('.')
        if (parts.length === 2) {
          const [catalogId, schemaName] = parts
          await loadTables(catalogId, schemaName)
        }
      }
    }
  }

  const handleCreateChild = (parentId: string, parentType: NodeType, parentName: string) => {
    const schemaInfo = schemaLookup.get(parentId)
    const catalogInfo = catalogLookup.get(parentId)
    const provider =
      parentType === 'SCHEMA'
        ? schemaInfo?.catalog.provider
        : parentType === 'CATALOG'
          ? catalogInfo?.provider
          : undefined

    setModalState({
      isOpen: true,
      nodeId: parentId,
      nodeType: parentType,
      nodeName: parentName,
      provider
    })
  }

  const closeModal = () => {
    setModalState({
      isOpen: false,
      nodeId: '',
      nodeType: 'ROOT',
      nodeName: ''
    })
    setFooterLeft(null)
    setContentOverlay(null)
  }

  return (
    <div className="relative flex w-full h-full bg-white dark:bg-slate-900 @container">
      <section className="flex-1 min-w-0 flex flex-col overflow-hidden">
        {selectedNodeType === 'TABLE' && selectedTable ? (
          <TableOverview
            activeTab={activeTab}
            onTabChange={setActiveTab}
            table={selectedTable.table}
            provider={selectedTable.catalog.provider}
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
          isSideCollapsed ? 'w-0 min-w-0 opacity-0 pointer-events-none' : 'w-panel-responsive opacity-100'
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
            onCreateChild={handleCreateChild}
          />
        </div>
      </aside>

      {/* 统一的模态弹窗 */}
      <Modal
        isOpen={modalState.isOpen}
        onClose={closeModal}
        title={
          modalState.nodeType === 'ROOT'
            ? '创建 Catalog'
            : modalState.nodeType === 'CATALOG'
              ? '创建 Schema'
              : modalState.nodeType === 'SCHEMA'
                ? '创建 Table'
                : '管理表'
        }
        footerLeft={footerLeft}
        footerRight={
          modalState.nodeType !== 'TABLE' ? (
            <>
              <button
                type="button"
                onClick={closeModal}
                disabled={isSubmitting}
                className="px-3 py-1.5 text-sm font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-md transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={isSubmitting}
                className="px-3 py-1.5 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? '创建中...' : '创建'}
              </button>
            </>
          ) : undefined
        }
        contentOverlay={contentOverlay}
      >
        {modalState.nodeType === 'ROOT' && (
          <CreateCatalogForm ref={catalogFormRef} parentName={modalState.nodeName} onFooterLeftRender={setFooterLeft} />
        )}
        {modalState.nodeType === 'CATALOG' && <CreateSchemaForm ref={schemaFormRef} parentName={modalState.nodeName} />}
        {modalState.nodeType === 'SCHEMA' && (
          <CreateTableForm
            parentName={modalState.nodeName}
            provider={modalState.provider}
            onDDLButtonRender={setFooterLeft}
            onOverlayRender={setContentOverlay}
          />
        )}
      </Modal>
    </div>
  )
}

function Tree({
  catalogs,
  expandedNodes,
  selectedNodeId,
  onToggle,
  onSelect,
  onCreateChild
}: {
  catalogs: CatalogAsset[]
  expandedNodes: Set<string>
  selectedNodeId: string
  onToggle: (id: string, type: NodeType) => void
  onSelect: (id: string, type: NodeType) => void
  onCreateChild: (parentId: string, parentType: NodeType, parentName: string) => void
}) {
  return (
    <div className="flex flex-col">
      <TreeNode
        id="ROOT"
        label="One Meta"
        type="ROOT"
        level={0}
        isActive={selectedNodeId === 'ROOT'}
        isOpen={expandedNodes.has('ROOT')}
        hasChildren
        onClick={() => onSelect('ROOT', 'ROOT')}
        onToggle={() => onToggle('ROOT', 'ROOT')}
        onMoreClick={() => onCreateChild('ROOT', 'ROOT', 'One Meta')}
      />
      {expandedNodes.has('ROOT') &&
        catalogs.map((catalog) => (
          <div key={catalog.id}>
            <TreeNode
              id={catalog.id}
              label={catalog.name}
              type="CATALOG"
              catalogIcon={catalog.icon}
              level={1}
              isActive={selectedNodeId === catalog.id}
              isOpen={expandedNodes.has(catalog.id)}
              hasChildren
              onClick={() => onSelect(catalog.id, 'CATALOG')}
              onToggle={() => onToggle(catalog.id, 'CATALOG')}
              onMoreClick={() => onCreateChild(catalog.id, 'CATALOG', catalog.name)}
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
                    hasChildren
                    onClick={() => onSelect(schema.id, 'SCHEMA')}
                    onToggle={() => onToggle(schema.id, 'SCHEMA')}
                    onMoreClick={() => onCreateChild(schema.id, 'SCHEMA', schema.name)}
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
                        onMoreClick={() => onCreateChild(table.id, 'TABLE', table.name)}
                        showMore={false}
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
  id: _id,
  label,
  type,
  catalogIcon,
  level,
  isOpen,
  isActive,
  hasChildren,
  onClick,
  onToggle,
  onMoreClick,
  showMore = true
}: {
  id: string
  label: string
  type: NodeType
  catalogIcon?: string
  level: number
  isOpen: boolean
  isActive: boolean
  hasChildren: boolean
  onClick: () => void
  onToggle: () => void
  onMoreClick: () => void
  showMore?: boolean
}) {
  const paddingLeft = level * INDENT_PX + 12

  const getCatalogIcon = (iconName?: string) => {
    const iconProps = { size: 14, className: 'text-blue-600 dark:text-blue-400' }
    switch (iconName) {
      case 'hive':
        return <SiApachehive {...iconProps} />
      case 'spark':
        return <SiApachespark {...iconProps} />
      case 'kafka':
        return <SiApachekafka {...iconProps} />
      case 'flink':
        return <SiApacheflink {...iconProps} />
      case 'hadoop':
        return <SiApachehadoop {...iconProps} />
      case 'clickhouse':
        return <SiClickhouse {...iconProps} />
      case 'snowflake':
        return <SiSnowflake {...iconProps} />
      case 'databricks':
        return <SiDatabricks {...iconProps} />
      case 'elasticsearch':
        return <SiElasticsearch {...iconProps} />
      case 'mongodb':
        return <SiMongodb {...iconProps} />
      case 'postgresql':
        return <SiPostgresql {...iconProps} />
      case 'mysql':
        return <SiMysql {...iconProps} />
      case 'redis':
        return <SiRedis {...iconProps} />
      case 'iceberg':
        return <Database size={14} className="text-cyan-600 dark:text-cyan-400" />
      case 'folder':
        return <Folder size={14} className="text-amber-600 dark:text-amber-400" />
      case 'metric':
        return <Activity size={14} className="text-green-600 dark:text-green-400" />
      case 'model':
        return <Box size={14} className="text-purple-600 dark:text-purple-400" />
      default:
        return <Database size={14} className="text-blue-600" />
    }
  }

  const icon = (() => {
    if (type === 'ROOT') return <Server size={14} className="text-indigo-500" />
    if (type === 'CATALOG') return getCatalogIcon(catalogIcon)
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

        {showMore && (
          <button
            type="button"
            className="p-1 rounded hover:bg-slate-200/60 dark:hover:bg-slate-700/60 transition-colors opacity-0 group-hover:opacity-100"
            onClick={(e) => {
              e.stopPropagation()
              onMoreClick()
            }}
            aria-label="更多操作"
          >
            <MoreVertical size={14} />
          </button>
        )}
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
