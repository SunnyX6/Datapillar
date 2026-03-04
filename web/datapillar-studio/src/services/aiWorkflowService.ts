/**
 * AI Workflow services
 *
 * use SSE/JSON event streaming(The browser can be disconnected and reconnected based on Last-Event-ID replay)
 * Protocol version:v3(unify stream event)
 */

import { API_BASE,API_PATH,openSse,requestData,requestEnvelope } from '@/api'
import type {
 AbortWorkflowResult,SseEvent,StreamCallbacks,WorkflowChatModel
} from '@/services/types/ai/workflow'

export type {
 ActivityEvent,ProcessActivity,ProcessStatus,SseEvent,SseEventType,StreamCallbacks,WorkflowChatModel,StreamInterrupt,StreamStatus,WorkflowResponse,JobResponse,DependencyResponse,AbortWorkflowResult
} from '@/services/types/ai/workflow'

/**
 * Generate unique session ID
 */
export function generateSessionId():string {
 return `session-${Date.now()}-${Math.random().toString(36).slice(2,9)}`
}

/**
 * create AI Workflow message flow
 */
export function createWorkflowStream(userInput:string | null,sessionId:string,model:WorkflowChatModel,callbacks:StreamCallbacks,resumeValue?: unknown):() => void {
 let closed = false
 let eventSource:EventSource | null = null

 const request = async () => {
 try {
 const emitLocalError = (message:string,detail?: string) => {
 callbacks.onEvent({
 ts:Date.now(),run_id:`local-${Date.now()}`,status:'error',activity:{
 agent_cn:'system',agent_en:'system',summary:detail?`${message}:${detail}`:message,event:'llm',event_name:'llm',status:'error',interrupt:{ options:[] },recommendations:[]
 },workflow:null
 })
 }

 // Use uniformly /workflow/chat endpoint
 await requestEnvelope<{ success:boolean },{
 userInput:string | null
 sessionId:string
 model:WorkflowChatModel
 resumeValue?: unknown
 }>({
 baseURL:API_BASE.aiWorkflow,url:API_PATH.workflow.chat,method:'POST',data:{
 userInput,sessionId,model,resumeValue
 }
 })

 if (closed) return

 eventSource = openSse({
 baseURL:API_BASE.aiWorkflow,url:API_PATH.workflow.sse,params:{ sessionId },withCredentials:true
 })

 eventSource.onmessage = (event) => {
 if (closed) return
 try {
 const sseEvent = JSON.parse(event.data) as SseEvent
 callbacks.onEvent(sseEvent)
 if (sseEvent.status === 'done' || sseEvent.status === 'error' || sseEvent.status === 'aborted') {
 eventSource?.close()
 eventSource = null
 }
 } catch (error) {
 emitLocalError('parse SSE Message failed',error instanceof Error?error.message:String(error))
 eventSource?.close()
 eventSource = null
 }
 }

 eventSource.onerror = () => {
 if (closed) return
 // hand over EventSource Automatically reconnect;Only stops when the connection is explicitly closed
 if (eventSource && eventSource.readyState === EventSource.CLOSED) {
 emitLocalError('SSE connection closed')
 }
 }
 } catch (error) {
 const message = error instanceof Error?error.message:String(error)
 callbacks.onEvent({
 ts:Date.now(),run_id:`local-${Date.now()}`,status:'error',activity:{
 agent_cn:'system',agent_en:'system',summary:`Connection failed:${message}`,event:'llm',event_name:'llm',status:'error',interrupt:{ options:[] },recommendations:[]
 },workflow:null
 })
 }
 }

 request()

 return () => {
 closed = true
 eventSource?.close()
 eventSource = null
 }
}

/**
 * interrupt current run
 *
 * Interrupted by run(current execution),No session(Conversation history).* Users can continue in the same session after interrupting session Send new message.*/
export async function abortWorkflow(sessionId:string,interruptId?: string):Promise<AbortWorkflowResult> {
 const payload:{ sessionId:string;interruptId?: string } = { sessionId }
 if (interruptId) {
 payload.interruptId = interruptId
 }
 return requestData<AbortWorkflowResult,{ sessionId:string;interruptId?: string }>({
 baseURL:API_BASE.aiWorkflow,url:API_PATH.workflow.abort,method:'POST',data:payload
 })
}
