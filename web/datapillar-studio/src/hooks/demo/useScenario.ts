/**
 * 场景切换 Hook
 *
 * 功能：
 * 1. 循环切换不同的业务场景
 * 2. 每个场景包含：输入文本、工作流配置、日志数据
 */

import { useState, useMemo } from 'react'
import { useLanguage } from '@/state'

/**
 * 工作流节点配置
 */
export interface WorkflowNode {
  id: string
  name: string
  description: string
  icon: string
  position: { x: number; y: number }
  color: string
  step: number
}

/**
 * 工作流连接配置
 */
export interface WorkflowEdge {
  id: string
  source: string
  target: string
  step: number
}

/**
 * 输入步骤（模拟真实用户输入）
 */
export interface InputStep {
  text: string      // 当前显示的文本
  duration: number  // 这一步持续时间（ms）
}

/**
 * 场景配置
 */
export interface Scenario {
  id: string
  input: string
  inputSteps: InputStep[]  // 输入步骤（包括打错、删除）
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  leftLogs: string[]
  rightLogs: string[]
}

/**
 * 中文场景数据
 */
const SCENARIOS_ZH: Scenario[] = [
  {
    id: 'simple-sync',
    input: '同步订单表和商品表到数仓',
    inputSteps: [
      { text: '同', duration: 50 },
      { text: '同步', duration: 50 },
      { text: '同步库', duration: 50 },
      { text: '同步库存', duration: 50 },
      { text: '同步库存表', duration: 50 },
      { text: '同步库存表和', duration: 50 },
      { text: '同步库存表和商', duration: 50 },
      { text: '同步库存表和商品', duration: 50 },
      { text: '同步库存表和商品表', duration: 50 },
      { text: '同步库存表和商品表到', duration: 50 },
      { text: '同步库存表和商品表到数', duration: 50 },
      { text: '同步库存表和商品表到数据', duration: 50 },
      { text: '同步库存表和商品表到数据库', duration: 250 }, // 停顿，发现打错了
      { text: '同步库存表和商品表到数据', duration: 80 }, // 回退尾部
      { text: '同步库存表和商品表到数', duration: 80 },
      { text: '同步库存表和商品表到数仓', duration: 80 },
      { text: '同步库表和商品表到数仓', duration: 80 }, // 删除“存”
      { text: '同步表和商品表到数仓', duration: 80 }, // 删除“库”
      { text: '同步订表和商品表到数仓', duration: 60 }, // 重新输入订单
      { text: '同步订单表和商品表到数仓', duration: 150 } // 完整正确文本
    ],
    nodes: [
      { id: 'order-table', name: '订单表', description: 'MySQL', icon: 'Database', position: { x: 50, y: 100 }, color: 'blue', step: 0 },
      { id: 'product-table', name: '商品表', description: 'MySQL', icon: 'Package', position: { x: 50, y: 200 }, color: 'cyan', step: 0 },
      { id: 'sync', name: '数据同步', description: 'DataX', icon: 'ArrowRightLeft', position: { x: 250, y: 150 }, color: 'purple', step: 1 },
      { id: 'warehouse', name: '数据仓库', description: 'Hive ODS', icon: 'Warehouse', position: { x: 450, y: 150 }, color: 'green', step: 2 }
    ],
    edges: [
      { id: 'e1', source: 'order-table', target: 'sync', step: 1 },
      { id: 'e2', source: 'product-table', target: 'sync', step: 1 },
      { id: 'e3', source: 'sync', target: 'warehouse', step: 2 }
    ],
    leftLogs: [
      "解析需求: 同步两张表到数仓...",
      "识别源表: orders (128万行), products (5.2万行)",
      "选择同步工具: DataX 批量同步",
      "设计同步策略: 全量同步",
      "生成 DataX 配置文件...",
      "配置目标表: ODS层, 分区按日期",
      "工作流编排完成。"
    ],
    rightLogs: [
      "启动 DataX 任务: sync-to-ods...",
      "同步订单表: orders -> ods.ods_orders",
      "进度: 128万 / 128万 (100%)",
      "同步商品表: products -> ods.ods_products",
      "进度: 5.2万 / 5.2万 (100%)",
      "验证数据完整性: 通过",
      "更新元数据信息...",
      "同步任务执行成功。"
    ]
  },
  {
    id: 'complex-join',
    input: '关联订单表和商品表输出到订单宽表，汇总统计销售额',
    inputSteps: [
      { text: '关', duration: 50 },
      { text: '关联', duration: 50 },
      { text: '关联客', duration: 50 },
      { text: '关联客户', duration: 50 },
      { text: '关联客户表', duration: 50 },
      { text: '关联客户表和', duration: 50 },
      { text: '关联客户表和商', duration: 50 },
      { text: '关联客户表和商品', duration: 50 },
      { text: '关联客户表和商品表', duration: 50 },
      { text: '关联客户表和商品表输', duration: 50 },
      { text: '关联客户表和商品表输出', duration: 50 },
      { text: '关联客户表和商品表输出到订', duration: 50 },
      { text: '关联客户表和商品表输出到订单', duration: 50 },
      { text: '关联客户表和商品表输出到订单表', duration: 220 }, // 停顿
      { text: '关联客户表和商品表输出到订单', duration: 80 },
      { text: '关联客表和商品表输出到订单', duration: 80 },
      { text: '关联表和商品表输出到订单', duration: 80 },
      { text: '关联订表和商品表输出到订单', duration: 60 },
      { text: '关联订单表和商品表输出到订单', duration: 60 },
      { text: '关联订单表和商品表输出到订单宽', duration: 60 },
      { text: '关联订单表和商品表输出到订单宽表，', duration: 60 },
      { text: '关联订单表和商品表输出到订单宽表，汇总统计销售额', duration: 150 }
    ],
    nodes: [
      { id: 'order-source', name: '订单表', description: 'ODS', icon: 'Database', position: { x: 50, y: 100 }, color: 'blue', step: 0 },
      { id: 'product-source', name: '商品表', description: 'ODS', icon: 'Package', position: { x: 50, y: 200 }, color: 'cyan', step: 0 },
      { id: 'join', name: 'JOIN 关联', description: 'Spark SQL', icon: 'Link', position: { x: 220, y: 150 }, color: 'purple', step: 1 },
      { id: 'wide-table', name: '订单宽表', description: 'DWD', icon: 'Table', position: { x: 370, y: 150 }, color: 'amber', step: 2 },
      { id: 'aggregation', name: '汇总统计', description: 'DWS', icon: 'BarChart3', position: { x: 510, y: 150 }, color: 'orange', step: 3 }
    ],
    edges: [
      { id: 'e1', source: 'order-source', target: 'join', step: 1 },
      { id: 'e2', source: 'product-source', target: 'join', step: 1 },
      { id: 'e3', source: 'join', target: 'wide-table', step: 2 },
      { id: 'e4', source: 'wide-table', target: 'aggregation', step: 3 }
    ],
    leftLogs: [
      "分析需求: 构建订单宽表 + 销售汇总...",
      "设计 JOIN 逻辑: orders LEFT JOIN products ON product_id",
      "宽表字段设计: 订单信息 + 商品详情 (35个字段)",
      "汇总指标设计: 按日期、类目统计销售额",
      "选择计算引擎: Spark SQL",
      "生成 SQL 脚本...",
      "   > DWD层: 订单宽表",
      "   > DWS层: 销售汇总表",
      "工作流编排完成。"
    ],
    rightLogs: [
      "启动 Spark 任务: build-order-wide...",
      "读取 ODS 层数据: orders (128万), products (5.2万)",
      "执行 LEFT JOIN 关联...",
      "写入宽表: dwd.dwd_order_wide (128万行)",
      "开始汇总计算...",
      "   > 按日期维度: 365条记录",
      "   > 按类目维度: 128条记录",
      "写入汇总表: dws.dws_sales_summary",
      "任务执行成功，耗时 3分28秒。"
    ]
  }
]

