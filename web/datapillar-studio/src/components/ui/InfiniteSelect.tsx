/**
 * infinite scroll dropdown selector
 * Support lazy loading,Paging scroll loading,maximum height limit
 */

import { useState,useRef,useEffect,useLayoutEffect,useCallback,type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown,Loader2 } from 'lucide-react'
import { Button } from './Button'

/** Default number of loads per page */
const DEFAULT_PAGE_SIZE = 8

export interface InfiniteSelectItem {
 key:string
 code:string
 name:string
 icon?: ReactNode
}

export interface InfiniteSelectProps {
 /** Trigger button copy */
 placeholder:string
 /** color theme */
 variant?: 'blue' | 'purple' | 'slate' | 'emerald'
 /** left icon */
 icon?: ReactNode
 /** selected value(Used to filter and not display) */
 selectedKeys?: string[]
 /** initial data(Avoid above the fold when cached loading) */
 initialItems?: InfiniteSelectItem[]
 /** initial total */
 initialTotal?: number
 /** Trigger button extra class,Easy to align the bottom line */
 triggerClassName?: string
 /** Get data function */
 fetchData:(offset:number,limit:number) => Promise<{ items:InfiniteSelectItem[];total:number }>
 /** select callback */
 onSelect:(item:InfiniteSelectItem) => void
 /** Number of loads per page */
 pageSize?: number
}

/** Color theme mapping */
const variantStyles = {
 blue:{
 trigger:'border-blue-400 text-blue-500',icon:'text-blue-400'
 },purple:{
 trigger:'border-purple-400 text-purple-500',icon:'text-purple-400'
 },slate:{
 trigger:'border-slate-400 text-slate-500',icon:'text-slate-400'
 },emerald:{
 trigger:'border-emerald-400 text-emerald-500',icon:'text-emerald-400'
 }
}

