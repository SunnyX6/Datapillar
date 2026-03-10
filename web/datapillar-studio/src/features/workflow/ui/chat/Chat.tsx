import { useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState,type KeyboardEvent as ReactKeyboardEvent } from 'react'
import type { StateSnapshot,VirtuosoHandle } from 'react-virtuoso'
import { toast } from 'sonner'
import {
 DEFAULT_WORKFLOW_AI_MODEL_ID,useWorkflowStudioCacheStore,useWorkflowStudioStore,type AgentActivity
} from '@/features/workflow/state'
import { upsertAgentActivityByAgent } from '@/features/workflow/utils/index'
import { convertAIResponseToGraph } from '@/services/workflowStudioService'
import { listCurrentUserModels,setCurrentUserDefaultModel } from '@/services/studioLlmService'
import {
 abortWorkflow,createWorkflowStream,generateSessionId,type SseEvent,type WorkflowChatModel,type WorkflowResponse
} from '@/services/aiWorkflowService'
import { ChatHeader } from './ChatHeader'
import { ChatMessageList } from './ChatMessage'
import { ChatInput,type ChatModelOption } from '@/components/ui/ChatInput'
import { CHAT_COMMAND_OPTIONS,type ChatCommandId } from './commandLibrary'

const DEFAULT_WORKFLOW_INTRO_MESSAGE =
 'hello!i am Datapillar Smart Assistant,Can help you query metadata or analyze ETL demand.Is there anything I can do to help you??'
const DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS = ['Query metadata','Synchronize order table','Associate the order table with the product table,Write summary table']

function resolveModelOptionAiModelId(model:ChatModelOption):number | null {
 if (typeof model.aiModelId === 'number' && Number.isInteger(model.aiModelId) && model.aiModelId > 0) {
 return model.aiModelId
 }
 if (typeof model.id === 'number' && Number.isInteger(model.id) && model.id > 0) {
 return model.id
 }
 return null
}

function hasModelOption(options:ChatModelOption[],aiModelId:number | null):boolean {
 if (aiModelId === null) {
 return false
 }
 return options.some((model) => resolveModelOptionAiModelId(model) === aiModelId)
}

function resolveBackendDefaultAiModelId(options:ChatModelOption[]):number | null {
 const defaultModel = options.find((model) => model.isDefault === true)
 if (!defaultModel) {
 return DEFAULT_WORKFLOW_AI_MODEL_ID
 }
 return resolveModelOptionAiModelId(defaultModel)
}

function resolveWorkflowChatModel(options:ChatModelOption[],selectedAiModelId:number | null,defaultAiModelId:number | null):WorkflowChatModel | null {
 const preferredAiModelId = selectedAiModelId?? defaultAiModelId
 const preferredOption = options.find((option) => resolveModelOptionAiModelId(option) === preferredAiModelId)
 const fallbackOption = options.find((option) => resolveModelOptionAiModelId(option)!== null)
 const resolvedOption = preferredOption?? fallbackOption
 const resolvedAiModelId = resolvedOption?resolveModelOptionAiModelId(resolvedOption):null
 const resolvedProviderModelId = resolvedOption?.providerModelId?.trim()
 if (!resolvedAiModelId ||!resolvedProviderModelId) {
 return null
 }
 return {
 aiModelId:resolvedAiModelId,providerModelId:resolvedProviderModelId
 }
}

const prefetchWorkflowCanvas = (() => {
 let started = false
 return () => {
 if (started) {
 return
 }
 started = true
 void import('@/features/workflow/ui/WorkflowCanvasRenderer')
 }
})()

const hasIdleCallback = typeof window!== 'undefined' && 'requestIdleCallback' in window
const scheduleIdle = (callback:() => void) => {
 if (hasIdleCallback && typeof window.requestIdleCallback === 'function') {
 return window.requestIdleCallback(() => callback())
 }
 return window.setTimeout(callback,32)
}
const cancelIdle = (handle:number | null) => {
 if (!handle) return
 if (hasIdleCallback && typeof window.cancelIdleCallback === 'function') {
 window.cancelIdleCallback(handle)
 return
 }
 clearTimeout(handle)
}

