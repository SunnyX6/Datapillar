/**
 * Icon configuration - react-icons/si Big data technology stack icon
 *
 * use Simple Icons Real technology stack provided Logo
 * Professional,unify,High brand recognition
 */

import React from 'react'
import {
 // ==================== big data framework ====================
 SiApachespark,SiApacheflink,SiApachehive,SiApachekafka,SiApachehadoop,SiApacheairflow,SiApachehbase,// ==================== database ====================
 SiMysql,SiPostgresql,SiMongodb,SiRedis,SiClickhouse,SiMariadb,SiSqlite,// ==================== data lake/warehouse ====================
 SiDatabricks,SiSnowflake,SiApachecassandra,// ==================== BI/Visualization ====================
 SiTableau,SiGrafana,SiApachesuperset,// ==================== container/Arrange ====================
 SiDocker,SiKubernetes,// ==================== programming language ====================
 SiPython,SiOpenjdk,SiScala,SiGo,SiJavascript,SiTypescript,// ==================== Cloud platform ====================
 SiAmazon,SiGooglecloud,SiAlibabacloud,// ==================== Universal icon(from lucide-react import)====================
} from 'react-icons/si'

import {
 Database,BarChart3,FileText,GitBranch,CheckCircle2,FolderOpen,Users,Shield,Settings,Layers,Workflow,CalendarClock,BrainCircuit,Cloud,LineChart,Share2,} from 'lucide-react'

// ==================== Icon size configuration ====================

const ICON_SIZES = {
 sm:14,md:16,lg:18,xl:20,} as const

// ==================== Menu icon mapping ====================

/**
 * Menu icon mapping table
 * key:menu ID
 * value:icon component
 */
export const menuIconMap = {
 // ==================== Asset management ====================
 'assets':<Layers size={ICON_SIZES.lg} />,'assets.datasets':<Database size={ICON_SIZES.md} />,'assets.models':<BrainCircuit size={ICON_SIZES.md} />,'assets.pipelines':<GitBranch size={ICON_SIZES.md} />,'assets.reports':<BarChart3 size={ICON_SIZES.md} />,'assets.dashboards':<BarChart3 size={ICON_SIZES.md} />,// ==================== project management ====================
 'projects':<FolderOpen size={ICON_SIZES.lg} />,'projects.my':<FolderOpen size={ICON_SIZES.md} />,'projects.collaboration':<Users size={ICON_SIZES.md} />,'projects.templates':<FileText size={ICON_SIZES.md} />,'projects.workflow':<Workflow size={ICON_SIZES.md} />,'projects.tasks':<CalendarClock size={ICON_SIZES.md} />,// ==================== data governance ====================
 'governance':<Shield size={ICON_SIZES.lg} />,'governance.metadata':<Database size={ICON_SIZES.md} />,'governance.quality':<CheckCircle2 size={ICON_SIZES.md} />,'governance.catalog':<Database size={ICON_SIZES.md} />,'governance.lineage':<GitBranch size={ICON_SIZES.md} />,'governance.knowledge':<Share2 size={ICON_SIZES.md} />,'governance.dictionary':<FileText size={ICON_SIZES.md} />,// ==================== AI Related ====================
 'ai':<BrainCircuit size={ICON_SIZES.lg} />,'ai.models':<BrainCircuit size={ICON_SIZES.md} />,'ai.training':<Settings size={ICON_SIZES.md} />,'ai.inference':<GitBranch size={ICON_SIZES.md} />,// ==================== System management ====================
 'system':<Settings size={ICON_SIZES.lg} />,'system.users':<Users size={ICON_SIZES.md} />,'system.roles':<Shield size={ICON_SIZES.md} />,'system.permissions':<Shield size={ICON_SIZES.md} />,'system.ai-models':<BrainCircuit size={ICON_SIZES.md} />,'system.config':<Settings size={ICON_SIZES.md} />,'system.audit':<FileText size={ICON_SIZES.md} />,} as const

