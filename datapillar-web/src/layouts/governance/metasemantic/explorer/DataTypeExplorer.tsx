import { useState, useMemo } from 'react'
import { ArrowLeft, Hash, Database, Type, Calendar, Check, Braces, Binary, Clock, ToggleLeft, FileCode, Layers, List, Map, Fingerprint } from 'lucide-react'
import { iconSizeToken, panelWidthClassMap } from '@/design-tokens/dimensions'
import { DEFAULT_LENGTH, DEFAULT_MAX_LENGTH, getMaxLengthForType } from '@/layouts/governance/utils/dataType'
import type { DataTypeItem } from '@/pages/governance/DataTypePage'

/** 数据类型分组 - 基于 Gravitino Types.java */
const DATA_TYPE_GROUPS = [
  { id: 'INTEGRAL', label: 'INTEGRAL' },
  { id: 'FRACTION', label: 'FRACTION' },
  { id: 'STRING', label: 'STRING' },
  { id: 'DATETIME', label: 'DATETIME' },
  { id: 'COMPLEX', label: 'COMPLEX' }
] as const

/** 预置数据类型 - 基于 Gravitino Types.java */
const DATA_TYPES: DataTypeItem[] = [
  // 整数类型 (IntegralType)
  {
    id: 'byte',
    name: 'BYTE',
    label: 'Byte',
    category: 'INTEGRAL',
    icon: 'hash',
    description: '8位有符号整数，取值范围 -128 到 127，支持 unsigned 模式。'
  },
  {
    id: 'short',
    name: 'SHORT',
    label: 'Short',
    category: 'INTEGRAL',
    icon: 'hash',
    description: '16位有符号整数，取值范围 -32,768 到 32,767，支持 unsigned 模式。'
  },
  {
    id: 'integer',
    name: 'INTEGER',
    label: 'Integer',
    category: 'INTEGRAL',
    icon: 'hash',
    description: '32位有符号整数，取值范围 -2,147,483,648 到 2,147,483,647，支持 unsigned 模式。'
  },
  {
    id: 'long',
    name: 'LONG',
    label: 'Long',
    category: 'INTEGRAL',
    icon: 'database',
    description: '64位有符号整数，取值范围极大，适用于大数据量的主键、时间戳等场景。'
  },
  // 小数类型 (FractionType)
  {
    id: 'float',
    name: 'FLOAT',
    label: 'Float',
    category: 'FRACTION',
    icon: 'braces',
    description: '单精度浮点数，适用于对精度要求不高的数值计算场景。'
  },
  {
    id: 'double',
    name: 'DOUBLE',
    label: 'Double',
    category: 'FRACTION',
    icon: 'braces',
    description: '双精度浮点数，适用于科学计算和需要更高精度的数值场景。'
  },
  {
    id: 'decimal',
    name: 'DECIMAL',
    label: 'Decimal',
    category: 'FRACTION',
    icon: 'braces',
    description: '定点高精度数值，通过定义 Precision (1-38) 和 Scale 来满足严苛的业务计算场景。',
    badge: 'PRECISION ARCHITECT',
    hasPrecision: true,
    hasScale: true,
    maxPrecision: 38,
    maxScale: 18
  },
  // 字符串类型 (PrimitiveType)
  {
    id: 'string',
    name: 'STRING',
    label: 'String',
    category: 'STRING',
    icon: 'type',
    description: '无长度限制的字符串类型，等价于 varchar(MAX)，具体长度由底层 catalog 决定。'
  },
  {
    id: 'varchar',
    name: 'VARCHAR',
    label: 'VarChar',
    category: 'STRING',
    icon: 'type',
    description: '可变长度字符串，需指定最大长度，适用于已知长度上限的文本数据。',
    hasLength: true,
    maxLength: getMaxLengthForType('VARCHAR')
  },
  {
    id: 'fixedchar',
    name: 'FIXEDCHAR',
    label: 'Char',
    category: 'STRING',
    icon: 'type',
    description: '固定长度字符串，需指定长度，不足部分用空格填充。',
    hasLength: true,
    maxLength: getMaxLengthForType('FIXEDCHAR')
  },
  {
    id: 'binary',
    name: 'BINARY',
    label: 'Binary',
    category: 'STRING',
    icon: 'binary',
    description: '二进制数据类型，适用于存储图片、文件等二进制内容。'
  },
  {
    id: 'uuid',
    name: 'UUID',
    label: 'UUID',
    category: 'STRING',
    icon: 'fingerprint',
    description: '通用唯一标识符，128位，适用于分布式系统中的唯一ID生成。'
  },
  {
    id: 'boolean',
    name: 'BOOLEAN',
    label: 'Boolean',
    category: 'STRING',
    icon: 'toggle',
    description: '布尔类型，只有 true 和 false 两个值，适用于开关、标志位等场景。'
  },
  // 日期时间类型 (DateTimeType)
  {
    id: 'date',
    name: 'DATE',
    label: 'Date',
    category: 'DATETIME',
    icon: 'calendar',
    description: '日期类型，格式为 YYYY-MM-DD，不包含时间信息。'
  },
  {
    id: 'time',
    name: 'TIME',
    label: 'Time',
    category: 'DATETIME',
    icon: 'clock',
    description: '时间类型，格式为 HH:MM:SS，可选精度 0-9，不包含日期信息。'
  },
  {
    id: 'timestamp',
    name: 'TIMESTAMP',
    label: 'Timestamp',
    category: 'DATETIME',
    icon: 'calendar',
    description: '时间戳类型，包含日期和时间，可选时区和精度 0-9。'
  },
  // 复杂类型 (ComplexType)
  {
    id: 'struct',
    name: 'STRUCT',
    label: 'Struct',
    category: 'COMPLEX',
    icon: 'layers',
    description: '结构体类型，包含多个命名字段，每个字段可以是任意类型。'
  },
  {
    id: 'list',
    name: 'LIST',
    label: 'List',
    category: 'COMPLEX',
    icon: 'list',
    description: '列表类型，包含同一类型的多个元素，元素可为空或非空。'
  },
  {
    id: 'map',
    name: 'MAP',
    label: 'Map',
    category: 'COMPLEX',
    icon: 'map',
    description: '映射类型，键值对结构，键和值可以是任意类型。'
  }
]

