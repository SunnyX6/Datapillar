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
    title: 'clear session',
    description: 'Clear current session content'
  },
  {
    id: 'compact',
    label: '/compact',
    title: 'Compression context',
    description: 'Requests the backend to compress the current session'
  }
]
