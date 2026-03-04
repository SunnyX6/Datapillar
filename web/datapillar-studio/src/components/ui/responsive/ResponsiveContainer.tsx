/**
 * Responsive container components
 *
 * Provide automatic padding,maximum width,Responsive layout
 * Alternative to manual processing max-w-xxx,px-4 sm:px-6 class name
 *
 * Usage:* ```tsx
 * <ResponsiveContainer>
 * <h1>Page title</h1>
 * <p>Automatically align page content to the center,Automatically adapt left and right padding</p>
 * </ResponsiveContainer>
 * ```
 */

import type { ReactNode,HTMLAttributes } from 'react'
import { cn } from '@/utils'
import {
 contentMaxWidthClassMap,paddingClassMap,type ContentMaxWidth,type Padding
} from '@/design-tokens/dimensions'

type ResponsiveBreakpoint = 'md' | 'lg' | 'xl' | '2xl' | '3xl'

interface ResponsiveContainerProps extends HTMLAttributes<HTMLDivElement> {
 children:ReactNode
 /** Container maximum width(Default normal,1024px)*/
 maxWidth?: ContentMaxWidth
 /** Whether to center align(Default true)*/
 center?: boolean
 /** Whether to add responsive padding(Default true)*/
 padding?: boolean
 /** padding size(Default md)*/
 paddingScale?: Exclude<Padding,'none'>
 /** Custom class name */
 className?: string
}

/**
 * reactive container
 * Automatically provide maximum width,center alignment,Responsive padding
 */
export function ResponsiveContainer({
 children,maxWidth = 'normal',center = true,padding = true,paddingScale = 'md',className,...props
}:ResponsiveContainerProps) {
 return (<div
 className={cn('w-full',contentMaxWidthClassMap[maxWidth],center && 'mx-auto',padding && paddingClassMap[paddingScale],className)}
 {...props}
 >
 {children}
 </div>)
}

/**
 * Responsive page container
 * Suitable as page root container,Provide full screen height + scroll
 */
export function ResponsivePageContainer({
 children,className
}:{
 children:ReactNode
 className?: string
}) {
 return (<div
 className={cn('min-h-dvh w-full overflow-y-auto custom-scrollbar','bg-slate-50 dark:bg-[#020617]',className)}
 >
 {children}
 </div>)
}

/**
 * Responsive grid container
 * Automatically adapt to the number of columns
 */
export function ResponsiveGrid({
 children,columns = { md:2,lg:3,xl:4 },gap = 'md',className
}:{
 children:ReactNode
 /** Number of columns for different breakpoints */
 columns?: Partial<Record<ResponsiveBreakpoint,number>>
 /** spacing size */
 gap?: 'sm' | 'md' | 'lg'
 className?: string
}) {
 const gapMap = {
 sm:'gap-4',md:'gap-5 lg:gap-6',lg:'gap-6 lg:gap-8 xl:gap-10'
 }

 // Build responsive column class names
 const gridCols = ['grid-cols-1',...Object.entries(columns).map(([bp,cols]) => `${bp}:grid-cols-${cols}`)].join(' ')

 return (<div className={cn('grid w-full',gridCols,gapMap[gap],className)}>
 {children}
 </div>)
}

/**
 * Responsive Flex container
 * Automatically switch directions
 */
export function ResponsiveFlex({
 children,direction = { md:'row' },gap = 'md',className
}:{
 children:ReactNode
 /** Different breakpoint directions */
 direction?: Partial<Record<ResponsiveBreakpoint,'row' | 'col'>>
 /** spacing size */
 gap?: 'sm' | 'md' | 'lg'
 className?: string
}) {
 const gapMap = {
 sm:'gap-3',md:'gap-4 lg:gap-5',lg:'gap-6 lg:gap-7 xl:gap-8'
 }

 // Build responsive direction class names
 const flexDir = ['flex-col',...Object.entries(direction).map(([bp,dir]) => `${bp}:${dir === 'row'?'flex-row':'flex-col'}`)].join(' ')

 return <div className={cn('flex',flexDir,gapMap[gap],className)}>{children}</div>
}
