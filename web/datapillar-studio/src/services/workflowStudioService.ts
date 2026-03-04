/**
 * Workflow Studio service
 *
 * Define workflow data structure,support AI Generate and manually create
 */

import type { JobResponse,DependencyResponse,WorkflowResponse } from './aiWorkflowService'

/**
 * Workflow node definition
 */
export interface WorkflowNodeDefinition {
 id:number | string
 label:string
 componentCode:string
 componentType?: string
 description:string
 positionX:number
 positionY:number
 jobParams?: Record<string,unknown>
}

/**
 * Workflow edge definition
 */
export interface WorkflowEdgeDefinition {
 id:string
 source:string
 target:string
}

/**
 * Workflow statistics
 */
export interface WorkflowStats {
 nodes:number
 edges:number
 runtimeMinutes:number
 qualityScore:number
}

/**
 * Workflow diagram
 */
export interface WorkflowGraph {
 name:string
 summary:string
 lastUpdated:number
 nodes:WorkflowNodeDefinition[]
 edges:WorkflowEdgeDefinition[]
 stats:WorkflowStats
 rawResponse?: WorkflowResponse
}

/**
 * Empty workflow diagram
 */
export const emptyWorkflowGraph:WorkflowGraph = {
 name:'Workflow Studio',summary:'Describe your data goals,AI Orchestration blueprints are generated in seconds.',lastUpdated:Date.now(),nodes:[],edges:[],stats:{
 nodes:0,edges:0,runtimeMinutes:0,qualityScore:0
 }
}

/**
 * will AI returned WorkflowResponse Convert to WorkflowGraph
 */
export function convertAIResponseToGraph(response:WorkflowResponse):WorkflowGraph {
 const nodes:WorkflowNodeDefinition[] = response.jobs.map((job:JobResponse) => ({
 id:job.id?? 0,label:job.jobName,componentCode:job.jobTypeCode,description:job.description || '',positionX:job.positionX,positionY:job.positionY,jobParams:job.jobParams
 }))

 const edges:WorkflowEdgeDefinition[] = response.dependencies.map((dep:DependencyResponse) => ({
 id:`edge-${dep.parentJobId}-${dep.jobId}`,source:String(dep.parentJobId),target:String(dep.jobId)
 }))

 const runtimeMinutes = Math.max(5,Math.round(nodes.length * 4.2))
 const qualityScore = Math.min(99,Math.round(78 + nodes.length * 1.2))

 return {
 name:response.workflowName,summary:response.description || '',lastUpdated:Date.now(),nodes,edges,stats:{
 nodes:nodes.length,edges:edges.length,runtimeMinutes,qualityScore
 },rawResponse:response
 }
}
