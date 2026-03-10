/**
 * Generic form component(to"indicator center"List style as base)
 *
 * design goals:* - Provide a unified table container/Header default style
 * - Header/Row Fully customizable by user(Combined)
 */

import type { HTMLAttributes,ReactNode,ThHTMLAttributes,TdHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'
import { cn } from '@/utils'
import { useInfiniteScroll } from '@/hooks'

type TableLayout = 'fixed' | 'auto'
type TableMinWidth = 'none' | 'wide'

const tableLayoutClassMap:Record<TableLayout,string> = {
 fixed:'table-fixed',auto:'table-auto'
}

const tableMinWidthClassMap:Record<TableMinWidth,string> = {
 none:'',wide:'min-w-table-wide'
}

export interface TableInfiniteScrollProps {
 /** Is there more data?*/
 hasMore:boolean
 /** Is loading */
 loading:boolean
 /** trigger loading more */
 onLoadMore:() => void
 /** trigger threshold:Triggered at how many pixels from the bottom(Default 300) */
 threshold?: number
 /** autofill:When the first screen is not enough to generate scroll bars,Automatic continuous loading(Default true) */
 autoFill?: boolean
 /** dynamic threshold:At least press 1 screen height prefetch(Default true) */
 dynamicThreshold?: boolean
}

function TableInfiniteFooter({
 hasMore,loading,onLoadMore,threshold = 300,autoFill = true,dynamicThreshold = true
}:TableInfiniteScrollProps) {
 const { sentinelRef } = useInfiniteScroll({
 hasMore,loading,onLoadMore,threshold,autoFill,dynamicThreshold
 })

 const statusNode = loading?(<>
 <Loader2 className="w-5 h-5 animate-spin text-blue-500" />
 <span>Loading...</span>
 </>):hasMore?null:(<span>All loaded</span>)

 return (<>
 <div ref={sentinelRef} className="h-1" />
 {statusNode && (<div className="flex justify-center items-center gap-2 py-3 border-t border-slate-100 dark:border-slate-800 text-caption text-slate-400 dark:text-slate-500 bg-white/90 dark:bg-slate-900/90 backdrop-blur-sm">
 {statusNode}
 </div>)}
 </>)
}

export interface TableProps extends Omit<HTMLAttributes<HTMLDivElement>,'children'> {
 children:ReactNode
 /** <table> elemental className */
 tableClassName?: string
 /** table layout(Default fixed,Consistent with the indicator center) */
 layout?: TableLayout
 /** Minimum width preset(Default wide,Correspond.min-w-table-wide) */
 minWidth?: TableMinWidth
 /** Additional content below the table(Such as:Sentinel element,load more) */
 footer?: ReactNode
 /** Infinite scroll down pagination:Table Responsible for rendering sentinel + loading,Data requests are handled by business components */
 infiniteScroll?: TableInfiniteScrollProps
 /** Whether to allow horizontal scrolling. Defaults to true. */
 horizontalScroll?: boolean
}

export function Table({
 children,className,tableClassName,layout = 'fixed',minWidth = 'wide',footer,infiniteScroll,horizontalScroll = true,...props
}:TableProps) {
 return (<div
 className={cn('bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm',className)}
 {...props}
 >
 <div className="overflow-hidden rounded-xl">
 <div className={horizontalScroll?'overflow-x-auto':'overflow-x-hidden'}>
 <table
 className={cn('w-full text-left border-collapse',tableLayoutClassMap[layout],tableMinWidthClassMap[minWidth],tableClassName)}
 >
 {children}
 </table>
 </div>
 </div>
 {footer}
 {infiniteScroll && <TableInfiniteFooter {...infiniteScroll} />}
 </div>)
}

export function TableHeader({
 children,className,...props
}:HTMLAttributes<HTMLTableSectionElement> & { children:ReactNode }) {
 return (<thead
 className={cn('bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-400 font-semibold text-micro uppercase tracking-wider',className)}
 {...props}
 >
 {children}
 </thead>)
}

export function TableBody({
 children,className,...props
}:HTMLAttributes<HTMLTableSectionElement> & { children:ReactNode }) {
 return (<tbody className={cn(className)} {...props}>
 {children}
 </tbody>)
}

export function TableRow({
 children,className,...props
}:HTMLAttributes<HTMLTableRowElement> & { children:ReactNode }) {
 return (<tr className={cn(className)} {...props}>
 {children}
 </tr>)
}

export function TableHead({ className,...props }:ThHTMLAttributes<HTMLTableCellElement>) {
 return <th className={cn('px-4 py-3 align-middle',className)} {...props} />
}

export function TableCell({ className,...props }:TdHTMLAttributes<HTMLTableCellElement>) {
 return <td className={cn('px-4 py-3 align-middle',className)} {...props} />
}
