/**
 * Dynamic icon component
 *
 * Dynamically render based on icon name react-icons/si or lucide-react icon
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
 * Icon registry
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
 * Dynamic icon component
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
