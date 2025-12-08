/**
 * 图标配置 - react-icons/si 大数据技术栈图标
 *
 * 使用 Simple Icons 提供的真实技术栈 Logo
 * 专业、统一、品牌识别度高
 */

import React from 'react'
import {
  // ==================== 大数据框架 ====================
  SiApachespark,
  SiApacheflink,
  SiApachehive,
  SiApachekafka,
  SiApachehadoop,
  SiApacheairflow,
  SiApachehbase,

  // ==================== 数据库 ====================
  SiMysql,
  SiPostgresql,
  SiMongodb,
  SiRedis,
  SiClickhouse,
  SiMariadb,
  SiSqlite,

  // ==================== 数据湖/仓库 ====================
  SiDatabricks,
  SiSnowflake,
  SiApachecassandra,

  // ==================== BI/可视化 ====================
  SiTableau,
  SiGrafana,
  SiApachesuperset,

  // ==================== 容器/编排 ====================
  SiDocker,
  SiKubernetes,

  // ==================== 编程语言 ====================
  SiPython,
  SiOpenjdk,
  SiScala,
  SiGo,
  SiJavascript,
  SiTypescript,

  // ==================== 云平台 ====================
  SiAmazon,
  SiGooglecloud,
  SiAlibabacloud,

  // ==================== 通用图标（从 lucide-react 导入）====================
} from 'react-icons/si'

import {
  Database,
  BarChart3,
  FileText,
  GitBranch,
  CheckCircle2,
  FolderOpen,
  Users,
  Shield,
  Settings,
  Layers,
  Workflow,
  CalendarClock,
  BrainCircuit,
  Cloud,
  LineChart,
  Share2,
} from 'lucide-react'

// ==================== 图标尺寸配置 ====================

const ICON_SIZES = {
  sm: 14,
  md: 16,
  lg: 18,
  xl: 20,
} as const

// ==================== 菜单图标映射 ====================

/**
 * 菜单图标映射表
 * key: 菜单 ID
 * value: 图标组件
 */
export const menuIconMap = {
  // ==================== 资产管理 ====================
  'assets': <Layers size={ICON_SIZES.lg} />,
  'assets.datasets': <Database size={ICON_SIZES.md} />,
  'assets.models': <BrainCircuit size={ICON_SIZES.md} />,
  'assets.pipelines': <GitBranch size={ICON_SIZES.md} />,
  'assets.reports': <BarChart3 size={ICON_SIZES.md} />,
  'assets.dashboards': <BarChart3 size={ICON_SIZES.md} />,

  // ==================== 项目管理 ====================
  'projects': <FolderOpen size={ICON_SIZES.lg} />,
  'projects.my': <FolderOpen size={ICON_SIZES.md} />,
  'projects.collaboration': <Users size={ICON_SIZES.md} />,
  'projects.templates': <FileText size={ICON_SIZES.md} />,
  'projects.workflow': <Workflow size={ICON_SIZES.md} />,
  'projects.tasks': <CalendarClock size={ICON_SIZES.md} />,

  // ==================== 数据治理 ====================
  'governance': <Shield size={ICON_SIZES.lg} />,
  'governance.metadata': <Database size={ICON_SIZES.md} />,
  'governance.quality': <CheckCircle2 size={ICON_SIZES.md} />,
  'governance.catalog': <Database size={ICON_SIZES.md} />,
  'governance.lineage': <GitBranch size={ICON_SIZES.md} />,
  'governance.knowledge': <Share2 size={ICON_SIZES.md} />,
  'governance.dictionary': <FileText size={ICON_SIZES.md} />,

  // ==================== AI 相关 ====================
  'ai': <BrainCircuit size={ICON_SIZES.lg} />,
  'ai.models': <BrainCircuit size={ICON_SIZES.md} />,
  'ai.training': <Settings size={ICON_SIZES.md} />,
  'ai.inference': <GitBranch size={ICON_SIZES.md} />,

  // ==================== 系统管理 ====================
  'system': <Settings size={ICON_SIZES.lg} />,
  'system.users': <Users size={ICON_SIZES.md} />,
  'system.roles': <Shield size={ICON_SIZES.md} />,
  'system.permissions': <Shield size={ICON_SIZES.md} />,
  'system.ai-models': <BrainCircuit size={ICON_SIZES.md} />,
  'system.config': <Settings size={ICON_SIZES.md} />,
  'system.audit': <FileText size={ICON_SIZES.md} />,
} as const

