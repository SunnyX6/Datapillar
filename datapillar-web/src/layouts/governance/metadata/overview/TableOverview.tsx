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
  Lock,
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
import { formatTime } from '@/lib/utils'
import { type TableAsset } from '../type/types'
import { getTable, associateObjectTags, getObjectTags, createTag } from '@/services/oneMetaService'
import { fetchValueDomains, type ValueDomainDTO } from '@/services/oneMetaSemanticService'
import type { GravitinoIndexDTO } from '@/types/oneMeta'
import { Card, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'

const VALUE_DOMAIN_TAG_PREFIX = 'vd:'

/** 数据类型等价组（同组内的类型视为兼容） */
const TYPE_EQUIVALENTS: string[][] = [
  ['STRING', 'VARCHAR', 'TEXT', 'CHAR', 'FIXEDCHAR'],
  ['INT', 'INTEGER'],
  ['BIGINT', 'LONG'],
  ['FLOAT', 'REAL'],
  ['DOUBLE', 'DOUBLE PRECISION'],
  ['BOOLEAN', 'BOOL']
]

/** 检查列数据类型与值域数据类型是否兼容（忽略大小写，考虑等价类型） */
function isDataTypeCompatible(columnDataType: string, domainDataType?: string): boolean {
  if (!domainDataType) return false // 值域未指定数据类型，不允许关联
  // 提取基础类型（去掉括号中的参数）
  const normalizeType = (type: string) => type.toUpperCase().replace(/\(.*\)$/, '')
  const colType = normalizeType(columnDataType)
  const domType = normalizeType(domainDataType)

  // 完全相同
  if (colType === domType) return true

  // 检查是否在同一等价组
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

// 格式化字节大小
const formatBytes = (bytes: number): string => {
  if (isNaN(bytes) || bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

/** 编辑模式类型 */
export type TableEditMode = 'basic' | 'columns'

/** 表基础信息编辑数据 */
export interface TableBasicEditData {
  name: string
  comment?: string
  properties?: Record<string, string>
}

/** 表列编辑数据 */
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
 * 判断是否为支持索引的 JDBC 类型 catalog
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
  // 动态展示的属性，根据不同 catalog 返回的 properties 动态生成
  const [tableSpecs, setTableSpecs] = useState<{ label: string; value: string }[]>([])
  // 值域相关状态
  const [valueDomains, setValueDomains] = useState<ValueDomainDTO[]>([])
  const [valueDomainsLoaded, setValueDomainsLoaded] = useState(false)
  const [columnValueDomainMap, setColumnValueDomainMap] = useState<Map<string, string>>(new Map())
  const [openDropdown, setOpenDropdown] = useState<string | null>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null)
  const dropdownButtonRefs = useRef<Map<string, HTMLElement>>(new Map())
  const dropdownRef = useRef<HTMLDivElement>(null)

  // 值域悬浮详情相关状态
  const [hoveredDomain, setHoveredDomain] = useState<string | null>(null)
  const [domainCardPos, setDomainCardPos] = useState<{ top: number; left: number } | null>(null)
  const hoverItemRef = useRef<HTMLDivElement>(null)
  const hoverTimeout = useRef<NodeJS.Timeout | null>(null)
  const domainCardRef = useRef<HTMLDivElement>(null)
  const isCardHovered = useRef(false)

  // 表 tag 相关状态
  const [tableTags, setTableTags] = useState<string[]>([])
  const [tagDropdownOpen, setTagDropdownOpen] = useState(false)

  const closeDropdown = () => {
    setOpenDropdown(null)
    setDropdownPos(null)
    setHoveredDomain(null)
  }

  /** 打开值域下拉（首次打开时加载值域列表） */
  const handleOpenValueDomainDropdown = async (columnName: string) => {
    if (openDropdown === columnName) {
      closeDropdown()
      return
    }
    setOpenDropdown(columnName)
    // 首次打开时加载值域列表
    if (!valueDomainsLoaded) {
      try {
        const result = await fetchValueDomains(0, 100)
        setValueDomains(result.items)
        setValueDomainsLoaded(true)
      } catch (error) {
        console.error('加载值域列表失败:', error)
      }
    }
  }

  // 值域悬浮事件处理
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

  // 计算值域详情卡片位置
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

  // 点击外部关闭下拉
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

  // 计算下拉位置
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

            // 获取每个列的 tag，提取值域关联
            const loadColumnTags = async () => {
              const domainMap = new Map<string, string>()
              await Promise.all(
                detail.columns.map(async (col) => {
                  try {
                    const columnFullName = `${catalogName}.${schemaName}.${tableName}.${col.name}`
                    const tags = await getObjectTags('COLUMN', columnFullName)
                    // 找出值域 tag（以 vd: 开头）
                    const valueDomainTag = tags.find((tag) => tag.startsWith(VALUE_DOMAIN_TAG_PREFIX))
                    if (valueDomainTag) {
                      const domainCode = valueDomainTag.slice(VALUE_DOMAIN_TAG_PREFIX.length)
                      domainMap.set(col.name, domainCode)
                    }
                  } catch {
                    // 忽略单个列的 tag 获取失败
                  }
                })
              )
              setColumnValueDomainMap(domainMap)
            }
            loadColumnTags()
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
            // 优先使用更新时间，没有则使用创建时间
            const timeValue = detail.audit.lastModifiedTime || detail.audit.createTime
            if (timeValue) {
              setUpdatedAt(formatTime(timeValue))
            }
          }

          // 根据 properties 动态构建展示字段
          if (detail.properties) {
            setProperties(detail.properties)
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

  // 加载表 tag
  useEffect(() => {
    const parts = table.id.split('.')
    if (parts.length === 3) {
      const fullName = table.id
      // 获取表的 tags（排除值域 tag）
      getObjectTags('TABLE', fullName)
        .then((tags) => setTableTags(tags.filter((t) => !t.startsWith(VALUE_DOMAIN_TAG_PREFIX))))
        .catch(console.error)
    }
  }, [table.id])

  /** 添加表 tag */
  const handleAddTableTag = async (tagName: string) => {
    try {
      // 先创建 tag（如果已存在会忽略错误）
      try {
        await createTag(tagName)
      } catch {
        // tag 可能已存在，忽略
      }
      // 关联 tag 到表
      await associateObjectTags('TABLE', table.id, [tagName], [])
      setTableTags((prev) => [...prev, tagName])
      toast.success(`已添加标签: ${tagName}`)
    } catch {
      // 错误已由统一客户端通过 toast 显示
    }
  }

  /** 移除表 tag */
  const handleRemoveTableTag = async (tagName: string) => {
    try {
      await associateObjectTags('TABLE', table.id, [], [tagName])
      setTableTags((prev) => prev.filter((t) => t !== tagName))
      toast.success(`已移除标签: ${tagName}`)
    } catch {
      // 错误已由统一客户端通过 toast 显示
    }
  }

  /** 获取值域类型图标 */
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

  /** 获取值域类型标签 */
  const getDomainTypeLabel = (domainType?: string) => {
    switch (domainType?.toUpperCase()) {
      case 'ENUM':
        return '枚举'
      case 'RANGE':
        return '范围'
      case 'REGEX':
        return '正则'
      default:
        return '未知'
    }
  }

  /** 为列关联值域 tag */
  const handleAssociateValueDomain = async (columnName: string, domainCode: string) => {
    const columnFullName = `${table.id}.${columnName}`
    const tagName = `${VALUE_DOMAIN_TAG_PREFIX}${domainCode}`
    try {
      // 如果已有关联，先移除
      const currentDomain = columnValueDomainMap.get(columnName)
      const tagsToRemove = currentDomain ? [`${VALUE_DOMAIN_TAG_PREFIX}${currentDomain}`] : []

      await associateObjectTags('COLUMN', columnFullName, [tagName], tagsToRemove)
      setColumnValueDomainMap((prev) => new Map(prev).set(columnName, domainCode))
      setOpenDropdown(null)
      toast.success('值域关联成功')
    } catch {
      // 错误已由统一客户端通过 toast 显示
    }
  }

  /** 移除列的值域关联 */
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
      toast.success('值域关联已移除')
    } catch {
      // 错误已由统一客户端通过 toast 显示
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
                {/* Tag 区域 */}
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => setTagDropdownOpen(true)}
                    className="text-slate-400 hover:text-emerald-500 transition-colors"
                    title="添加标签"
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
                        title="移除"
                      >
                        <X size={10} />
                      </button>
                    </span>
                  ))}
                  {tagDropdownOpen && (
                    <input
                      type="text"
                      autoFocus
                      placeholder="回车添加"
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
              title="查询"
            >
              <Play size={16} />
            </button>
            <button
              type="button"
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
              title="分享"
            >
              <Share2 size={16} />
            </button>
            <button
              type="button"
              onClick={() => {
                if (activeTab === 'COLUMNS') {
                  // 列编辑模式
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
                  // 基础信息编辑模式（OVERVIEW 及其他 Tab）
                  onEdit?.('basic', {
                    name: table.name,
                    comment: description,
                    properties
                  })
                }
              }}
              className="h-9 w-9 inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
              title="编辑"
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
                    <TableHead className={`px-6 py-3 text-left ${TYPOGRAPHY.legal} uppercase tracking-widest`}>Tags</TableHead>
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
                      <TableRow key={col.name} className="group hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors">
                        <TableCell className="px-6 py-3 font-mono text-caption text-slate-800 dark:text-slate-100">
                          <div className="flex items-center gap-2">
                            {/* 列名 + 值域标记容器 */}
                            <span className="relative inline-flex items-center">
                              {col.name}
                              {/* 值域标签/图钉 - 紧贴列名右上角 */}
                              <span
                                className="absolute -top-2 left-full ml-0.5"
                                ref={(el) => { if (el) dropdownButtonRefs.current.set(col.name, el) }}
                              >
                              {currentDomain ? (
                                <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-micro font-medium bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-full border border-indigo-200 dark:border-indigo-800">
                                  <span
                                    className="inline-flex items-center gap-0.5 cursor-pointer hover:text-indigo-800"
                                    title={`点击修改值域: ${currentDomain.domainName}`}
                                    onClick={() => handleOpenValueDomainDropdown(col.name)}
                                  >
                                    {getDomainIcon(currentDomain.domainType)}
                                    <span className="max-w-16 truncate">{currentDomain.domainName}</span>
                                  </span>
                                  <span
                                    className="text-indigo-400 hover:text-red-500 cursor-pointer ml-0.5"
                                    title="清除关联"
                                    onClick={(e) => { e.stopPropagation(); handleRemoveValueDomain(col.name) }}
                                  >×</span>
                                </span>
                              ) : (
                                <button
                                  type="button"
                                  onClick={() => handleOpenValueDomainDropdown(col.name)}
                                  className="h-4 w-4 inline-flex items-center justify-center rounded-full text-slate-300 hover:text-indigo-500 hover:bg-indigo-50 transition-colors opacity-0 group-hover:opacity-100"
                                  title="关联值域"
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
                        <TableCell className="px-6 py-3 text-caption">
                          {col.piiTag && (
                            <span className={`inline-flex items-center gap-1 px-2 py-1 rounded ${TYPOGRAPHY.micro} font-bold bg-rose-50 text-rose-600 border border-rose-100`}>
                              <Lock size={10} />
                              {col.piiTag}
                            </span>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              {/* 值域选择下拉 - Portal 到 body */}
              {openDropdown && dropdownPos && createPortal(
                <div
                  ref={dropdownRef}
                  style={{ top: dropdownPos.top, left: dropdownPos.left }}
                  className={`fixed ${menuWidthClassMap.medium} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-[1000000]`}
                >
                  <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 rounded-t-xl">
                    <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">选择值域</span>
                  </div>
                  <div className="max-h-48 overflow-y-auto py-1">
                    {!valueDomainsLoaded ? (
                      <div className="px-3 py-2 text-xs text-slate-400 text-center">加载中...</div>
                    ) : valueDomains.length === 0 ? (
                      <div className="px-3 py-2 text-xs text-slate-400 text-center">暂无值域</div>
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
                          // 获取列的数据类型
                          const columnDataType = columns.find((c) => c.name === openDropdown)?.dataType || ''
                          // 校验数据类型是否匹配
                          if (!domain.dataType) {
                            toast.error(`值域 ${domain.domainName} 未指定数据类型，无法关联`)
                            return
                          }
                          if (!isDataTypeCompatible(columnDataType, domain.dataType)) {
                            toast.error(`数据类型不匹配：列类型为 ${columnDataType.toUpperCase()}，值域类型为 ${domain.dataType.toUpperCase()}`)
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
              {/* 值域详情卡片 - Portal */}
              {activeHoveredDomain && domainCardPos && createPortal(
                <div
                  ref={domainCardRef}
                  style={{ top: domainCardPos.top, left: domainCardPos.left }}
                  className={`fixed z-[1000001] ${menuWidthClassMap.xxlarge} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-2xl animate-in fade-in-0 slide-in-from-left-2 duration-150`}
                  onMouseEnter={handleDomainCardMouseEnter}
                  onMouseLeave={handleDomainCardMouseLeave}
                >
                  {/* 卡片头部 */}
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
                          {activeHoveredDomain.domainLevel === 'BUILTIN' ? '内置' : '业务'}
                        </span>
                      )}
                    </div>
                  </div>
                  {/* 枚举项列表 */}
                  {activeHoveredDomain.items.length > 0 && (
                    <div className="px-3 py-2">
                      <p className="text-micro font-medium text-slate-400 dark:text-slate-500 mb-1.5">枚举值 ({activeHoveredDomain.items.length})</p>
                      <div className="max-h-32 overflow-y-auto space-y-1">
                        {activeHoveredDomain.items.slice(0, 10).map((item) => (
                          <div key={item.value} className="flex items-center gap-2 text-micro">
                            <span className="font-mono text-slate-600 dark:text-slate-300">{item.value}</span>
                            {item.label && <span className="text-slate-400 truncate">({item.label})</span>}
                          </div>
                        ))}
                        {activeHoveredDomain.items.length > 10 && (
                          <p className="text-micro text-slate-400">...还有 {activeHoveredDomain.items.length - 10} 项</p>
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
