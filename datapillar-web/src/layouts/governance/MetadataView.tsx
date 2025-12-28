import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Database, FolderTree, Menu, MoreVertical, Server, Table as TableIcon, Folder, Activity, Box, Layers } from 'lucide-react'
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
import { CreateCatalogForm, CreateSchemaForm, CreateTableForm, type TableFormHandle } from './metadata/form'
import { type CatalogFormHandle } from './metadata/form/CatalogForm'
import { type SchemaFormHandle } from './metadata/form/SchemaForm'
import { type CatalogAsset, type NodeType, type SchemaAsset, type TableAsset } from './metadata/type/types'
import { Modal, ModalCancelButton, ModalPrimaryButton } from '@/components/ui'
import { useSearchStore } from '@/stores'
import {
  fetchCatalogs,
  fetchSchemas,
  fetchTables,
  createCatalog,
  createSchema,
  createTable,
  updateTable,
  mapProviderToIcon,
  associateObjectTags,
  type CatalogItem,
  type SchemaItem,
  type TableItem
} from '@/services/oneMetaService'

const INDENT_PX = 18
const VALUE_DOMAIN_TAG_PREFIX = 'vd:'

export function MetadataView() {
  const navigate = useNavigate()
  const { catalogName, schemaName, tableName } = useParams<{
    catalogName?: string
    schemaName?: string
    tableName?: string
  }>()

  const [catalogs, setCatalogs] = useState<CatalogItem[]>([])
  const [schemasMap, setSchemasMap] = useState<Map<string, SchemaItem[]>>(new Map())
  const [tablesMap, setTablesMap] = useState<Map<string, TableItem[]>>(new Map())
  const [_isLoading, setIsLoading] = useState(false)
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(() => new Set<string>(['ROOT']))
  const [selectedNodeId, setSelectedNodeId] = useState<string>('ROOT')
  const [selectedNodeType, setSelectedNodeType] = useState<NodeType>('ROOT')
  const [activeTab, setActiveTab] = useState<'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE'>('OVERVIEW')
  const [isSideCollapsed, setIsSideCollapsed] = useState(true)
  const catalogFormRef = useRef<CatalogFormHandle>(null)
  const schemaFormRef = useRef<SchemaFormHandle>(null)
  const tableFormRef = useRef<TableFormHandle>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [refreshKey, setRefreshKey] = useState(0)

  // 使用全局搜索状态
  const searchTerm = useSearchStore((state) => state.searchTerm)

  // 响应 URL 参数变化，更新选中状态和加载数据
  useEffect(() => {
    // 计算选中状态
    let id = 'ROOT'
    let type: NodeType = 'ROOT'
    if (tableName && schemaName && catalogName) {
      id = `${catalogName}.${schemaName}.${tableName}`
      type = 'TABLE'
    } else if (schemaName && catalogName) {
      id = `${catalogName}.${schemaName}`
      type = 'SCHEMA'
    } else if (catalogName) {
      id = catalogName
      type = 'CATALOG'
    }

    setSelectedNodeId(id)
    setSelectedNodeType(type)

    // 展开对应节点
    setExpandedNodes((prev) => {
      const next = new Set(prev)
      next.add('ROOT')
      if (catalogName) next.add(catalogName)
      if (catalogName && schemaName) next.add(`${catalogName}.${schemaName}`)
      return next
    })

    // 加载数据
    async function loadData() {
      try {
        setIsLoading(true)
        const catalogsData = await fetchCatalogs()
        setCatalogs(catalogsData)

        if (catalogName) {
          const schemas = await fetchSchemas(catalogName)
          setSchemasMap((prev) => new Map(prev).set(catalogName, schemas))
        }
        if (catalogName && schemaName) {
          const tables = await fetchTables(catalogName, schemaName)
          setTablesMap((prev) => new Map(prev).set(`${catalogName}.${schemaName}`, tables))
        }
      } catch {
        // 错误已由统一客户端通过 toast 显示
      } finally {
        setIsLoading(false)
      }
    }
    loadData()
  }, [catalogName, schemaName, tableName])

  async function loadCatalogs() {
    try {
      setIsLoading(true)
      const catalogsData = await fetchCatalogs()
      setCatalogs(catalogsData)
    } catch {
      // 错误已由统一客户端通过 toast 显示
    } finally {
      setIsLoading(false)
    }
  }

  async function loadSchemas(catalogId: string) {
    if (schemasMap.has(catalogId)) return

    try {
      const schemas = await fetchSchemas(catalogId)
      setSchemasMap((prev) => new Map(prev).set(catalogId, schemas))
    } catch {
      // 错误已由统一客户端通过 toast 显示
    }
  }

  async function loadTables(catalogId: string, schemaId: string) {
    const fullSchemaId = `${catalogId}.${schemaId}`
    if (tablesMap.has(fullSchemaId)) return

    try {
      const tables = await fetchTables(catalogId, schemaId)
      setTablesMap((prev) => new Map(prev).set(fullSchemaId, tables))
    } catch {
      // 错误已由统一客户端通过 toast 显示
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
      } else if (modalState.nodeType === 'SCHEMA') {
        if (!tableFormRef.current?.validate()) return

        const formData = tableFormRef.current.getData()
        const [catalogName, schemaName] = modalState.nodeId.split('.')

        if (modalState.editingTable) {
          // 编辑模式：调用 updateTable API
          const updates: Array<{ '@type': string; [key: string]: unknown }> = []

          // 更新表注释
          if (formData.comment !== modalState.editingTable.comment) {
            updates.push({ '@type': 'updateComment', newComment: formData.comment || '' })
          }

          // 对比列变化
          const oldColumns = modalState.editingTable.columns
          const newColumns = formData.columns

          // 删除的列
          oldColumns.forEach((oldCol) => {
            if (!newColumns.find((newCol) => newCol.name === oldCol.name)) {
              updates.push({ '@type': 'deleteColumn', fieldName: [oldCol.name], ifExists: true })
            }
          })

          // 新增的列
          newColumns.forEach((newCol) => {
            const oldCol = oldColumns.find((c) => c.name === newCol.name)
            if (!oldCol) {
              updates.push({
                '@type': 'addColumn',
                fieldName: [newCol.name],
                type: newCol.type,
                comment: newCol.comment || '',
                position: 'default',
                nullable: true
              })
            }
          })

          // 修改的列（类型、注释）
          newColumns.forEach((newCol) => {
            const oldCol = oldColumns.find((c) => c.name === newCol.name)
            if (oldCol) {
              if (oldCol.type.toLowerCase() !== newCol.type.toLowerCase()) {
                updates.push({
                  '@type': 'updateColumnType',
                  fieldName: [newCol.name],
                  newType: newCol.type
                })
              }
              if (oldCol.comment !== newCol.comment) {
                updates.push({
                  '@type': 'updateColumnComment',
                  fieldName: [newCol.name],
                  newComment: newCol.comment || ''
                })
              }
            }
          })

          if (updates.length > 0) {
            await updateTable(catalogName, schemaName, modalState.editingTable.name, { updates })
          }

          // 处理值域 tag 变化
          const tagPromises: Promise<void>[] = []
          const tableName = modalState.editingTable.name

          // 遍历所有列，处理值域变化
          formData.columns.forEach((newCol) => {
            const oldCol = modalState.editingTable!.columns.find((c) => c.name === newCol.name)
            const oldDomainCode = oldCol?.valueDomainCode
            const newDomainCode = newCol.valueDomainCode

            const columnFullName = `${catalogName}.${schemaName}.${tableName}.${newCol.name}`

            if (newDomainCode && newDomainCode !== oldDomainCode) {
              // 新增或修改值域
              const tagName = `${VALUE_DOMAIN_TAG_PREFIX}${newDomainCode}`
              const tagsToRemove = oldDomainCode ? [`${VALUE_DOMAIN_TAG_PREFIX}${oldDomainCode}`] : []
              tagPromises.push(associateObjectTags('COLUMN', columnFullName, [tagName], tagsToRemove).catch(() => {}))
            } else if (!newDomainCode && oldDomainCode) {
              // 移除值域
              const tagToRemove = `${VALUE_DOMAIN_TAG_PREFIX}${oldDomainCode}`
              tagPromises.push(associateObjectTags('COLUMN', columnFullName, [], [tagToRemove]).catch(() => {}))
            }
          })

          if (tagPromises.length > 0) {
            await Promise.all(tagPromises)
          }

          closeModal()
          setRefreshKey((k) => k + 1)
          toast.success(`表 ${modalState.editingTable.name} 更新成功`)
        } else {
          // 创建模式
          await createTable(catalogName, schemaName, {
            name: formData.name,
            comment: formData.comment,
            columns: formData.columns.map(({ valueDomainCode: _, ...col }) => col),
            properties: formData.properties
          })

          // 为关联了值域的列打 tag（用于血缘追踪）
          const columnsWithValueDomain = formData.columns.filter((col) => col.valueDomainCode)
          if (columnsWithValueDomain.length > 0) {
            const tagPromises = columnsWithValueDomain.map((col) => {
              const columnFullName = `${catalogName}.${schemaName}.${formData.name}.${col.name}`
              const tagName = `${VALUE_DOMAIN_TAG_PREFIX}${col.valueDomainCode}`
              return associateObjectTags('COLUMN', columnFullName, [tagName], []).catch(() => {})
            })
            await Promise.all(tagPromises)
          }

          // 刷新表列表
          setTablesMap((prev) => {
            const next = new Map(prev)
            next.delete(modalState.nodeId)
            return next
          })
          await loadTables(catalogName, schemaName)
          closeModal()
          toast.success(`表 ${formData.name} 创建成功`)
        }
      }
    } catch {
      // 错误已由统一客户端通过 toast 显示
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
    editingTable?: {
      name: string
      comment?: string
      columns: Array<{
        name: string
        type: string
        comment?: string
        nullable?: boolean
        valueDomainCode?: string
      }>
    }
  }>({
    isOpen: false,
    nodeId: '',
    nodeType: 'ROOT',
    nodeName: '',
    provider: undefined,
    editingTable: undefined
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

  // 搜索时自动展开匹配项的父节点
  useEffect(() => {
    const query = searchTerm.trim().toLowerCase()
    if (!query) return

    const nodesToExpand = new Set<string>(['ROOT'])

    catalogsWithSchemas.forEach((catalog) => {
      const catalogMatches = catalog.name.toLowerCase().includes(query)

      if (catalogMatches) {
        nodesToExpand.add(catalog.id)
      }

      catalog.schemas.forEach((schema) => {
        const schemaMatches = schema.name.toLowerCase().includes(query)

        if (schemaMatches) {
          nodesToExpand.add(catalog.id)
          nodesToExpand.add(schema.id)
        }

        schema.tables.forEach((table) => {
          const tableMatches = table.name.toLowerCase().includes(query)

          if (tableMatches) {
            nodesToExpand.add(catalog.id)
            nodesToExpand.add(schema.id)
          }
        })
      })
    })

    if (nodesToExpand.size > 1) {
      setExpandedNodes((prev) => new Set([...prev, ...nodesToExpand]))
    }
  }, [searchTerm, catalogsWithSchemas])

  const filteredCatalogs = useMemo(() => {
    const query = searchTerm.trim().toLowerCase()
    if (!query) return catalogsWithSchemas

    return catalogsWithSchemas
      .map((catalog) => {
        const catalogMatches = catalog.name.toLowerCase().includes(query)

        // 过滤匹配的 schema 和 table
        const filteredSchemas = catalog.schemas
          .map((schema) => {
            const schemaMatches = schema.name.toLowerCase().includes(query)
            const filteredTables = schema.tables.filter((table) =>
              table.name.toLowerCase().includes(query)
            )

            // schema 匹配或有匹配的 table，则保留
            if (schemaMatches || filteredTables.length > 0) {
              return {
                ...schema,
                // schema 本身匹配时显示所有 table，否则只显示匹配的 table
                tables: schemaMatches ? schema.tables : filteredTables
              }
            }
            return null
          })
          .filter((schema): schema is NonNullable<typeof schema> => schema !== null)

        // catalog 匹配或有匹配的 schema，则保留
        if (catalogMatches || filteredSchemas.length > 0) {
          return {
            ...catalog,
            // catalog 本身匹配时显示所有 schema，否则只显示匹配的 schema
            schemas: catalogMatches ? catalog.schemas : filteredSchemas
          }
        }
        return null
      })
      .filter((catalog): catalog is NonNullable<typeof catalog> => catalog !== null)
  }, [catalogsWithSchemas, searchTerm])

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
    // 更新 URL，组件会通过 useEffect 响应参数变化
    if (type === 'ROOT') {
      navigate('/governance/metadata')
    } else if (type === 'CATALOG') {
      navigate(`/governance/metadata/catalogs/${encodeURIComponent(id)}`)
    } else if (type === 'SCHEMA') {
      const parts = id.split('.')
      if (parts.length === 2) {
        const [catalog, schema] = parts
        navigate(`/governance/metadata/catalogs/${encodeURIComponent(catalog)}/schemas/${encodeURIComponent(schema)}`)
      }
    } else if (type === 'TABLE') {
      const parts = id.split('.')
      if (parts.length === 3) {
        const [catalog, schema, table] = parts
        navigate(`/governance/metadata/catalogs/${encodeURIComponent(catalog)}/schemas/${encodeURIComponent(schema)}/tables/${encodeURIComponent(table)}`)
      }
    }
    setActiveTab('OVERVIEW')
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
      nodeName: '',
      editingTable: undefined
    })
    setFooterLeft(null)
    setContentOverlay(null)
  }

  return (
    <div className="relative flex w-full h-full bg-white dark:bg-slate-900 @container">
      <section className="flex-1 min-w-0 flex flex-col overflow-hidden">
        {selectedNodeType === 'TABLE' && selectedTable ? (
          <TableOverview
            key={`${selectedTable.table.id}-${refreshKey}`}
            activeTab={activeTab}
            onTabChange={setActiveTab}
            table={selectedTable.table}
            provider={selectedTable.catalog.provider}
            breadcrumb={[
              selectedTable.catalog.name,
              selectedTable.schema.name,
              selectedTable.table.name
            ]}
            onEdit={(tableData) => {
              setModalState({
                isOpen: true,
                nodeId: selectedTable.schema.id,
                nodeType: 'SCHEMA',
                nodeName: selectedTable.schema.name,
                provider: selectedTable.catalog.provider,
                editingTable: tableData
              })
            }}
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
        <div className="p-3 border-b border-slate-200 dark:border-slate-800 flex items-center bg-slate-50 dark:bg-slate-900">
          <button
            type="button"
            aria-label="收起元数据侧栏"
            className="p-2 rounded-md text-slate-600 hover:text-indigo-600 hover:bg-white/70 dark:hover:bg-slate-800 transition-colors"
            onClick={() => setIsSideCollapsed(true)}
          >
            <Menu size={18} />
          </button>
          <div className="flex-1 flex items-center justify-center gap-1.5 pr-10">
            <Layers size={16} className="text-indigo-500" />
            <span className="text-sm font-semibold text-slate-800 dark:text-slate-200">元数据中心</span>
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
        size={modalState.nodeType === 'SCHEMA' ? 'lg' : 'md'}
        title={
          modalState.nodeType === 'ROOT'
            ? '创建 Catalog'
            : modalState.nodeType === 'CATALOG'
              ? '创建 Schema'
              : modalState.nodeType === 'SCHEMA'
                ? modalState.editingTable ? '编辑 Table' : '创建 Table'
                : '管理表'
        }
        subtitle={
          modalState.nodeType === 'SCHEMA' ? (
            <span className="text-xs text-slate-400 dark:text-slate-500">
              {modalState.editingTable ? '编辑' : '将创建于'} Schema「<span className="font-medium text-blue-600 dark:text-blue-400">{modalState.nodeName}</span>」
            </span>
          ) : undefined
        }
        footerLeft={footerLeft}
        footerRight={
          modalState.nodeType !== 'TABLE' ? (
            <>
              <ModalCancelButton onClick={closeModal} disabled={isSubmitting}>
                取消
              </ModalCancelButton>
              <ModalPrimaryButton onClick={handleSubmit} disabled={isSubmitting} loading={isSubmitting}>
                {modalState.editingTable ? '保存修改' : '创建物理资产'}
              </ModalPrimaryButton>
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
            key={modalState.editingTable?.name || 'new'}
            ref={tableFormRef}
            parentName={modalState.nodeName}
            provider={modalState.provider}
            onDDLButtonRender={setFooterLeft}
            onOverlayRender={setContentOverlay}
            initialData={modalState.editingTable}
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
