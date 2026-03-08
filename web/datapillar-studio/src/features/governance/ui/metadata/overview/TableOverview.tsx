import { useEffect, useState, useMemo, useRef, useLayoutEffect } from 'react'
import { createPortal } from 'react-dom'
import {
  Activity,
  Book,
  CheckCircle2,
  ChevronRight,
  Clock,
  Fingerprint,
  Key,
  Medal,
  Table as TableIcon,
  User,
  Pin,
  List,
  Hash,
  Type,
  Share2,
  Play,
  Pencil,
  Tag,
  X
} from 'lucide-react'
import { toast } from 'sonner'
import { contentMaxWidthClassMap, iconContainerSizeClassMap, menuWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { formatTime } from '@/utils'
import { type TableAsset } from '../type/types'
import { getTable, associateObjectTags, getObjectTags, createTag } from '@/services/oneMetaService'
import { fetchValueDomains, type ValueDomainDTO } from '@/services/oneMetaSemanticService'
import type { GravitinoIndexDTO } from '@/services/types/onemeta/metadata'
import { Card, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'

const VALUE_DOMAIN_TAG_PREFIX = 'vd:'

/** Data type equivalence group（Types within the same group are considered compatible） */
const TYPE_EQUIVALENTS: string[][] = [
  ['STRING', 'VARCHAR', 'TEXT', 'CHAR', 'FIXEDCHAR'],
  ['INT', 'INTEGER'],
  ['BIGINT', 'LONG'],
  ['FLOAT', 'REAL'],
  ['DOUBLE', 'DOUBLE PRECISION'],
  ['BOOLEAN', 'BOOL']
]

/** Check whether column data type and range data type are compatible（Ignore case，consider equivalent types） */
function isDataTypeCompatible(columnDataType: string, domainDataType?: string): boolean {
  if (!domainDataType) return false // Value field has no specified data type，No association allowed
  // Extract base type（Remove parameters in parentheses）
  const normalizeType = (type: string) => type.toUpperCase().replace(/\(.*\)$/, '')
  const colType = normalizeType(columnDataType)
  const domType = normalizeType(domainDataType)

  // exactly the same
  if (colType === domType) return true

  // Check if they are in the same equivalence group
  for (const group of TYPE_EQUIVALENTS) {
    if (group.includes(colType) && group.includes(domType)) return true
  }

  return false
}

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

// Format byte size
const formatBytes = (bytes: number): string => {
  if (isNaN(bytes) || bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

/** Edit mode type */
export type TableEditMode = 'basic' | 'columns'

/** Table basic information editing data */
export interface TableBasicEditData {
  name: string
  comment?: string
  properties?: Record<string, string>
}

/** Table column editing data */
export interface TableColumnsEditData {
  name: string
  comment?: string
  columns: Array<{ name: string; type: string; comment?: string; valueDomainCode?: string }>
}

type TableOverviewProps = {
  table: TableAsset
  provider?: string
  breadcrumb: string[]
  activeTab: 'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE'
  onTabChange: (tab: 'OVERVIEW' | 'COLUMNS' | 'QUALITY' | 'LINEAGE') => void
  onEdit?: (mode: TableEditMode, data: TableBasicEditData | TableColumnsEditData) => void
}

/**
 * Determine whether it supports indexing JDBC Type catalog
 */
function isJdbcProvider(provider?: string): boolean {
  if (!provider) return false
  return provider.startsWith('jdbc-')
}

export function TableOverview({ table, provider, breadcrumb, activeTab, onTabChange, onEdit }: TableOverviewProps) {
  const [owner, setOwner] = useState<string>(table.owner)
  const [updatedAt, setUpdatedAt] = useState<string>(table.updatedAt)
  const [description, setDescription] = useState<string>(table.description)
  const [columns, setColumns] = useState(table.columns)
  const [indexes, setIndexes] = useState<GravitinoIndexDTO[]>([])
  const [properties, setProperties] = useState<Record<string, string>>(table.properties || {})
  // Dynamically displayed attributes，According to different catalog returned properties Dynamically generated
  const [tableSpecs, setTableSpecs] = useState<{ label: string; value: string }[]>([])
  // Value range related status
  const [valueDomains, setValueDomains] = useState<ValueDomainDTO[]>([])
  const [valueDomainsLoaded, setValueDomainsLoaded] = useState(false)
  const [columnValueDomainMap, setColumnValueDomainMap] = useState<Map<string, string>>(new Map())
  const [openDropdown, setOpenDropdown] = useState<string | null>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null)
  const dropdownButtonRefs = useRef<Map<string, HTMLElement>>(new Map())
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Value field suspension details related status
  const [hoveredDomain, setHoveredDomain] = useState<string | null>(null)
  const [domainCardPos, setDomainCardPos] = useState<{ top: number; left: number } | null>(null)
  const hoverItemRef = useRef<HTMLDivElement>(null)
  const hoverTimeout = useRef<NodeJS.Timeout | null>(null)
  const domainCardRef = useRef<HTMLDivElement>(null)
  const isCardHovered = useRef(false)

  // table tag Related status
  const [tableTags, setTableTags] = useState<string[]>([])
  const [tagDropdownOpen, setTagDropdownOpen] = useState(false)

  const closeDropdown = () => {
    setOpenDropdown(null)
    setDropdownPos(null)
    setHoveredDomain(null)
  }

  /** Open the value range drop-down（Load the value range list when opening for the first time） */
  const handleOpenValueDomainDropdown = async (columnName: string) => {
    if (openDropdown === columnName) {
      closeDropdown()
      return
    }
    setOpenDropdown(columnName)
    // Load the value range list when opening for the first time
    if (!valueDomainsLoaded) {
      try {
        const result = await fetchValueDomains(0, 100)
        setValueDomains(result.items)
        setValueDomainsLoaded(true)
      } catch (error) {
        console.error('Failed to load range list:', error)
      }
    }
  }

  // Value range suspension event handling
  const handleDomainItemMouseEnter = (domainCode: string) => {
    if (hoverTimeout.current) clearTimeout(hoverTimeout.current)
    setHoveredDomain(domainCode)
  }

  const handleDomainItemMouseLeave = () => {
    hoverTimeout.current = setTimeout(() => {
      if (!isCardHovered.current) setHoveredDomain(null)
    }, 100)
  }

  const handleDomainCardMouseEnter = () => {
    isCardHovered.current = true
    if (hoverTimeout.current) clearTimeout(hoverTimeout.current)
  }

  const handleDomainCardMouseLeave = () => {
    isCardHovered.current = false
    setHoveredDomain(null)
  }

  // Calculate range details card position
  useLayoutEffect(() => {
    if (!hoveredDomain || !hoverItemRef.current) return
    const rect = hoverItemRef.current.getBoundingClientRect()
    setDomainCardPos({
      top: rect.top,
      left: rect.right + 8
    })
    return () => setDomainCardPos(null)
  }, [hoveredDomain])

  const activeHoveredDomain = valueDomains.find((d) => d.domainCode === hoveredDomain)

  // Click outside to close the dropdown
  useEffect(() => {
    if (!openDropdown) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      const buttonEl = dropdownButtonRefs.current.get(openDropdown)
      if (buttonEl?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      closeDropdown()
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [openDropdown])

  // Calculate drop down position
  useLayoutEffect(() => {
    if (!openDropdown) return
    const updatePosition = () => {
      const btn = dropdownButtonRefs.current.get(openDropdown)
      if (!btn) return
      const rect = btn.getBoundingClientRect()
      const left = Math.max(12, rect.left)
      const top = rect.bottom + 4
      setDropdownPos({ top, left })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [openDropdown])

  // Construct a mapping of column names to index types（only for JDBC type catalog valid）
  const columnIndexMap = useMemo(() => {
    const map = new Map<string, Set<'PRIMARY_KEY' | 'UNIQUE_KEY'>>()
    if (!isJdbcProvider(provider)) return map

    indexes.forEach((index) => {
      index.fieldNames.forEach((fieldNamePath) => {
        // fieldNames is a two-dimensional array，Take the first element as the column name
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
    // fromtable.idparsecatalogName, schemaName, tableName
    const parts = table.id.split('.')
    if (parts.length === 3) {
      const [catalogName, schemaName, tableName] = parts

      // call getTable Get complete data
      getTable(catalogName, schemaName, tableName)
        .then((detail) => {
          // updatedescription
          if (detail.comment) {
            setDescription(detail.comment)
          }

          // updatecolumns
          if (detail.columns) {
            setColumns(detail.columns)
          }

          // updateindexes（only for JDBC type valid）
          if (detail.indexes) {
            setIndexes(detail.indexes)
          }

          // Owner must come from Gravitino owner relation, never from audit.creator.
          setOwner(detail.owner || '')

          // Update audit timestamp display.
          if (detail.audit) {
            // Prioritize update time，If not, use creation time
            const timeValue = detail.audit.lastModifiedTime || detail.audit.createTime
            if (timeValue) {
              setUpdatedAt(formatTime(timeValue))
            }
          }

          // According to properties Dynamically construct display fields
          if (detail.properties) {
            setProperties(detail.properties)
            const props = detail.properties
            const specs: { label: string; value: string }[] = []

            // Hive unique: table-type
            if (props['table-type']) {
              specs.push({ label: 'Type', value: props['table-type'] })
            }

            // Hive unique: format (from input-format inference)
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

            // MySQL unique: engine
            if (props['engine']) {
              specs.push({ label: 'Engine', value: props['engine'] })
            }

            // Universal: numRows
            if (props.numRows) {
              const rows = parseInt(props.numRows, 10)
              specs.push({ label: 'Rows', value: isNaN(rows) ? '-' : rows.toLocaleString() })
            }

            // Universal: rawDataSize (Data size)
            if (props.rawDataSize) {
              specs.push({ label: 'Data Size', value: formatBytes(parseInt(props.rawDataSize, 10)) })
            }

            // MySQL unique: indexSize
            if (props.indexSize && props.indexSize !== '0') {
              specs.push({ label: 'Index Size', value: formatBytes(parseInt(props.indexSize, 10)) })
            }

            // Universal: totalSize
            if (props.totalSize) {
              specs.push({ label: 'Total Size', value: formatBytes(parseInt(props.totalSize, 10)) })
            }

            // Hive unique: numFiles
            if (props.numFiles) {
              const files = parseInt(props.numFiles, 10)
              specs.push({ label: 'Files', value: isNaN(files) ? '-' : files.toLocaleString() })
            }

            setTableSpecs(specs)
          }
        })
        .catch((error) => {
          console.error('GettableDetails failed:', error)
        })
    }
  }, [table.id])

  // load table tag
  useEffect(() => {
    const parts = table.id.split('.')
    if (parts.length === 3) {
      const fullName = table.id
      // Get the table tags（exclude range tag）
      getObjectTags('TABLE', fullName)
        .then((tags) => setTableTags(tags.filter((t) => !t.startsWith(VALUE_DOMAIN_TAG_PREFIX))))
        .catch(console.error)
    }
  }, [table.id])

  /** Add table tag */
  const handleAddTableTag = async (tagName: string) => {
    try {
      // Create first tag（If it already exists, errors will be ignored.）
      try {
        await createTag(tagName)
      } catch {
        // tag may already exist，ignore
      }
      // association tag Arrive at the table
      await associateObjectTags('TABLE', table.id, [tagName], [])
      setTableTags((prev) => [...prev, tagName])
      toast.success(`Label added: ${tagName}`)
    } catch {
      // The error was passed by the unity client toast show
    }
  }

  /** Remove table tag */
  const handleRemoveTableTag = async (tagName: string) => {
    try {
      await associateObjectTags('TABLE', table.id, [], [tagName])
      setTableTags((prev) => prev.filter((t) => t !== tagName))
      toast.success(`Label removed: ${tagName}`)
    } catch {
      // The error was passed by the unity client toast show
    }
  }

  /** Get value range type icon */
  const getDomainIcon = (domainType?: string) => {
    switch (domainType?.toUpperCase()) {
      case 'ENUM':
        return <List size={10} className="text-purple-500" />
      case 'RANGE':
        return <Hash size={10} className="text-blue-500" />
      case 'REGEX':
        return <Type size={10} className="text-amber-500" />
      default:
        return <Pin size={10} className="text-indigo-400" />
    }
  }

  /** Get the value field type label */
  const getDomainTypeLabel = (domainType?: string) => {
    switch (domainType?.toUpperCase()) {
      case 'ENUM':
        return 'enumeration'
      case 'RANGE':
        return 'scope'
      case 'REGEX':
        return 'regular'
      default:
        return 'unknown'
    }
  }

  /** Associate a range with a column tag */
  const handleAssociateValueDomain = async (columnName: string, domainCode: string) => {
    const columnFullName = `${table.id}.${columnName}`
    const tagName = `${VALUE_DOMAIN_TAG_PREFIX}${domainCode}`
    try {
      // If there is an association，Remove first
      const currentDomain = columnValueDomainMap.get(columnName)
      const tagsToRemove = currentDomain ? [`${VALUE_DOMAIN_TAG_PREFIX}${currentDomain}`] : []

      await associateObjectTags('COLUMN', columnFullName, [tagName], tagsToRemove)
      setColumnValueDomainMap((prev) => new Map(prev).set(columnName, domainCode))
      setOpenDropdown(null)
      toast.success('Value range association successful')
    } catch {
      // The error was passed by the unity client toast show
    }
  }

  /** Remove range association from column */
  const handleRemoveValueDomain = async (columnName: string) => {
    const columnFullName = `${table.id}.${columnName}`
    const currentDomain = columnValueDomainMap.get(columnName)
    if (!currentDomain) return

    try {
      await associateObjectTags('COLUMN', columnFullName, [], [`${VALUE_DOMAIN_TAG_PREFIX}${currentDomain}`])
      setColumnValueDomainMap((prev) => {
        const next = new Map(prev)
        next.delete(columnName)
        return next
      })
      toast.success('Range association removed')
    } catch {
      // The error was passed by the unity client toast show
    }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 px-6 @md:px-8 py-7 shadow-sm">
        <div className={`flex items-center gap-2 ${TYPOGRAPHY.legal} uppercase tracking-widest text-slate-400 dark:text-slate-500 mb-4`}>
          {breadcrumb.map((crumb, index) => (
            <span key={crumb} className="flex items-center gap-2">
              {index > 0 && <span className="text-slate-300">/</span>}
              <span className="hover:text-indigo-600 cursor-pointer">{crumb}</span>
            </span>
          ))}
        </div>

        <div className="flex flex-col gap-4 @md:flex-row @md:items-start @md:justify-between">
          <div className="flex gap-5">
            <div className={`${iconContainerSizeClassMap.small} rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 text-white flex items-center justify-center shadow-md shadow-blue-200 dark:shadow-blue-900/40`}>
              <TableIcon size={24} />
            </div>
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <h2 className="text-xl font-bold text-slate-900 dark:text-white">{table.name}</h2>
                {/* Tag area */}
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setTagDropdownOpen(true)}
                    className="text-slate-400 hover:text-emerald-500 transition-colors"
                    title="Add tag"
                  >
                    <Tag size={14} />
                  </button>
                  {tableTags.map((tagName) => (
                    <span
                      key={tagName}
                      className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-medium bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-full border border-emerald-200 dark:border-emerald-800"
                    >
                      {tagName}
                      <button
                        type="button"
                        onClick={() => handleRemoveTableTag(tagName)}
                        className="text-emerald-400 hover:text-red-500"
                        title="Remove"
                      >
                        <X size={10} />
                      </button>
                    </span>
                  ))}
                  {tagDropdownOpen && (
                    <input
                      type="text"
                      autoFocus
                      placeholder="Enter to add"
                      className="w-24 px-2 py-0.5 text-xs border border-emerald-300 dark:border-emerald-700 rounded-full bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-1 focus:ring-emerald-500"
                      onKeyDown={async (e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          const input = e.target as HTMLInputElement
                          const value = input.value.trim()
                          if (value) {
                            await handleAddTableTag(value)
                          }
                          setTagDropdownOpen(false)
                        } else if (e.key === 'Escape') {
                          setTagDropdownOpen(false)
                        }
                      }}
                      onBlur={() => setTagDropdownOpen(false)}
                    />
                  )}
                </div>
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
              <div className="flex items-center gap-4 text-xs text-slate-600 dark:text-slate-300">
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
              <div className="flex items-center flex-wrap gap-3">
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
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg bg-blue-600 hover:bg-blue-700 text-white shadow-sm transition-colors"
              title="Query"
            >
              <Play size={16} />
            </button>
            <button
              type="button"
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
              title="Share"
            >
              <Share2 size={16} />
            </button>
            <button
              type="button"
              onClick={() => {
                if (activeTab === 'COLUMNS') {
                  // Column editing mode
                  onEdit?.('columns', {
                    name: table.name,
                    comment: description,
                    columns: columns.map((col) => ({
                      name: col.name,
                      type: col.dataType,
                      comment: col.comment,
                      valueDomainCode: columnValueDomainMap.get(col.name)
                    }))
                  })
                } else {
                  // Basic information editing mode（OVERVIEW and others Tab）
                  onEdit?.('basic', {
                    name: table.name,
                    comment: description,
                    properties
                  })
                }
              }}
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
              title="Edit"
            >
              <Pencil size={16} />
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
                <Card>
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
                </Card>
                <Card className="@md:col-span-2">
                  <h4 className={`${TYPOGRAPHY.legal} font-bold uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-4`}>Governance & Usage</h4>
                  <div className="grid grid-cols-3 gap-4">
                    <UsageStat label="Weekly Queries" value="1.2k" />
                    <UsageStat label="Downstream Jobs" value="42" />
                    <UsageStat label="SLA Met" value="99.9%" tone="text-green-600" />
                  </div>
                </Card>
              </div>

              <Card padding="none" className="overflow-hidden">
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
              </Card>
            </div>
          )}

          {activeTab === 'COLUMNS' && (
            <>
              <Table layout="auto" minWidth="none">
                <TableHeader className="bg-slate-50 dark:bg-slate-800 text-slate-500 dark:text-slate-300 border-b border-slate-200 dark:border-slate-700">
	                  <TableRow>
	                    <TableHead className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Column Name</TableHead>
	                    <TableHead className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Data Type</TableHead>
	                    <TableHead className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Comment</TableHead>
	                  </TableRow>
	                </TableHeader>
                <TableBody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {columns.map((col) => {
                    const indexTypes = columnIndexMap.get(col.name)
                    const isPrimaryKey = indexTypes?.has('PRIMARY_KEY') ?? false
                    const isUniqueKey = indexTypes?.has('UNIQUE_KEY') ?? false
                    const currentDomainCode = columnValueDomainMap.get(col.name)
                    const currentDomain = valueDomains.find((d) => d.domainCode === currentDomainCode)

                    return (
                      <TableRow
                        key={col.name}
                        className="group hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors"
                      >
                        <TableCell className="px-6 py-3 font-mono text-caption text-slate-800 dark:text-slate-100">
                          <div className="flex items-center gap-2">
                            {/* List + value range tag container */}
                            <span className="relative inline-flex items-center">
                              {col.name}
                              {/* Range labels/Thumbtack - Close to the upper right corner of the column name */}
                              <span
                                className="absolute -top-2 left-full ml-0.5"
                                ref={(el) => { if (el) dropdownButtonRefs.current.set(col.name, el) }}
                              >
                              {currentDomain ? (
                                <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-micro font-medium bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-full border border-indigo-200 dark:border-indigo-800">
                                  <span
                                    className="inline-flex items-center gap-0.5 cursor-pointer hover:text-indigo-800"
                                    title={`Click to modify the value range: ${currentDomain.domainName}`}
                                    onClick={() => handleOpenValueDomainDropdown(col.name)}
                                  >
                                    {getDomainIcon(currentDomain.domainType)}
                                    <span className="max-w-16 truncate">{currentDomain.domainName}</span>
                                  </span>
                                  <span
                                    className="text-indigo-400 hover:text-red-500 cursor-pointer ml-0.5"
                                    title="clear association"
                                    onClick={(e) => { e.stopPropagation(); handleRemoveValueDomain(col.name) }}
                                  >×</span>
                                </span>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => handleOpenValueDomainDropdown(col.name)}
                                  className="h-4 w-4 inline-flex items-center justify-center rounded-full text-slate-300 hover:text-indigo-500 hover:bg-indigo-50 transition-colors opacity-0 group-hover:opacity-100"
                                  title="associated range"
                                >
                                  <Pin size={10} />
                                </button>
                              )}
                              </span>
                            </span>
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
	                        </TableCell>
	                        <TableCell className="px-6 py-3 font-mono text-caption text-slate-500 dark:text-slate-300">{col.dataType}</TableCell>
	                        <TableCell className="px-6 py-3 text-caption text-slate-600 dark:text-slate-300">{col.comment ?? '-'}</TableCell>
	                      </TableRow>
	                    )
	                  })}
                </TableBody>
              </Table>
              {/* Value range selection drop-down - Portal Arrive body */}
              {openDropdown && dropdownPos && createPortal(
                <div
                  ref={dropdownRef}
                  style={{ top: dropdownPos.top, left: dropdownPos.left }}
                  className={`fixed ${menuWidthClassMap.medium} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-[1000000]`}
                >
                  <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 rounded-t-xl">
                    <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">Select range</span>
                  </div>
                  <div className="max-h-48 overflow-y-auto py-1">
                    {!valueDomainsLoaded ? (
                      <div className="px-3 py-2 text-xs text-slate-400 text-center">Loading...</div>
                    ) : valueDomains.length === 0 ? (
                      <div className="px-3 py-2 text-xs text-slate-400 text-center">No value range yet</div>
                    ) : (
                    valueDomains.map((domain) => (
                      <div
                        key={domain.domainCode}
                        ref={hoveredDomain === domain.domainCode ? hoverItemRef : null}
                        onMouseEnter={() => handleDomainItemMouseEnter(domain.domainCode)}
                        onMouseLeave={handleDomainItemMouseLeave}
                        className={`flex items-center justify-between px-3 py-1.5 text-xs cursor-pointer transition-colors ${
                          hoveredDomain === domain.domainCode
                            ? 'bg-blue-50 dark:bg-blue-900/30'
                            : columnValueDomainMap.get(openDropdown) === domain.domainCode
                              ? 'text-blue-600 font-medium bg-blue-50/50 dark:bg-blue-900/20'
                              : 'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700'
                        }`}
                        onClick={() => {
                          // Get the data type of a column
                          const columnDataType = columns.find((c) => c.name === openDropdown)?.dataType || ''
                          // Verify that data types match
                          if (!domain.dataType) {
                            toast.error(`range ${domain.domainName} No data type specified，Unable to associate`)
                            return
                          }
                          if (!isDataTypeCompatible(columnDataType, domain.dataType)) {
                            toast.error(`Data type mismatch：The column type is ${columnDataType.toUpperCase()}，The value range type is ${domain.dataType.toUpperCase()}`)
                            return
                          }
                          handleAssociateValueDomain(openDropdown, domain.domainCode)
                        }}
                      >
                        <span className="truncate">{domain.domainName}</span>
                        <ChevronRight size={12} className={`flex-shrink-0 ml-1 ${hoveredDomain === domain.domainCode ? 'text-blue-400' : 'text-slate-300'}`} />
                      </div>
                    ))
                    )}
                  </div>
                </div>,
                document.body
              )}
              {/* Value range details card - Portal */}
              {activeHoveredDomain && domainCardPos && createPortal(
                <div
                  ref={domainCardRef}
                  style={{ top: domainCardPos.top, left: domainCardPos.left }}
                  className={`fixed z-[1000001] ${menuWidthClassMap.xxlarge} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-2xl animate-in fade-in-0 slide-in-from-left-2 duration-150`}
                  onMouseEnter={handleDomainCardMouseEnter}
                  onMouseLeave={handleDomainCardMouseLeave}
                >
                  {/* card header */}
                  <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 rounded-t-xl">
                    <div className="flex items-center gap-2">
                      {getDomainIcon(activeHoveredDomain.domainType)}
                      <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 truncate">{activeHoveredDomain.domainName}</span>
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <span className="text-micro px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-700 text-slate-500 dark:text-slate-400">
                        {getDomainTypeLabel(activeHoveredDomain.domainType)}
                      </span>
                      {activeHoveredDomain.dataType && (
                        <span className="text-micro px-1.5 py-0.5 rounded font-mono bg-cyan-50 dark:bg-cyan-900/30 text-cyan-600 dark:text-cyan-400">
                          {activeHoveredDomain.dataType}
                        </span>
                      )}
                      {activeHoveredDomain.domainLevel && (
                        <span className={`text-micro px-1.5 py-0.5 rounded ${activeHoveredDomain.domainLevel === 'BUILTIN' ? 'bg-amber-100 text-amber-600' : 'bg-blue-100 text-blue-600'}`}>
                          {activeHoveredDomain.domainLevel === 'BUILTIN' ? 'Built-in' : 'Business'}
                        </span>
                      )}
                    </div>
                  </div>
                  {/* List of enumeration items */}
                  {activeHoveredDomain.items.length > 0 && (
                    <div className="px-3 py-2">
                      <p className="text-micro font-medium text-slate-400 dark:text-slate-500 mb-1.5">enumeration value ({activeHoveredDomain.items.length})</p>
                      <div className="max-h-32 overflow-y-auto space-y-1">
                        {activeHoveredDomain.items.slice(0, 10).map((item) => (
                          <div key={item.value} className="flex items-center gap-2 text-micro">
                            <span className="font-mono text-slate-600 dark:text-slate-300">{item.value}</span>
                            {item.label && <span className="text-slate-400 truncate">({item.label})</span>}
                          </div>
                        ))}
                        {activeHoveredDomain.items.length > 10 && (
                          <p className="text-micro text-slate-400">...Also {activeHoveredDomain.items.length - 10} item</p>
                        )}
                      </div>
                    </div>
                  )}
                </div>,
                document.body
              )}
            </>
          )}

          {activeTab === 'QUALITY' && (
            <div className="space-y-6">
              <Card>
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-bold text-slate-800 dark:text-slate-100">Quality Metrics Trend</h3>
                  <span className="text-xs font-medium text-slate-500 dark:text-slate-400 px-2 py-1 bg-slate-100 dark:bg-slate-800 rounded">Last 30 Days</span>
                </div>
                <div className="h-56 rounded-lg border border-dashed border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/60 flex items-center justify-center text-sm text-slate-400 dark:text-slate-500">
                  Trend chart placeholder
                </div>
              </Card>

              <Card padding="none" className="overflow-hidden">
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
              </Card>
            </div>
          )}

          {activeTab === 'LINEAGE' && (
            <Card padding="none" className="h-[420px] flex items-center justify-center relative overflow-hidden">
              <div className="absolute inset-0 bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] dark:bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:18px_18px] opacity-50" />
              <div className="z-10 text-sm text-slate-500 dark:text-slate-400">Lineage graph placeholder</div>
            </Card>
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
