/**
 * Demo scenario hook.
 *
 * Provides language-specific scenarios for:
 * 1. Typewriter input animation.
 * 2. Workflow graph rendering.
 * 3. Left/right progress logs.
 */

import { useMemo, useState } from 'react'
import { useLanguage } from '@/state'

export interface WorkflowNode {
  id: string
  name: string
  description: string
  icon: string
  position: { x: number; y: number }
  color: string
  step: number
}

export interface WorkflowEdge {
  id: string
  source: string
  target: string
  step: number
}

export interface InputStep {
  text: string
  duration: number
}

export interface Scenario {
  id: string
  input: string
  inputSteps: InputStep[]
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  leftLogs: string[]
  rightLogs: string[]
}

const STEP_DURATION_MS = 50
const FINAL_STEP_DURATION_MS = 150

function buildTypingSteps(input: string): InputStep[] {
  const chars = Array.from(input)
  if (chars.length === 0) {
    return []
  }
  return chars.map((_, index) => ({
    text: chars.slice(0, index + 1).join(''),
    duration: index === chars.length - 1 ? FINAL_STEP_DURATION_MS : STEP_DURATION_MS
  }))
}

const COMMON_SIMPLE_NODES: WorkflowNode[] = [
  {
    id: 'order-table',
    name: 'Orders',
    description: 'MySQL',
    icon: 'Database',
    position: { x: 50, y: 100 },
    color: 'blue',
    step: 0
  },
  {
    id: 'product-table',
    name: 'Products',
    description: 'MySQL',
    icon: 'Package',
    position: { x: 50, y: 200 },
    color: 'cyan',
    step: 0
  },
  {
    id: 'sync',
    name: 'Data Sync',
    description: 'DataX',
    icon: 'ArrowRightLeft',
    position: { x: 250, y: 150 },
    color: 'purple',
    step: 1
  },
  {
    id: 'warehouse',
    name: 'Warehouse',
    description: 'Hive ODS',
    icon: 'Warehouse',
    position: { x: 450, y: 150 },
    color: 'green',
    step: 2
  }
]

const COMMON_SIMPLE_EDGES: WorkflowEdge[] = [
  { id: 'e1', source: 'order-table', target: 'sync', step: 1 },
  { id: 'e2', source: 'product-table', target: 'sync', step: 1 },
  { id: 'e3', source: 'sync', target: 'warehouse', step: 2 }
]

const COMMON_COMPLEX_NODES: WorkflowNode[] = [
  {
    id: 'order-source',
    name: 'Orders',
    description: 'ODS',
    icon: 'Database',
    position: { x: 50, y: 100 },
    color: 'blue',
    step: 0
  },
  {
    id: 'product-source',
    name: 'Products',
    description: 'ODS',
    icon: 'Package',
    position: { x: 50, y: 200 },
    color: 'cyan',
    step: 0
  },
  {
    id: 'join',
    name: 'JOIN',
    description: 'Spark SQL',
    icon: 'Link',
    position: { x: 220, y: 150 },
    color: 'purple',
    step: 1
  },
  {
    id: 'wide-table',
    name: 'Order Wide',
    description: 'DWD',
    icon: 'Table',
    position: { x: 370, y: 150 },
    color: 'amber',
    step: 2
  },
  {
    id: 'aggregation',
    name: 'Aggregation',
    description: 'DWS',
    icon: 'BarChart3',
    position: { x: 510, y: 150 },
    color: 'orange',
    step: 3
  }
]

const COMMON_COMPLEX_EDGES: WorkflowEdge[] = [
  { id: 'e1', source: 'order-source', target: 'join', step: 1 },
  { id: 'e2', source: 'product-source', target: 'join', step: 1 },
  { id: 'e3', source: 'join', target: 'wide-table', step: 2 },
  { id: 'e4', source: 'wide-table', target: 'aggregation', step: 3 }
]

