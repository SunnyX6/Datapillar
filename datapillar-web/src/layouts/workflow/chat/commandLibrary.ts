export type ChatCommandId = 'clear' | 'compact'

export type ChatCommandOption = {
  id: ChatCommandId
  label: string
  title: string
  description: string
}

export const CHAT_COMMAND_OPTIONS: ChatCommandOption[] = [
  {
    id: 'clear',
    label: '/clear',
    title: '清空会话',
    description: '清空当前会话内容'
  },
  {
    id: 'compact',
    label: '/compact',
    title: '压缩上下文',
    description: '请求后端压缩当前会话'
  }
]