// ==================== 大数据技术栈图标映射 ====================

/**
 * 大数据技术栈图标映射
 * 用于工作流组件、数据源配置等场景
 */
export const techStackIconMap = {
  // 计算引擎
  spark: <SiApachespark size={ICON_SIZES.lg} color="#E25A1C" />,
  flink: <SiApacheflink size={ICON_SIZES.lg} color="#E6526F" />,
  hadoop: <SiApachehadoop size={ICON_SIZES.lg} color="#FDDB3A" />,

  // 数据仓库
  hive: <SiApachehive size={ICON_SIZES.lg} color="#FDDB3A" />,
  hbase: <SiApachehbase size={ICON_SIZES.lg} color="#0095D5" />,

  // 消息队列
  kafka: <SiApachekafka size={ICON_SIZES.lg} color="#231F20" />,

  // 调度
  airflow: <SiApacheairflow size={ICON_SIZES.lg} color="#017CEE" />,

  // 数据库
  mysql: <SiMysql size={ICON_SIZES.lg} color="#4479A1" />,
  postgresql: <SiPostgresql size={ICON_SIZES.lg} color="#4169E1" />,
  mongodb: <SiMongodb size={ICON_SIZES.lg} color="#47A248" />,
  redis: <SiRedis size={ICON_SIZES.lg} color="#DC382D" />,
  clickhouse: <SiClickhouse size={ICON_SIZES.lg} color="#FFCC01" />,
  mariadb: <SiMariadb size={ICON_SIZES.lg} color="#003545" />,
  sqlite: <SiSqlite size={ICON_SIZES.lg} color="#003B57" />,
  cassandra: <SiApachecassandra size={ICON_SIZES.lg} color="#1287B1" />,

  // 数据湖/仓库
  databricks: <SiDatabricks size={ICON_SIZES.lg} color="#FF3621" />,
  snowflake: <SiSnowflake size={ICON_SIZES.lg} color="#29B5E8" />,

  // BI/可视化
  tableau: <SiTableau size={ICON_SIZES.lg} color="#E97627" />,
  powerbi: <LineChart size={ICON_SIZES.lg} color="#F2C811" />,
  grafana: <SiGrafana size={ICON_SIZES.lg} color="#F46800" />,
  superset: <SiApachesuperset size={ICON_SIZES.lg} color="#20A6C9" />,

  // 容器
  docker: <SiDocker size={ICON_SIZES.lg} color="#2496ED" />,
  kubernetes: <SiKubernetes size={ICON_SIZES.lg} color="#326CE5" />,

  // 编程语言
  python: <SiPython size={ICON_SIZES.lg} color="#3776AB" />,
  java: <SiOpenjdk size={ICON_SIZES.lg} color="#007396" />,
  scala: <SiScala size={ICON_SIZES.lg} color="#DC322F" />,
  go: <SiGo size={ICON_SIZES.lg} color="#00ADD8" />,
  javascript: <SiJavascript size={ICON_SIZES.lg} color="#F7DF1E" />,
  typescript: <SiTypescript size={ICON_SIZES.lg} color="#3178C6" />,

  // 云平台
  aws: <SiAmazon size={ICON_SIZES.lg} color="#232F3E" />,
  azure: <Cloud size={ICON_SIZES.lg} color="#0078D4" />,
  gcp: <SiGooglecloud size={ICON_SIZES.lg} color="#4285F4" />,
  aliyun: <SiAlibabacloud size={ICON_SIZES.lg} color="#FF6A00" />,
} as const

// ==================== 类型导出 ====================

export type MenuIconKey = keyof typeof menuIconMap
export type TechStackIconKey = keyof typeof techStackIconMap

// ==================== 辅助函数 ====================

/**
 * 获取菜单图标
 */
export const getMenuIcon = (key: string): React.ReactNode => {
  return menuIconMap[key as MenuIconKey] || <Layers size={ICON_SIZES.md} />
}

/**
 * 获取技术栈图标
 */
export const getTechStackIcon = (key: string): React.ReactNode => {
  return techStackIconMap[key as TechStackIconKey] || <Database size={ICON_SIZES.lg} />
}
