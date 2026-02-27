import type { ChatMessage } from '@/features/workflow/state'

export { upsertAgentActivityByAgent } from './agentRows'

interface WorkflowGraphLike {
  nodes: unknown[]
}

/**
 * 判断是否需要展示双栏布局：出现用户消息或已有工作流节点就切换
 */
export const hasWorkflowInteraction = (messages: ChatMessage[], workflow: WorkflowGraphLike) => {
  const hasUserMessage = messages.some((message) => message.role === 'user')
  const hasWorkflow = workflow.nodes.length > 0
  return hasUserMessage || hasWorkflow
}
