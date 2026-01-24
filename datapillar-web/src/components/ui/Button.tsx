import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { buttonSizeClassMap as buttonSizeClassMapToken, type ButtonSize as TokenButtonSize } from '@/design-tokens/dimensions'
import { cn } from '@/lib/utils'

export type ButtonVariant = 'primary' | 'outline' | 'dangerOutline' | 'ghost' | 'link'

export type ButtonSize = TokenButtonSize | 'header'

const buttonVariantClassMap: Record<ButtonVariant, string> = {
  // 指标中心 Header「新建指标」同款主按钮
  primary: 'bg-slate-900 dark:bg-blue-600 text-white shadow-md hover:bg-blue-600 dark:hover:bg-blue-700',
  outline:
    'bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700/50',
  dangerOutline:
    'bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:border-rose-200 dark:hover:border-rose-800 hover:text-rose-600 dark:hover:text-rose-300 hover:bg-rose-50 dark:hover:bg-rose-900/20',
  ghost: 'bg-transparent text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white',
  link: 'bg-transparent text-brand-600 dark:text-brand-300 hover:text-brand-700 dark:hover:text-brand-200 hover:underline'
}

const buttonSizeClassMap: Record<ButtonSize, string> = {
  // Header 尺寸：以「词根页」主按钮为基准，使用容器断点（项目大量使用 @md）。
  header: 'px-3 @md:px-4 py-1 @md:py-1.5 text-caption @md:text-body-sm',
  tiny: buttonSizeClassMapToken.tiny,
  compact: buttonSizeClassMapToken.compact,
  small: buttonSizeClassMapToken.small,
  normal: buttonSizeClassMapToken.normal,
  large: buttonSizeClassMapToken.large,
  iconSm: buttonSizeClassMapToken.iconSm,
  icon: buttonSizeClassMapToken.icon
}

const buttonGapClassMap: Record<ButtonSize, string> = {
  header: 'gap-1 @md:gap-1.5',
  tiny: 'gap-1',
  compact: 'gap-1',
  small: 'gap-1.5',
  normal: 'gap-2',
  large: 'gap-2.5',
  iconSm: '',
  icon: ''
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'header', type, ...props }, ref) => {
    return (
      <button
        ref={ref}
        type={type ?? 'button'}
        className={cn(
          'inline-flex items-center justify-center whitespace-nowrap rounded-lg font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/30',
          buttonVariantClassMap[variant],
          buttonSizeClassMap[size],
          buttonGapClassMap[size],
          className
        )}
        {...props}
      />
    )
  }
)

Button.displayName = 'Button'