export function InfiniteSelect({
 placeholder,variant = 'slate',icon:_icon,selectedKeys = [],initialItems = [],initialTotal,triggerClassName = '',fetchData,onSelect,pageSize = DEFAULT_PAGE_SIZE
}:InfiniteSelectProps) {
 const [open,setOpen] = useState(false)
 const [items,setItems] = useState<InfiniteSelectItem[]>(initialItems)
 const [total,setTotal] = useState(initialTotal?? initialItems.length?? 0)
 const [loading,setLoading] = useState(false)
 const [loadingMore,setLoadingMore] = useState(false)
 const [initialized,setInitialized] = useState(initialItems.length > 0)

 const triggerRef = useRef<HTMLButtonElement>(null)
 const dropdownRef = useRef<HTMLDivElement>(null)
 const listRef = useRef<HTMLDivElement>(null)
 const [dropdownPos,setDropdownPos] = useState<{ top:number;left:number } | null>(null)

 const styles = variantStyles[variant]
 const hasMore = items.length < total
 const filteredItems = items.filter((item) =>!selectedKeys.includes(item.key))

 // If cached data is later passed in from the outside,Fill it first,Skip above the fold loading
 useEffect(() => {
 if (initialized) return
 if (initialItems.length > 0) {
 setItems(initialItems)
 setTotal(initialTotal?? initialItems.length)
 setInitialized(true)
 }
 },[initialItems,initialTotal,initialized])

 // Calculate drop-down box position
 const updatePosition = useCallback(() => {
 const btn = triggerRef.current
 if (!btn) return
 const rect = btn.getBoundingClientRect()
 setDropdownPos({
 top:rect.bottom + 4,left:rect.left
 })
 },[])

 // Load homepage data
 const loadInitial = useCallback(async () => {
 if (initialized || loading) return
 setLoading(true)
 try {
 const result = await fetchData(0,pageSize)
 setItems(result.items)
 setTotal(result.total)
 } catch {
 setItems([])
 setTotal(0)
 } finally {
 setInitialized(true)
 setLoading(false)
 }
 },[fetchData,pageSize,initialized,loading])

 // load more data
 const loadMore = useCallback(async () => {
 if (loadingMore ||!hasMore) return
 setLoadingMore(true)
 try {
 const result = await fetchData(items.length,pageSize)
 setItems((prev) => [...prev,...result.items])
 setTotal(result.total)
 } catch {
 // Loading failed
 } finally {
 setLoadingMore(false)
 }
 },[fetchData,pageSize,items.length,hasMore,loadingMore])

 // Open drop down box - Just try UI,No matter the data
 const handleOpen = () => {
 if (open) {
 setOpen(false)
 } else {
 updatePosition()
 setOpen(true)
 }
 }

 // UI Open and then load data
 useEffect(() => {
 if (open &&!initialized &&!loading) {
 loadInitial()
 }
 },[open,initialized,loading,loadInitial])

 // Click outside to close
 useEffect(() => {
 if (!open) return
 const handleClickOutside = (e:MouseEvent) => {
 const target = e.target as Node
 if (triggerRef.current?.contains(target)) return
 if (dropdownRef.current?.contains(target)) return
 setOpen(false)
 }
 document.addEventListener('mousedown',handleClickOutside)
 return () => document.removeEventListener('mousedown',handleClickOutside)
 },[open])

 // Monitor window changes and update position
 useLayoutEffect(() => {
 if (!open) return
 window.addEventListener('resize',updatePosition)
 window.addEventListener('scroll',updatePosition,true)
 return () => {
 window.removeEventListener('resize',updatePosition)
 window.removeEventListener('scroll',updatePosition,true)
 }
 },[open,updatePosition])

 // Scroll to load more
 useEffect(() => {
 if (!open ||!listRef.current) return
 const list = listRef.current
 const handleScroll = () => {
 if (list.scrollTop + list.clientHeight >= list.scrollHeight - 20) {
 loadMore()
 }
 }
 list.addEventListener('scroll',handleScroll)
 return () => list.removeEventListener('scroll',handleScroll)
 },[open,loadMore])

 const handleSelect = (item:InfiniteSelectItem) => {
 onSelect(item)
 setOpen(false)
 }

 // Determine whether to display loading
 const showLoading =!initialized || loading

 return (<>
 <Button
 ref={triggerRef}
 type="button"
 onClick={handleOpen}
 variant="link"
 size="tiny"
 className={`shrink-0 inline-flex items-center gap-1 bg-transparent border-b border-dashed ${styles.trigger} text-caption px-1 py-0.5 ${triggerClassName}`}
 >
 <span>{placeholder}</span>
 <ChevronDown size={12} className={open?'rotate-180':''} />
 </Button>

 {open && dropdownPos && createPortal(<div
 ref={dropdownRef}
 style={{ top:dropdownPos.top,left:dropdownPos.left }}
 className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl"
 >
 <div
 ref={listRef}
 className="overflow-y-auto p-1 max-h-60 min-w-40 custom-scrollbar"
 >
 {showLoading?(<div className="flex items-center justify-center py-4">
 <Loader2 size={16} className="animate-spin text-slate-400" />
 </div>):filteredItems.length === 0?(<div className="py-4 text-center text-caption text-slate-400">No data yet</div>):(<>
 {filteredItems.map((item) => (<Button
 key={item.key}
 type="button"
 onClick={() => handleSelect(item)}
 variant="ghost"
 size="small"
 className="w-full flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg text-left hover:bg-slate-50 dark:hover:bg-slate-800"
 >
 <span className="flex items-center gap-1.5">
 {item.icon && <span className={styles.icon}>{item.icon}</span>}
 <span className="text-caption text-slate-500 font-mono">{item.code}</span>
 </span>
 <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.name}</span>
 </Button>))}
 {loadingMore && (<div className="flex items-center justify-center py-2">
 <Loader2 size={14} className="animate-spin text-slate-400" />
 </div>)}
 {!hasMore && items.length > pageSize && (<div className="py-1.5 text-center text-micro text-slate-300">All loaded</div>)}
 </>)}
 </div>
 </div>,document.body)}
 </>)
}