// ==================== Big data technology stack icon map ====================

/**
 * Big data technology stack icon map
 * for workflow components,Data source configuration and other scenarios
 */
export const techStackIconMap = {
 // Compute engine
 spark:<SiApachespark size={ICON_SIZES.lg} color="#E25A1C" />,flink:<SiApacheflink size={ICON_SIZES.lg} color="#E6526F" />,hadoop:<SiApachehadoop size={ICON_SIZES.lg} color="#FDDB3A" />,// data warehouse
 hive:<SiApachehive size={ICON_SIZES.lg} color="#FDDB3A" />,hbase:<SiApachehbase size={ICON_SIZES.lg} color="#0095D5" />,// message queue
 kafka:<SiApachekafka size={ICON_SIZES.lg} color="#231F20" />,// Scheduling
 airflow:<SiApacheairflow size={ICON_SIZES.lg} color="#017CEE" />,// database
 mysql:<SiMysql size={ICON_SIZES.lg} color="#4479A1" />,postgresql:<SiPostgresql size={ICON_SIZES.lg} color="#4169E1" />,mongodb:<SiMongodb size={ICON_SIZES.lg} color="#47A248" />,redis:<SiRedis size={ICON_SIZES.lg} color="#DC382D" />,clickhouse:<SiClickhouse size={ICON_SIZES.lg} color="#FFCC01" />,mariadb:<SiMariadb size={ICON_SIZES.lg} color="#003545" />,sqlite:<SiSqlite size={ICON_SIZES.lg} color="#003B57" />,cassandra:<SiApachecassandra size={ICON_SIZES.lg} color="#1287B1" />,// data lake/warehouse
 databricks:<SiDatabricks size={ICON_SIZES.lg} color="#FF3621" />,snowflake:<SiSnowflake size={ICON_SIZES.lg} color="#29B5E8" />,// BI/Visualization
 tableau:<SiTableau size={ICON_SIZES.lg} color="#E97627" />,powerbi:<LineChart size={ICON_SIZES.lg} color="#F2C811" />,grafana:<SiGrafana size={ICON_SIZES.lg} color="#F46800" />,superset:<SiApachesuperset size={ICON_SIZES.lg} color="#20A6C9" />,// container
 docker:<SiDocker size={ICON_SIZES.lg} color="#2496ED" />,kubernetes:<SiKubernetes size={ICON_SIZES.lg} color="#326CE5" />,// programming language
 python:<SiPython size={ICON_SIZES.lg} color="#3776AB" />,java:<SiOpenjdk size={ICON_SIZES.lg} color="#007396" />,scala:<SiScala size={ICON_SIZES.lg} color="#DC322F" />,go:<SiGo size={ICON_SIZES.lg} color="#00ADD8" />,javascript:<SiJavascript size={ICON_SIZES.lg} color="#F7DF1E" />,typescript:<SiTypescript size={ICON_SIZES.lg} color="#3178C6" />,// Cloud platform
 aws:<SiAmazon size={ICON_SIZES.lg} color="#232F3E" />,azure:<Cloud size={ICON_SIZES.lg} color="#0078D4" />,gcp:<SiGooglecloud size={ICON_SIZES.lg} color="#4285F4" />,aliyun:<SiAlibabacloud size={ICON_SIZES.lg} color="#FF6A00" />,} as const

// ==================== Type export ====================

export type MenuIconKey = keyof typeof menuIconMap
export type TechStackIconKey = keyof typeof techStackIconMap

// ==================== Helper function ====================

/**
 * Get menu icon
 */
export const getMenuIcon = (key:string):React.ReactNode => {
 return menuIconMap[key as MenuIconKey] || <Layers size={ICON_SIZES.md} />
}

/**
 * Get technology stack icon
 */
export const getTechStackIcon = (key:string):React.ReactNode => {
 return techStackIconMap[key as TechStackIconKey] || <Database size={ICON_SIZES.lg} />
}