/** 图标映射 */
const ICON_MAP: Record<string, React.ElementType> = {
  hash: Hash,
  database: Database,
  braces: Braces,
  type: Type,
  calendar: Calendar,
  binary: Binary,
  clock: Clock,
  toggle: ToggleLeft,
  code: FileCode,
  layers: Layers,
  list: List,
  map: Map,
  fingerprint: Fingerprint
}

interface DataTypeExplorerProps {
  onBack: () => void
  selectedType: DataTypeItem | null
  onSelectType: (type: DataTypeItem | null) => void
}

export function DataTypeExplorer({ onBack, selectedType, onSelectType }: DataTypeExplorerProps) {
  const [precision, setPrecision] = useState(18)
  const [scale, setScale] = useState(2)
  const [length, setLength] = useState(DEFAULT_LENGTH)

  // 按分组整理数据类型
  const groupedTypes = useMemo(() => {
    return DATA_TYPE_GROUPS.map((group) => ({
      ...group,
      types: DATA_TYPES.filter((t) => t.category === group.id)
    }))
  }, [])

  // 默认选中 Decimal
  const currentType = selectedType || DATA_TYPES.find((t) => t.id === 'decimal')!

  const IconComponent = ICON_MAP[currentType.icon] || Hash
  const lengthMax = currentType.maxLength ?? DEFAULT_MAX_LENGTH

  return (
    <div className="flex h-full w-full">
      {/* 左侧类型列表 */}
      <div className={`${panelWidthClassMap.wide} border-r border-slate-200 dark:border-slate-800 flex flex-col bg-slate-50/50 dark:bg-slate-900/50`}>
        {/* 头部 */}
        <div className="p-4 border-b border-slate-200 dark:border-slate-800">
          <button
            onClick={onBack}
            className="flex items-center gap-1.5 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 mb-3 transition-colors"
          >
            <ArrowLeft size={iconSizeToken.small} />
            <span className="text-micro font-medium">返回</span>
          </button>
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-lg bg-slate-800 dark:bg-slate-700 flex items-center justify-center text-white">
              <Database size={iconSizeToken.medium} />
            </div>
            <div>
              <h1 className="text-body font-semibold text-slate-900 dark:text-slate-100">TYPE LIBRARY</h1>
              <p className="text-micro text-slate-400 uppercase tracking-wider">METADATA STANDARD</p>
            </div>
          </div>
        </div>

        {/* 类型列表 */}
        <div className="flex-1 overflow-auto p-3 space-y-4">
          {groupedTypes.map((group) => (
            <div key={group.id}>
              <h3 className="text-micro font-medium text-slate-400 uppercase tracking-wider mb-2 px-2">{group.label}</h3>
              <div className="space-y-0.5">
                {group.types.map((type) => {
                  const TypeIcon = ICON_MAP[type.icon] || Hash
                  const isSelected = currentType.id === type.id
                  return (
                    <button
                      key={type.id}
                      onClick={() => {
                        if (type.id !== currentType.id) {
                          setLength(DEFAULT_LENGTH)
                        }
                        onSelectType(type)
                      }}
                      className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg transition-all duration-200 ${
                        isSelected
                          ? 'bg-white dark:bg-slate-800 shadow-sm border border-slate-200 dark:border-slate-700'
                          : 'hover:bg-white dark:hover:bg-slate-800 hover:shadow-sm hover:border hover:border-slate-200 dark:hover:border-slate-700 border border-transparent'
                      }`}
                    >
                      <div
                        className={`w-8 h-8 rounded-md flex items-center justify-center transition-colors duration-200 ${
                          isSelected
                            ? 'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 group-hover:bg-slate-200 dark:group-hover:bg-slate-700'
                        }`}
                      >
                        <TypeIcon size={iconSizeToken.small} />
                      </div>
                      <span className={`text-body-sm font-medium ${isSelected ? 'text-slate-900 dark:text-slate-100' : 'text-slate-600 dark:text-slate-400'}`}>{type.label}</span>
                      {isSelected && (
                        <div className="ml-auto">
                          <Check size={iconSizeToken.small} className="text-blue-600 dark:text-blue-400" />
                        </div>
                      )}
                    </button>
                  )
                })}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 右侧详情 */}
      <div className="flex-1 overflow-auto p-6">
        <div className="max-w-2xl">
          {/* 标签 */}
          {currentType.badge && (
            <div className="inline-flex px-3 py-1 rounded-full bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-400 text-micro font-medium uppercase tracking-wider mb-3">
              {currentType.badge}
            </div>
          )}

          {/* 标题 */}
          <h1 className="text-heading font-semibold text-slate-900 dark:text-slate-100 mb-1.5">
            {currentType.label}{' '}
            {currentType.hasPrecision && <span className="font-light text-slate-400 dark:text-slate-500">Precision</span>}
            {currentType.hasLength && <span className="font-light text-slate-400 dark:text-slate-500">Length</span>}
          </h1>

          {/* 描述 */}
          <p className="text-body-sm text-slate-600 dark:text-slate-400 mb-6 leading-relaxed">{currentType.description}</p>

          {/* Decimal 专属可视化 */}
          {currentType.hasPrecision && (
            <>
              {/* 存储掩码可视化 */}
              <div className="bg-slate-900 dark:bg-slate-800 rounded-xl p-6 mb-6">
                <div className="text-micro font-medium text-slate-400 uppercase tracking-widest mb-4">LIVE STORAGE MASK</div>
                <div className="flex justify-center mb-6">
                  <div className="flex gap-0.5">
                    {Array.from({ length: precision }).map((_, i) => (
                      <div
                        key={i}
                        className={`w-3 h-8 ${i >= precision - scale ? 'bg-amber-500' : 'bg-white'} ${i === 0 ? 'rounded-l' : ''} ${i === precision - 1 ? 'rounded-r' : ''}`}
                      />
                    ))}
                  </div>
                </div>
                <div className="flex justify-center gap-12">
                  <div>
                    <div className="text-micro font-medium text-slate-500 uppercase tracking-wider mb-0.5">TOTAL PRECISION</div>
                    <div className="flex items-baseline gap-1.5">
                      <span className="text-heading font-semibold text-white">{precision}</span>
                      <span className="text-micro text-slate-500 uppercase">DIGITS</span>
                    </div>
                  </div>
                  <div className="w-px bg-slate-700" />
                  <div>
                    <div className="text-micro font-medium text-amber-500 uppercase tracking-wider mb-0.5">DECIMAL SCALE</div>
                    <div className="flex items-baseline gap-1.5">
                      <span className="text-heading font-semibold text-amber-500">{scale}</span>
                      <span className="text-micro text-slate-500 uppercase">POINTS</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* 配置滑块 */}
              <div className="space-y-4">
                {/* Precision */}
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-1.5 w-28">
                    <div className="w-2.5 h-2.5 rounded-full bg-slate-900 dark:bg-white" />
                    <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">PRECISION</span>
                  </div>
                  <div className="flex-1">
                    <input
                      type="range"
                      min={1}
                      max={currentType.maxPrecision || 38}
                      value={precision}
                      onChange={(e) => {
                        const newPrecision = Number(e.target.value)
                        setPrecision(newPrecision)
                        if (scale > newPrecision) setScale(newPrecision)
                      }}
                      className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-slate-900 dark:accent-white"
                    />
                  </div>
                  <div className="flex items-center gap-1.5 w-20 justify-end">
                    <span className="text-body font-semibold text-blue-600 dark:text-blue-400">{precision}</span>
                    <span className="text-micro text-slate-400">/ {currentType.maxPrecision || 38}</span>
                  </div>
                </div>

                {/* Scale */}
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-1.5 w-28">
                    <div className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                    <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">SCALE</span>
                  </div>
                  <div className="flex-1">
                    <input
                      type="range"
                      min={0}
                      max={precision}
                      value={scale}
                      onChange={(e) => setScale(Number(e.target.value))}
                      className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-amber-500"
                    />
                  </div>
                  <div className="flex items-center gap-1.5 w-20 justify-end">
                    <span className="text-body font-semibold text-amber-600 dark:text-amber-400">{scale}</span>
                    <span className="text-micro text-slate-400">/ {precision}</span>
                  </div>
                </div>
              </div>
            </>
          )}

          {/* VARCHAR/CHAR 长度可视化 */}
          {currentType.hasLength && (
            <>
              {/* 长度可视化 */}
              <div className="bg-slate-900 dark:bg-slate-800 rounded-xl p-6 mb-6">
                <div className="text-micro font-medium text-slate-400 uppercase tracking-widest mb-4">LENGTH CONFIGURATION</div>
                <div className="flex justify-center mb-6">
                  <div className="flex gap-0.5">
                    {Array.from({ length: Math.min(length, 32) }).map((_, i) => (
                      <div
                        key={i}
                        className={`w-2.5 h-8 bg-blue-500 ${i === 0 ? 'rounded-l' : ''} ${i === Math.min(length, 32) - 1 ? 'rounded-r' : ''}`}
                      />
                    ))}
                    {length > 32 && <span className="text-slate-400 ml-2 self-center text-caption">+{length - 32}</span>}
                  </div>
                </div>
                <div className="flex justify-center">
                  <div>
                    <div className="text-micro font-medium text-blue-500 uppercase tracking-wider mb-0.5">MAX LENGTH</div>
                    <div className="flex items-baseline gap-1.5 justify-center">
                      <span className="text-heading font-semibold text-blue-500">{length}</span>
                      <span className="text-micro text-slate-500 uppercase">CHARS</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* 长度滑块 */}
              <div className="flex items-center gap-4">
                <div className="flex items-center gap-1.5 w-28">
                  <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />
                  <span className="font-medium text-slate-900 dark:text-slate-100 uppercase text-caption">LENGTH</span>
                </div>
                <div className="flex-1">
                  <input
                    type="range"
                    min={1}
                    max={lengthMax}
                    value={length}
                    onChange={(e) => setLength(Number(e.target.value))}
                    className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
                  />
                </div>
                <div className="flex items-center gap-1.5 w-24 justify-end">
                  <span className="text-body font-semibold text-blue-600 dark:text-blue-400">{length}</span>
                  <span className="text-micro text-slate-400">/ {lengthMax}</span>
                </div>
              </div>
            </>
          )}

          {/* 基础类型的简单展示 */}
          {!currentType.hasPrecision && !currentType.hasLength && (
            <div className="bg-slate-100 dark:bg-slate-800 rounded-xl p-6">
              <div className="flex items-center justify-center">
                <div className="w-16 h-16 rounded-xl bg-slate-200 dark:bg-slate-700 flex items-center justify-center">
                  <IconComponent size={32} className="text-slate-500 dark:text-slate-400" />
                </div>
              </div>
              <div className="text-center mt-4">
                <div className="text-micro font-medium text-slate-400 uppercase tracking-wider">DATA TYPE</div>
                <div className="text-heading font-semibold text-slate-900 dark:text-slate-100 mt-0.5">{currentType.name}</div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