export function ChatPanel() {
 const messages = useWorkflowStudioStore((state) => state.messages)
 const isGenerating = useWorkflowStudioStore((state) => state.isGenerating)
 const isWaitingForResume = useWorkflowStudioStore((state) => state.isWaitingForResume)
 const addMessage = useWorkflowStudioStore((state) => state.addMessage)
 const updateMessage = useWorkflowStudioStore((state) => state.updateMessage)
 const setGenerating = useWorkflowStudioStore((state) => state.setGenerating)
 const setWaitingForResume = useWorkflowStudioStore((state) => state.setWaitingForResume)
 const setWorkflow = useWorkflowStudioStore((state) => state.setWorkflow)
 const setLastPrompt = useWorkflowStudioStore((state) => state.setLastPrompt)
 const resetStudio = useWorkflowStudioStore((state) => state.reset)
 const setInitialized = useWorkflowStudioStore((state) => state.setInitialized)
 const selectedAiModelId = useWorkflowStudioStore((state) => state.selectedAiModelId)
 const defaultAiModelId = useWorkflowStudioStore((state) => state.defaultAiModelId)
 const setSelectedAiModelId = useWorkflowStudioStore((state) => state.setSelectedAiModelId)
 const setDefaultAiModelId = useWorkflowStudioStore((state) => state.setDefaultAiModelId)
 const [modelOptions,setModelOptions] = useState<ChatModelOption[]>([])
 const [input,setInput] = useState('')
 const [showHistory,setShowHistory] = useState(false)
 const [forceScrollVersion,setForceScrollVersion] = useState(0)
 const [commandNotice,setCommandNotice] = useState<string | null>(null)
 const [commandNoticeVisible,setCommandNoticeVisible] = useState(false)
 const commandNoticeHideTimerRef = useRef<number | null>(null)
 const commandNoticeClearTimerRef = useRef<number | null>(null)
 const initialCacheSnapshot = useWorkflowStudioCacheStore.persist?.hasHydrated?.()?useWorkflowStudioCacheStore.getState():null
 const [initialVirtuosoState,setInitialVirtuosoState] = useState<StateSnapshot | null>(() => initialCacheSnapshot?.virtuosoState?? null)
 const [virtuosoKey,setVirtuosoKey] = useState(() => (initialCacheSnapshot?.virtuosoState?1:0))
 const [cacheReady,setCacheReady] = useState(() => useWorkflowStudioCacheStore.persist?.hasHydrated?.()?? true)
 const historyButtonRef = useRef<HTMLButtonElement | null>(null)
 const historyCardRef = useRef<HTMLDivElement | null>(null)
 const virtuosoRef = useRef<VirtuosoHandle | null>(null)
 const cancelStreamRef = useRef<(() => void) | null>(null)
 const sessionIdRef = useRef<string>(generateSessionId())
 const streamingMessageIdRef = useRef<string | null>(null)
 const hasHydratedFromCacheRef = useRef(false)

 useEffect(() => {
 const handle = scheduleIdle(() => {
 prefetchWorkflowCanvas()
 })
 return () => cancelIdle(handle)
 },[])

 useEffect(() => {
 if (cacheReady) {
 return
 }
 const unsubscribe = useWorkflowStudioCacheStore.persist?.onFinishHydration?.(() => {
 const cache = useWorkflowStudioCacheStore.getState()
 setCacheReady(true)
 if (cache.virtuosoState) {
 setInitialVirtuosoState(cache.virtuosoState)
 setVirtuosoKey((key) => key + 1)
 }
 })
 return () => {
 if (unsubscribe) {
 unsubscribe()
 }
 }
 },[cacheReady])

 useLayoutEffect(() => {
 if (!cacheReady || hasHydratedFromCacheRef.current) return
 const cache = useWorkflowStudioCacheStore.getState()
 const hasCacheState =
 cache.messages.length > 0 ||
 cache.workflow.nodes.length > 0 ||
 cache.lastPrompt.length > 0 ||
 cache.selectedAiModelId!== DEFAULT_WORKFLOW_AI_MODEL_ID ||
 cache.defaultAiModelId!== DEFAULT_WORKFLOW_AI_MODEL_ID
 if (hasCacheState) {
 const runtime = useWorkflowStudioStore.getState()
 const hasRuntimeState =
 runtime.messages.length > 0 || runtime.workflow.nodes.length > 0 || runtime.lastPrompt.length > 0
 if (!hasRuntimeState) {
 runtime.hydrateFromCache({
 messages:cache.messages,workflow:cache.workflow,lastPrompt:cache.lastPrompt,isInitialized:cache.isInitialized,isWaitingForResume:false,selectedAiModelId:cache.selectedAiModelId,defaultAiModelId:cache.defaultAiModelId
 })
 }
 }

 hasHydratedFromCacheRef.current = true
 },[cacheReady])

 useEffect(() => {
 let active = true
 const loadCurrentUserModels = async () => {
 try {
 const rows = await listCurrentUserModels()
 if (!active) {
 return
 }
 const options = rows
 .filter((model) => model.modelType?.toLowerCase() === 'chat')
 .filter((model) => resolveModelOptionAiModelId(model)!== null)
 setModelOptions(options)

 const current = useWorkflowStudioStore.getState()
 if (options.length === 0) {
 if (current.defaultAiModelId!== DEFAULT_WORKFLOW_AI_MODEL_ID) {
 setDefaultAiModelId(DEFAULT_WORKFLOW_AI_MODEL_ID)
 }
 if (current.selectedAiModelId!== DEFAULT_WORKFLOW_AI_MODEL_ID) {
 setSelectedAiModelId(DEFAULT_WORKFLOW_AI_MODEL_ID)
 }
 return
 }

 const backendDefaultAiModelId = resolveBackendDefaultAiModelId(options)
 if (backendDefaultAiModelId!== current.defaultAiModelId) {
 setDefaultAiModelId(backendDefaultAiModelId)
 }

 const fallbackAiModelId = resolveModelOptionAiModelId(options[0])?? DEFAULT_WORKFLOW_AI_MODEL_ID
 const nextSelectedAiModelId = hasModelOption(options,current.selectedAiModelId)?current.selectedAiModelId:(backendDefaultAiModelId?? fallbackAiModelId)
 if (nextSelectedAiModelId!== current.selectedAiModelId) {
 setSelectedAiModelId(nextSelectedAiModelId)
 }
 } catch {
 if (active) {
 setModelOptions([])
 }
 }
 }

 void loadCurrentUserModels()
 return () => {
 active = false
 }
 },[setDefaultAiModelId,setSelectedAiModelId])
 const requestForceScroll = useCallback(() => {
 setForceScrollVersion((version) => version + 1)
 },[])

 const persistSnapshot = useCallback(() => {
 if (!cacheReady) return
 const runtime = useWorkflowStudioStore.getState()
 const commitSnapshot = (virtuosoState:StateSnapshot | null) => {
 useWorkflowStudioCacheStore.getState().setSnapshot({
 messages:runtime.messages,workflow:runtime.workflow,lastPrompt:runtime.lastPrompt,isInitialized:runtime.isInitialized,virtuosoState,selectedAiModelId:runtime.selectedAiModelId,defaultAiModelId:runtime.defaultAiModelId
 })
 }
 const handle = virtuosoRef.current
 if (handle?.getState) {
 handle.getState((snapshot) => commitSnapshot(snapshot))
 return
 }
 commitSnapshot(null)
 },[cacheReady])

 useEffect(() => {
 const handlePageHide = () => {
 persistSnapshot()
 }
 window.addEventListener('pagehide',handlePageHide)
 return () => {
 window.removeEventListener('pagehide',handlePageHide)
 persistSnapshot()
 }
 },[persistSnapshot])

 useEffect(() => {
 if (!showHistory) return
 const handleClickOutside = (event:MouseEvent) => {
 const target = event.target as Node
 if (historyButtonRef.current?.contains(target)) return
 if (historyCardRef.current?.contains(target)) return
 setShowHistory(false)
 }
 document.addEventListener('mousedown',handleClickOutside)
 return () => document.removeEventListener('mousedown',handleClickOutside)
 },[showHistory])

 useEffect(() => {
 return () => {
 if (commandNoticeHideTimerRef.current) {
 window.clearTimeout(commandNoticeHideTimerRef.current)
 commandNoticeHideTimerRef.current = null
 }
 if (commandNoticeClearTimerRef.current) {
 window.clearTimeout(commandNoticeClearTimerRef.current)
 commandNoticeClearTimerRef.current = null
 }
 }
 },[])

 // clean up SSE connect
 useEffect(() => {
 return () => {
 if (cancelStreamRef.current) {
 cancelStreamRef.current()
 }
 }
 },[])

 const nextMessageId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`

 // Give a static guide when entering the page:Does not trigger backend requests,avoid LLM Stuck causing infinite loading
 useEffect(() => {
 if (!cacheReady) return
 const store = useWorkflowStudioStore.getState()
 if (store.isInitialized) return
 if (store.messages.length > 0) {
 store.setInitialized(true)
 return
 }

 store.addMessage({
 id:nextMessageId(),role:'assistant',content:DEFAULT_WORKFLOW_INTRO_MESSAGE,timestamp:Date.now(),processRows:[],recommendations:DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS
 })
 store.setInitialized(true)
 },[cacheReady])

 const MAX_PROCESS_ROWS = 200
 const workflowChatModel = useMemo(() => resolveWorkflowChatModel(modelOptions,selectedAiModelId,defaultAiModelId),[defaultAiModelId,modelOptions,selectedAiModelId])

 const historySessions = useMemo(() => [{ group:'today',items:['Real-time data warehouse construction tasks','User portrait ETL Repair'] },{ group:'yesterday',items:['Q3 Sales report analysis','API Interface stress test configuration'] },{ group:'past 7 day',items:['Log cleaning rules V2'] }],[])

 const handleSseEvent = useCallback((evt:SseEvent) => {
 const msgId = streamingMessageIdRef.current
 if (!msgId) return

 if (evt.activity) {
 const activity = evt.activity
 const timestamp = evt.ts?? Date.now()
 const eventName = activity.event_name || activity.event
 const agentKey = activity.agent_en || activity.agent_cn || 'agent'
 const activityId = `${agentKey}:${activity.event}:${eventName}`
 const nextActivity:AgentActivity = {...activity,id:activityId,timestamp
 }
 updateMessage(msgId,(currentMsg) => ({...currentMsg,processRows:upsertAgentActivityByAgent(currentMsg.processRows?? currentMsg.agentRows,nextActivity,MAX_PROCESS_ROWS)
 }))
 }

 if (evt.workflow && typeof evt.workflow === 'object') {
 const graph = convertAIResponseToGraph(evt.workflow as WorkflowResponse)
 setWorkflow(graph)
 if (evt.status === 'done') {
 sessionIdRef.current = generateSessionId()
 }
 }

 const nextMessage = evt.activity?.summary?.trim()
 const nextRecommendations =
 evt.activity?.recommendations && evt.activity.recommendations.length > 0?evt.activity.recommendations:undefined
 const isInterruptEvent = evt.activity?.event === 'interrupt'
 const nextInterrupt = isInterruptEvent?evt.activity?.interrupt:undefined

 updateMessage(msgId,(currentMsg) => ({...currentMsg,content:nextMessage || currentMsg.content,streamStatus:evt.status,interrupt:isInterruptEvent?(nextInterrupt?? { options:[] }):undefined,recommendations:nextRecommendations?? currentMsg.recommendations
 }))

 if (isInterruptEvent) {
 setGenerating(false)
 setWaitingForResume(true)
 return
 }

 if (evt.status === 'done' || evt.status === 'error' || evt.status === 'aborted') {
 setGenerating(false)
 setWaitingForResume(false)
 streamingMessageIdRef.current = null
 }
 },[setGenerating,setWaitingForResume,setWorkflow,updateMessage])

 const startStream = useCallback((prompt:string,resumeValue?: unknown) => {
 if (isGenerating) return
 if (!workflowChatModel) {
 toast.error('Please select an available model before sending')
 return
 }
 prefetchWorkflowCanvas()
 const now = Date.now()
 if (prompt.trim()) {
 addMessage({
 id:nextMessageId(),role:'user',content:prompt.trim(),timestamp:now
 })
 }
 setInput('')
 setLastPrompt(prompt)
 setGenerating(true)

 const assistantMsgId = nextMessageId()
 streamingMessageIdRef.current = assistantMsgId
 addMessage({
 id:assistantMsgId,role:'assistant',content:'',timestamp:Date.now(),processRows:[]
 })
 requestForceScroll()

 if (cancelStreamRef.current) {
 cancelStreamRef.current()
 }

 cancelStreamRef.current = createWorkflowStream(resumeValue?null:prompt,sessionIdRef.current,workflowChatModel,{
 onEvent:handleSseEvent
 },resumeValue)
 },[addMessage,handleSseEvent,isGenerating,requestForceScroll,setGenerating,setLastPrompt,workflowChatModel])

 const sendPrompt = useCallback((prompt:string) => {
 if (isGenerating) return
 const trimmed = prompt.trim()
 if (!trimmed) return
 const shouldResume = isWaitingForResume
 setWaitingForResume(false)
 startStream(trimmed,shouldResume?trimmed:undefined)
 },[isGenerating,isWaitingForResume,setWaitingForResume,startStream])

 const handleSend = () => {
 sendPrompt(input)
 }

 const canSend = input.trim().length > 0

 const handleNewSession = useCallback(() => {
 const preservedAiModelId = useWorkflowStudioStore.getState().selectedAiModelId
 if (cancelStreamRef.current) {
 cancelStreamRef.current()
 cancelStreamRef.current = null
 }
 setWaitingForResume(false)
 streamingMessageIdRef.current = null
 sessionIdRef.current = generateSessionId()
 setGenerating(false)
 resetStudio()
 setSelectedAiModelId(preservedAiModelId)
 setInitialVirtuosoState(null)
 setVirtuosoKey((key) => key + 1)
 addMessage({
 id:nextMessageId(),role:'assistant',content:DEFAULT_WORKFLOW_INTRO_MESSAGE,timestamp:Date.now(),processRows:[],recommendations:DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS
 })
 setInitialized(true)
 setInput('')
 },[addMessage,resetStudio,setGenerating,setInitialized,setSelectedAiModelId,setWaitingForResume])

 const handleSetDefaultModel = useCallback(async (aiModelId:number) => {
 try {
 await setCurrentUserDefaultModel(aiModelId)
 setDefaultAiModelId(aiModelId)
 setSelectedAiModelId(aiModelId)
 } catch (error) {
 const message = error instanceof Error?error.message:String(error)
 toast.error(`Setting default model failed:${message}`)
 }
 },[setDefaultAiModelId,setSelectedAiModelId])

 const showCommandNotice = useCallback((message:string) => {
 const totalDuration = 3000
 const fadeDuration = 200
 const hideDelay = Math.max(0,totalDuration - fadeDuration)
 setCommandNotice(message)
 setCommandNoticeVisible(true)
 if (commandNoticeHideTimerRef.current) {
 window.clearTimeout(commandNoticeHideTimerRef.current)
 }
 if (commandNoticeClearTimerRef.current) {
 window.clearTimeout(commandNoticeClearTimerRef.current)
 }
 commandNoticeHideTimerRef.current = window.setTimeout(() => {
 setCommandNoticeVisible(false)
 commandNoticeHideTimerRef.current = null
 },hideDelay)
 commandNoticeClearTimerRef.current = window.setTimeout(() => {
 setCommandNotice(null)
 commandNoticeClearTimerRef.current = null
 },totalDuration)
 },[])

 const handleCommand = useCallback((commandId:ChatCommandId) => {
 const command = CHAT_COMMAND_OPTIONS.find((item) => item.id === commandId)
 const label = command?.label?? `/${commandId}`
 if (commandId === 'clear') {
 showCommandNotice(`Triggered ${label},It will take effect after the backend is connected.`)
 return
 }
 if (commandId === 'compact') {
 showCommandNotice(`Triggered ${label},It will take effect after the backend is connected.`)
 }
 },[showCommandNotice])

 const isComposingRef = useRef(false)

 const handleCompositionStart = () => {
 isComposingRef.current = true
 }

 const handleCompositionEnd = () => {
 isComposingRef.current = false
 }

 const handleKeyDown = (event:ReactKeyboardEvent<HTMLTextAreaElement>) => {
 if (event.nativeEvent.isComposing || isComposingRef.current) {
 return
 }
 if (event.key === 'Enter' &&!event.shiftKey) {
 event.preventDefault()
 handleSend()
 }
 }

 // interrupt current run
 const latestInterrupt = useMemo(() => {
 for (let index = messages.length - 1;index >= 0;index -= 1) {
 const message = messages[index]
 if (message.role!== 'assistant') continue
 const interruptId = message.interrupt?.interrupt_id
 if (interruptId) {
 return { messageId:message.id,interruptId }
 }
 }
 return null
 },[messages])

 const handleAbort = useCallback(async () => {
 if (!isGenerating &&!isWaitingForResume) return

 const isInterruptAbort = Boolean(isWaitingForResume && latestInterrupt?.interruptId)
 try {
 if (isWaitingForResume &&!latestInterrupt?.interruptId) {
 return
 }
 if (isInterruptAbort) {
 if (cancelStreamRef.current) {
 cancelStreamRef.current()
 cancelStreamRef.current = null
 }
 const response = await abortWorkflow(sessionIdRef.current,latestInterrupt?.interruptId)
 setGenerating(false)
 setWaitingForResume(false)
 const msgId = latestInterrupt?.messageId
 if (msgId) {
 updateMessage(msgId,{
 content:response.message,streamStatus:'aborted',interrupt:undefined
 })
 streamingMessageIdRef.current = null
 }
 return
 }

 // Close first SSE connect
 if (cancelStreamRef.current) {
 cancelStreamRef.current()
 cancelStreamRef.current = null
 }

 // Call backend abort API
 const response = await abortWorkflow(sessionIdRef.current)

 // update UI Status
 setGenerating(false)
 setWaitingForResume(false)

 // Update message status
 const msgId = streamingMessageIdRef.current
 if (msgId) {
 updateMessage(msgId,{
 content:response.message,streamStatus:'aborted',interrupt:undefined
 })
 streamingMessageIdRef.current = null
 }
 } catch (error) {
 console.error('[Chat] Abort failed:',error)
 }
 },[isGenerating,isWaitingForResume,latestInterrupt,updateMessage,setGenerating,setWaitingForResume])

 // ESC key interrupt
 useEffect(() => {
 const handleEsc = (event:KeyboardEvent) => {
 if (event.key === 'Escape' && (isGenerating || isWaitingForResume)) {
 event.preventDefault()
 handleAbort()
 }
 }

 document.addEventListener('keydown',handleEsc)
 return () => document.removeEventListener('keydown',handleEsc)
 },[isGenerating,isWaitingForResume,handleAbort])

 const latestAssistantMessageId = useMemo(() => {
 for (let index = messages.length - 1;index >= 0;index -= 1) {
 const message = messages[index]
 if (message.role === 'assistant') {
 return message.id
 }
 }
 return null
 },[messages])

 // Header Title:Show only the latest user messages that have been sent
 const latestUserMessage = (() => {
 for (let index = messages.length - 1;index >= 0;index -= 1) {
 const message = messages[index]
 if (message.role === 'user') {
 return message.content.trim()
 }
 }
 return ''
 })()

 return (<aside className="w-full h-full flex-shrink-0 bg-white/90 dark:bg-slate-900/95 border-r border-slate-200/60 dark:border-slate-800/80 flex flex-col overflow-hidden">
 <div className="relative">
 <ChatHeader
 showHistory={showHistory}
 onToggleHistory={() => setShowHistory((prev) =>!prev)}
 historySessions={historySessions}
 historyButtonRef={historyButtonRef}
 historyCardRef={historyCardRef}
 latestUserMessage={latestUserMessage}
 onNewSession={handleNewSession}
 />
 {commandNotice && (<div className="pointer-events-none absolute inset-0 z-50 flex items-center justify-center px-16">
 <div
 className={`rounded-lg border border-blue-100/80 dark:border-blue-900/60 bg-blue-50/90 dark:bg-blue-900/30 px-3 py-1.5 text-xs font-medium text-blue-700 dark:text-blue-300 shadow-sm transition-all duration-200 ease-out ${
 commandNoticeVisible?'opacity-100 translate-y-0':'opacity-0 -translate-y-1'
 }`}
 >
 {commandNotice}
 </div>
 </div>)}
 </div>
 <ChatMessageList
 key={virtuosoKey}
 messages={messages}
 forceScrollVersion={forceScrollVersion}
 autoScrollEnabled={isGenerating}
 latestAssistantMessageId={latestAssistantMessageId}
 restoreStateFrom={initialVirtuosoState}
 virtuosoRef={virtuosoRef}
 onAbort={handleAbort}
 onQuickSend={sendPrompt}
 className="flex-1 mt-2"
 />
 <ChatInput
 input={input}
 onInputChange={setInput}
 onCompositionStart={handleCompositionStart}
 onCompositionEnd={handleCompositionEnd}
 onKeyDown={handleKeyDown}
 onFocus={prefetchWorkflowCanvas}
 onSend={handleSend}
 onAbort={handleAbort}
 isGenerating={isGenerating}
 isWaitingForResume={isWaitingForResume}
 canSend={canSend}
 selectedModelId={selectedAiModelId}
 defaultModelId={defaultAiModelId}
 modelOptions={modelOptions}
 onModelChange={setSelectedAiModelId}
 onDefaultModelChange={(aiModelId) => {
 void handleSetDefaultModel(aiModelId)
 }}
 commandOptions={CHAT_COMMAND_OPTIONS}
 onCommand={handleCommand}
 />
 </aside>)
}
