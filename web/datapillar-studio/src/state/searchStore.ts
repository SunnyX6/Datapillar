/**
 * Global search status management
 *
 * Dynamically switch search scopes and prompts based on the current page context
 */

import { create } from 'zustand'

export type SearchContext =
 | 'dashboard'
 | 'metadata'
 | 'semantic'
 | 'semantic-metrics'
 | 'semantic-glossary'
 | 'knowledge'
 | 'default'

interface SearchContextConfig {
 placeholder:string
 scope:string[]
}

const SEARCH_CONTEXT_MAP:Record<SearchContext,SearchContextConfig> = {
 dashboard:{
 placeholder:'Search items,Workflow...',scope:['projects','workflows']
 },metadata:{
 placeholder:'Search Catalog,Schema,Table...',scope:['catalog','schema','table']
 },semantic:{
 placeholder:'search metrics,root,Data services...',scope:['metrics','glossary','apis','models','standards']
 },'semantic-metrics':{
 placeholder:'Search metric name or encoding...',scope:['metrics']
 },'semantic-glossary':{
 placeholder:'Search for root words,meaning...',scope:['glossary']
 },knowledge:{
 placeholder:'Search knowledge graph nodes...',scope:['knowledge']
 },default:{
 placeholder:'global search...',scope:['all']
 }
}

interface SearchState {
 /** Current search keywords */
 searchTerm:string
 /** Current search context */
 context:SearchContext
 /** Whether the search box is expanded */
 isOpen:boolean
 /** Set search keywords */
 setSearchTerm:(term:string) => void
 /** Set search context */
 setContext:(context:SearchContext) => void
 /** Set search box expansion state */
 setIsOpen:(isOpen:boolean) => void
 /** Clear search */
 clearSearch:() => void
 /** Get the current context configuration */
 getContextConfig:() => SearchContextConfig
}

export const useSearchStore = create<SearchState>((set,get) => ({
 searchTerm:'',context:'default',isOpen:false,setSearchTerm:(term) => set({ searchTerm:term }),setContext:(context) => set({ context,searchTerm:'' }),setIsOpen:(isOpen) => set({ isOpen }),clearSearch:() => set({ searchTerm:'',isOpen:false }),getContextConfig:() => {
 const { context } = get()
 return SEARCH_CONTEXT_MAP[context] || SEARCH_CONTEXT_MAP.default
 }
}))

/** Hook:Get the current search context placeholder */
export const useSearchPlaceholder = () => {
 const context = useSearchStore((state) => state.context)
 return SEARCH_CONTEXT_MAP[context]?.placeholder || SEARCH_CONTEXT_MAP.default.placeholder
}

/** Hook:Get current search keywords */
export const useSearchTerm = () => useSearchStore((state) => state.searchTerm)
