import type { ChatMessage } from '@/features/workflow/state'

export { upsertAgentActivityByAgent } from './agentRows'

interface WorkflowGraphLike {
  nodes: unknown[]
}

/**
 * Determine whether a two-column layout needs to be displayed：Switch when a user message appears or there is an existing workflow node.
 */
export const hasWorkflowInteraction = (messages: ChatMessage[], workflow: WorkflowGraphLike) => {
  const hasUserMessage = messages.some((message) => message.role === 'user')
  const hasWorkflow = workflow.nodes.length > 0
  return hasUserMessage || hasWorkflow
}
