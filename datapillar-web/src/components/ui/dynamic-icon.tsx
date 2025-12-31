/**
 * 动态图标组件
 *
 * 根据图标名称动态渲染 react-icons/si 或 lucide-react 图标
 */

import { memo } from 'react'
import {
  SiApachehive,
  SiApachespark,
  SiApachehadoop,
  SiApachekafka,
  SiApacheflink,
  SiGnubash,
  SiPython
} from 'react-icons/si'
import {
  Box,
  Database,
  Terminal,
  Code,
  ArrowRightLeft,
  type LucideIcon
} from 'lucide-react'
import type { IconType } from 'react-icons'

type IconProps = {
  size?: number | string
  color?: string
  className?: string
}

interface DynamicIconProps extends IconProps {
  name: string
}

/**
 * 图标注册表
 */
const ICON_REGISTRY: Record<string, IconType | LucideIcon> = {
  // react-icons/si
  SiApachehive,
  SiApachespark,
  SiApachehadoop,
  SiApachekafka,
  SiApacheflink,
  SiGnubash,
  SiPython,
  // lucide-react
  Box,
  Database,
  Terminal,
  Code,
  ArrowRightLeft
}

/**
 * 动态图标组件
 *
 * @example
 * <DynamicIcon name="SiApachehive" size={16} color="#FDEE21" />
 * <DynamicIcon name="Database" size={16} className="text-blue-500" />
 */
function DynamicIconComponent({ name, size = 16, color, className }: DynamicIconProps) {
  const Icon = ICON_REGISTRY[name] || Box
  return <Icon size={size} color={color} className={className} />
}

export const DynamicIcon = memo(DynamicIconComponent)
