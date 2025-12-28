/**
 * 数蝶品牌 Logo
 *
 * 设计理念：团队协作 + 数据蝶变
 * 多人围成圆圈，象征团队协作与数据流转
 */

import type { CSSProperties } from 'react'
import logoSvg from '@/assets/icons/logo.png'

interface BrandLogoProps {
  size?: number
  showText?: boolean
  brandName?: string
  brandTagline?: string
  className?: string
  nameClassName?: string
}

export function BrandLogo({
  size = 36,
  showText = false,
  brandName = '',
  brandTagline = '',
  className = '',
  nameClassName = 'text-title leading-tight tracking-tight text-indigo-600 dark:text-indigo-300'
}: BrandLogoProps) {
  const dimension = `${size}px`

  const containerStyle = {
    '--logo-size': dimension
  } as CSSProperties

  return (
    <div
      className={`brand-logo flex items-center gap-3 ${className}`}
      style={containerStyle}
    >
      <div className="[width:var(--logo-size)] [height:var(--logo-size)] flex items-center justify-center">
        <img src={logoSvg} alt="Logo" className="w-full h-full object-contain" />
      </div>

      {showText && brandName && (
        <div className="flex flex-col">
          <span className={nameClassName}>
            {brandName}
          </span>
          {brandTagline && (
            <span className="text-caption text-slate-400 dark:text-slate-500 mt-0.5 leading-snug">
              {brandTagline}
            </span>
          )}
        </div>
      )}
    </div>
  )
}
