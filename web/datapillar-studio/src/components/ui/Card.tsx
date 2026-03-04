/**
 * Universal card component(to"metasemantic home page"card as basis)
 *
 * design goals:* - Background for unified card container/border/rounded corners/hover interaction
 * - Pass padding Options cover different content density scenarios
 */

import type { HTMLAttributes,ReactNode } from 'react'
import { cn } from '@/utils'
import { cardWidthClassMap,type CardWidth } from '@/design-tokens/dimensions'

type CardVariant = 'default' | 'interactive'

type CardPadding = 'none' | 'sm' | 'md' | 'lg'

const cardVariantClassMap:Record<CardVariant,string> = {
 default:'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800',// metasemantic home page(HubAssetCard)Same style interaction:hover shadow + blue stroke
 interactive:'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:shadow-lg hover:border-blue-300 dark:hover:border-blue-600'
}

const cardPaddingClassMap:Record<CardPadding,string> = {
 none:'',sm:'p-4',md:'p-4 @md:p-6',lg:'p-6 @md:p-8'
}

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>,'children'> {
 children:ReactNode
 /** card maximum width(use design-tokens/dimensions in cardWidthClassMap) */
 size?: CardWidth
 /** Default interactive:Consistent with the metasemantic homepage card */
 variant?: CardVariant
 /** Default md:p-4 @md:p-6(Consistent with the metasemantic homepage card) */
 padding?: CardPadding
}

export function Card({ children,className,size,variant = 'interactive',padding = 'md',...props }:CardProps) {
 return (<div
 className={cn('rounded-xl @md:rounded-2xl transition-all',size?cardWidthClassMap[size]:null,cardVariantClassMap[variant],cardPaddingClassMap[padding],className)}
 {...props}
 >
 {children}
 </div>)
}