const SCENARIOS_ZH: Scenario[] = [
  {
    id: 'simple-sync',
    input: '同步订单表和商品表到数仓',
    inputSteps: buildTypingSteps('同步订单表和商品表到数仓'),
    nodes: [
      { ...COMMON_SIMPLE_NODES[0], name: '订单表' },
      { ...COMMON_SIMPLE_NODES[1], name: '商品表' },
      { ...COMMON_SIMPLE_NODES[2], name: '数据同步' },
      { ...COMMON_SIMPLE_NODES[3], name: '数仓' }
    ],
    edges: COMMON_SIMPLE_EDGES,
    leftLogs: [
      '解析需求：同步两张业务表到数仓...',
      '识别数据源：orders（128万行）、products（5.2万行）',
      '选择同步方式：DataX 批量同步',
      '生成任务编排与同步配置...',
      '配置目标表与分区策略...',
      '校验字段映射与主键策略...',
      '工作流编排完成。'
    ],
    rightLogs: [
      '启动任务：sync-to-ods...',
      '同步订单表：orders -> ods.ods_orders',
      '进度：128万 / 128万（100%）',
      '同步商品表：products -> ods.ods_products',
      '进度：5.2万 / 5.2万（100%）',
      '数据一致性校验：通过',
      '更新元数据与血缘...',
      '同步任务执行成功。'
    ]
  },
  {
    id: 'complex-join',
    input: '关联订单表和商品表，生成订单宽表并汇总销售',
    inputSteps: buildTypingSteps('关联订单表和商品表，生成订单宽表并汇总销售'),
    nodes: [
      { ...COMMON_COMPLEX_NODES[0], name: '订单表' },
      { ...COMMON_COMPLEX_NODES[1], name: '商品表' },
      { ...COMMON_COMPLEX_NODES[2], name: 'JOIN 关联' },
      { ...COMMON_COMPLEX_NODES[3], name: '订单宽表' },
      { ...COMMON_COMPLEX_NODES[4], name: '销售汇总' }
    ],
    edges: COMMON_COMPLEX_EDGES,
    leftLogs: [
      '解析需求：构建订单宽表并汇总销售指标...',
      '设计 JOIN 逻辑：orders LEFT JOIN products ON product_id',
      '定义宽表字段：订单信息 + 商品信息（35字段）',
      '设计汇总指标：按日期、类目统计销售额',
      '选择计算引擎：Spark SQL',
      '生成 SQL 脚本与调度配置...',
      ' > DWD：订单宽表',
      ' > DWS：销售汇总表',
      '工作流编排完成。'
    ],
    rightLogs: [
      '启动 Spark 任务：build-order-wide...',
      '读取 ODS 数据：orders（128万）、products（5.2万）',
      '执行 LEFT JOIN 关联...',
      '写入宽表：dwd.dwd_order_wide（128万行）',
      '启动汇总计算...',
      ' > 按日期维度：365 条',
      ' > 按类目维度：128 条',
      '写入汇总：dws.dws_sales_summary',
      '任务执行成功，耗时 3 分 28 秒。'
    ]
  }
]

const SCENARIOS_EN: Scenario[] = [
  {
    id: 'simple-sync',
    input: 'Sync order and product tables to warehouse',
    inputSteps: buildTypingSteps('Sync order and product tables to warehouse'),
    nodes: COMMON_SIMPLE_NODES,
    edges: COMMON_SIMPLE_EDGES,
    leftLogs: [
      'Analyzing requirement: sync two tables to warehouse...',
      'Identifying sources: orders (1.28M rows), products (52K rows)',
      'Selecting sync tool: DataX batch sync',
      'Generating workflow and sync config...',
      'Configuring target table and partition strategy...',
      'Validating field mappings and PK strategy...',
      'Workflow orchestration completed.'
    ],
    rightLogs: [
      'Starting DataX task: sync-to-ods...',
      'Syncing orders: orders -> ods.ods_orders',
      'Progress: 1.28M / 1.28M (100%)',
      'Syncing products: products -> ods.ods_products',
      'Progress: 52K / 52K (100%)',
      'Data integrity validation: passed',
      'Updating metadata and lineage...',
      'Sync task completed successfully.'
    ]
  },
  {
    id: 'complex-join',
    input: 'Join orders and products into wide table, aggregate sales',
    inputSteps: buildTypingSteps('Join orders and products into wide table, aggregate sales'),
    nodes: COMMON_COMPLEX_NODES,
    edges: COMMON_COMPLEX_EDGES,
    leftLogs: [
      'Analyzing requirement: build order wide table + sales aggregation...',
      'Designing JOIN logic: orders LEFT JOIN products ON product_id',
      'Defining wide table schema: order + product details (35 fields)',
      'Designing aggregation metrics: sales by date and category',
      'Selecting compute engine: Spark SQL',
      'Generating SQL scripts and scheduling config...',
      ' > DWD layer: order wide table',
      ' > DWS layer: sales summary',
      'Workflow orchestration completed.'
    ],
    rightLogs: [
      'Starting Spark task: build-order-wide...',
      'Reading ODS data: orders (1.28M), products (52K)',
      'Executing LEFT JOIN...',
      'Writing wide table: dwd.dwd_order_wide (1.28M rows)',
      'Starting aggregation...',
      ' > By date dimension: 365 records',
      ' > By category dimension: 128 records',
      'Writing summary: dws.dws_sales_summary',
      'Task completed successfully, elapsed 3m28s.'
    ]
  }
]

export function useScenario(): { scenario: Scenario; nextScenario: () => void } {
  const language = useLanguage()
  const [languageIndexMap, setLanguageIndexMap] = useState<Record<string, number>>({})

  const scenarios = useMemo(
    () => (language === 'zh-CN' ? SCENARIOS_ZH : SCENARIOS_EN),
    [language]
  )

  const currentIndex = languageIndexMap[language] ?? 0

  const nextScenario = () => {
    setLanguageIndexMap((prev) => ({
      ...prev,
      [language]: (currentIndex + 1) % scenarios.length
    }))
  }

  const scenario = useMemo(() => scenarios[currentIndex] ?? scenarios[0], [scenarios, currentIndex])

  return { scenario, nextScenario }
}