/**
 * 英文场景数据
 */
const SCENARIOS_EN: Scenario[] = [
  {
    id: 'simple-sync',
    input: 'Sync order and product tables to warehouse',
    inputSteps: [
      { text: 'S', duration: 50 },
      { text: 'Sy', duration: 50 },
      { text: 'Syn', duration: 50 },
      { text: 'Sync', duration: 50 },
      { text: 'Sync ', duration: 50 },
      { text: 'Sync i', duration: 50 },
      { text: 'Sync in', duration: 50 },
      { text: 'Sync inv', duration: 50 },
      { text: 'Sync inve', duration: 50 },
      { text: 'Sync inven', duration: 50 },
      { text: 'Sync invent', duration: 50 },
      { text: 'Sync invento', duration: 50 },
      { text: 'Sync inventor', duration: 50 },
      { text: 'Sync inventory', duration: 50 },
      { text: 'Sync inventory ', duration: 50 },
      { text: 'Sync inventory a', duration: 50 },
      { text: 'Sync inventory an', duration: 50 },
      { text: 'Sync inventory and', duration: 50 },
      { text: 'Sync inventory and ', duration: 50 },
      { text: 'Sync inventory and p', duration: 50 },
      { text: 'Sync inventory and pr', duration: 50 },
      { text: 'Sync inventory and pro', duration: 50 },
      { text: 'Sync inventory and prod', duration: 50 },
      { text: 'Sync inventory and produ', duration: 50 },
      { text: 'Sync inventory and produc', duration: 50 },
      { text: 'Sync inventory and product', duration: 50 },
      { text: 'Sync inventory and product ', duration: 50 },
      { text: 'Sync inventory and product t', duration: 50 },
      { text: 'Sync inventory and product ta', duration: 50 },
      { text: 'Sync inventory and product tab', duration: 50 },
      { text: 'Sync inventory and product tabl', duration: 50 },
      { text: 'Sync inventory and product table', duration: 50 },
      { text: 'Sync inventory and product tables', duration: 50 },
      { text: 'Sync inventory and product tables ', duration: 50 },
      { text: 'Sync inventory and product tables t', duration: 50 },
      { text: 'Sync inventory and product tables to', duration: 50 },
      { text: 'Sync inventory and product tables to ', duration: 50 },
      { text: 'Sync inventory and product tables to w', duration: 50 },
      { text: 'Sync inventory and product tables to wa', duration: 50 },
      { text: 'Sync inventory and product tables to war', duration: 50 },
      { text: 'Sync inventory and product tables to ware', duration: 50 },
      { text: 'Sync inventory and product tables to wareh', duration: 50 },
      { text: 'Sync inventory and product tables to wareho', duration: 50 },
      { text: 'Sync inventory and product tables to warehou', duration: 50 },
      { text: 'Sync inventory and product tables to warehous', duration: 50 },
      { text: 'Sync inventory and product tables to warehouse', duration: 250 },
      { text: 'Sync nventory and product tables to warehouse', duration: 80 },
      { text: 'Sync ventory and product tables to warehouse', duration: 80 },
      { text: 'Sync entory and product tables to warehouse', duration: 80 },
      { text: 'Sync ntory and product tables to warehouse', duration: 80 },
      { text: 'Sync tory and product tables to warehouse', duration: 80 },
      { text: 'Sync ory and product tables to warehouse', duration: 80 },
      { text: 'Sync ry and product tables to warehouse', duration: 80 },
      { text: 'Sync y and product tables to warehouse', duration: 80 },
      { text: 'Sync  and product tables to warehouse', duration: 80 },
      { text: 'Sync o and product tables to warehouse', duration: 60 },
      { text: 'Sync or and product tables to warehouse', duration: 60 },
      { text: 'Sync ord and product tables to warehouse', duration: 60 },
      { text: 'Sync orde and product tables to warehouse', duration: 60 },
      { text: 'Sync order and product tables to warehouse', duration: 150 }
    ],
    nodes: [
      { id: 'order-table', name: 'Orders', description: 'MySQL', icon: 'Database', position: { x: 50, y: 100 }, color: 'blue', step: 0 },
      { id: 'product-table', name: 'Products', description: 'MySQL', icon: 'Package', position: { x: 50, y: 200 }, color: 'cyan', step: 0 },
      { id: 'sync', name: 'Data Sync', description: 'DataX', icon: 'ArrowRightLeft', position: { x: 250, y: 150 }, color: 'purple', step: 1 },
      { id: 'warehouse', name: 'Warehouse', description: 'Hive ODS', icon: 'Warehouse', position: { x: 450, y: 150 }, color: 'green', step: 2 }
    ],
    edges: [
      { id: 'e1', source: 'order-table', target: 'sync', step: 1 },
      { id: 'e2', source: 'product-table', target: 'sync', step: 1 },
      { id: 'e3', source: 'sync', target: 'warehouse', step: 2 }
    ],
    leftLogs: [
      "Analyzing requirement: sync two tables to warehouse...",
      "Identifying sources: orders (1.28M rows), products (52K rows)",
      "Selecting sync tool: DataX batch sync",
      "Designing sync strategy: full load",
      "Generating DataX config file...",
      "Configuring target: ODS layer, partitioned by date",
      "Workflow orchestration completed."
    ],
    rightLogs: [
      "Starting DataX task: sync-to-ods...",
      "Syncing orders: orders -> ods.ods_orders",
      "Progress: 1.28M / 1.28M (100%)",
      "Syncing products: products -> ods.ods_products",
      "Progress: 52K / 52K (100%)",
      "Validating data integrity: passed",
      "Updating metadata...",
      "Sync task completed successfully."
    ]
  },
  {
    id: 'complex-join',
    input: 'Join orders and products into wide table, aggregate sales',
    inputSteps: [
      { text: 'J', duration: 50 },
      { text: 'Jo', duration: 50 },
      { text: 'Joi', duration: 50 },
      { text: 'Join', duration: 50 },
      { text: 'Join ', duration: 50 },
      { text: 'Join o', duration: 50 },
      { text: 'Join or', duration: 50 },
      { text: 'Join ord', duration: 50 },
      { text: 'Join orde', duration: 50 },
      { text: 'Join order', duration: 50 },
      { text: 'Join orders', duration: 50 },
      { text: 'Join orders ', duration: 50 },
      { text: 'Join orders a', duration: 50 },
      { text: 'Join orders an', duration: 50 },
      { text: 'Join orders and', duration: 50 },
      { text: 'Join orders and ', duration: 50 },
      { text: 'Join orders and p', duration: 50 },
      { text: 'Join orders and pr', duration: 50 },
      { text: 'Join orders and pro', duration: 50 },
      { text: 'Join orders and prod', duration: 50 },
      { text: 'Join orders and produ', duration: 50 },
      { text: 'Join orders and produc', duration: 50 },
      { text: 'Join orders and product', duration: 50 },
      { text: 'Join orders and products', duration: 50 },
      { text: 'Join orders and products ', duration: 50 },
      { text: 'Join orders and products i', duration: 50 },
      { text: 'Join orders and products in', duration: 50 },
      { text: 'Join orders and products int', duration: 50 },
      { text: 'Join orders and products into', duration: 50 },
      { text: 'Join orders and products into ', duration: 50 },
      { text: 'Join orders and products into w', duration: 50 },
      { text: 'Join orders and products into wi', duration: 50 },
      { text: 'Join orders and products into wid', duration: 50 },
      { text: 'Join orders and products into wide', duration: 50 },
      { text: 'Join orders and products into wide ', duration: 50 },
      { text: 'Join orders and products into wide t', duration: 50 },
      { text: 'Join orders and products into wide ta', duration: 50 },
      { text: 'Join orders and products into wide tab', duration: 50 },
      { text: 'Join orders and products into wide tabl', duration: 50 },
      { text: 'Join orders and products into wide table', duration: 50 },
      { text: 'Join orders and products into wide table,', duration: 50 },
      { text: 'Join orders and products into wide table, ', duration: 50 },
      { text: 'Join orders and products into wide table, a', duration: 50 },
      { text: 'Join orders and products into wide table, ag', duration: 50 },
      { text: 'Join orders and products into wide table, agg', duration: 50 },
      { text: 'Join orders and products into wide table, aggr', duration: 50 },
      { text: 'Join orders and products into wide table, aggre', duration: 50 },
      { text: 'Join orders and products into wide table, aggreg', duration: 50 },
      { text: 'Join orders and products into wide table, aggrega', duration: 50 },
      { text: 'Join orders and products into wide table, aggregat', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate ', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate s', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate sa', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate sal', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate sale', duration: 50 },
      { text: 'Join orders and products into wide table, aggregate sales', duration: 150 }
    ],
    nodes: [
      { id: 'order-source', name: 'Orders', description: 'ODS', icon: 'Database', position: { x: 50, y: 100 }, color: 'blue', step: 0 },
      { id: 'product-source', name: 'Products', description: 'ODS', icon: 'Package', position: { x: 50, y: 200 }, color: 'cyan', step: 0 },
      { id: 'join', name: 'JOIN', description: 'Spark SQL', icon: 'Link', position: { x: 220, y: 150 }, color: 'purple', step: 1 },
      { id: 'wide-table', name: 'Order Wide', description: 'DWD', icon: 'Table', position: { x: 370, y: 150 }, color: 'amber', step: 2 },
      { id: 'aggregation', name: 'Aggregation', description: 'DWS', icon: 'BarChart3', position: { x: 510, y: 150 }, color: 'orange', step: 3 }
    ],
    edges: [
      { id: 'e1', source: 'order-source', target: 'join', step: 1 },
      { id: 'e2', source: 'product-source', target: 'join', step: 1 },
      { id: 'e3', source: 'join', target: 'wide-table', step: 2 },
      { id: 'e4', source: 'wide-table', target: 'aggregation', step: 3 }
    ],
    leftLogs: [
      "Analyzing requirement: build order wide table + sales aggregation...",
      "Designing JOIN logic: orders LEFT JOIN products ON product_id",
      "Wide table schema: order info + product details (35 fields)",
      "Aggregation metrics: sales by date, category",
      "Selecting compute engine: Spark SQL",
      "Generating SQL scripts...",
      "   > DWD layer: order wide table",
      "   > DWS layer: sales summary",
      "Workflow orchestration completed."
    ],
    rightLogs: [
      "Starting Spark task: build-order-wide...",
      "Reading ODS data: orders (1.28M), products (52K)",
      "Executing LEFT JOIN...",
      "Writing wide table: dwd.dwd_order_wide (1.28M rows)",
      "Starting aggregation...",
      "   > By date dimension: 365 records",
      "   > By category dimension: 128 records",
      "Writing summary: dws.dws_sales_summary",
      "Task completed successfully, elapsed 3m28s."
    ]
  }
]

/**
 * 场景切换 Hook
 * @returns 当前场景配置和切换函数
 */
export function useScenario(): { scenario: Scenario; nextScenario: () => void } {
  const language = useLanguage()
  const [currentIndex, setCurrentIndex] = useState(0)

  // 根据语言选择场景数据，使用 useMemo 避免重复创建
  const SCENARIOS = useMemo(() => {
    return language === 'zh-CN' ? SCENARIOS_ZH : SCENARIOS_EN
  }, [language])

  // 当语言切换时重置场景索引
  const [prevLanguage, setPrevLanguage] = useState(language)
  if (prevLanguage !== language) {
    setPrevLanguage(language)
    setCurrentIndex(0)
  }

  const nextScenario = () => {
    setCurrentIndex((prev) => (prev + 1) % SCENARIOS.length)
  }

  // 使用 useMemo 缓存当前场景对象，避免不必要的重新渲染
  const scenario = useMemo(() => SCENARIOS[currentIndex], [SCENARIOS, currentIndex])

  return {
    scenario,
    nextScenario
  }
}
