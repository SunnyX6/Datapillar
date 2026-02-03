import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { X, Send, Headphones, User } from 'lucide-react'

type ChatRole = 'user' | 'model'

interface ChatMessage {
  role: ChatRole
  text: string
}

const getPresetReply = (
  input: string,
  seed: number,
  presetResponses: Array<{ keywords: string[]; reply: string }>,
  fallbackResponses: string[]
) => {
  const normalized = input.toLowerCase()
  const matched = presetResponses.find((item) => item.keywords.some((keyword) => normalized.includes(keyword)))
  if (matched) return matched.reply
  return fallbackResponses[seed % fallbackResponses.length]
}

export function ChatWidget() {
  const { t, i18n } = useTranslation('home')
  const presetResponses = useMemo(
    () => t('chat.presetResponses', { returnObjects: true }) as Array<{ keywords: string[]; reply: string }>,
    [t, i18n.language]
  )
  const fallbackResponses = useMemo(() => t('chat.fallbackResponses', { returnObjects: true }) as string[], [t, i18n.language])
  const initialMessage = t('chat.initialMessage')
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'model', text: initialMessage }
  ])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const replyTimerRef = useRef<number | null>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, isOpen])

  useEffect(() => {
    return () => {
      if (replyTimerRef.current) {
        window.clearTimeout(replyTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    setMessages((prev) => {
      if (prev.length === 1 && prev[0]?.role === 'model') {
        return [{ role: 'model', text: initialMessage }]
      }
      return prev
    })
  }, [initialMessage])

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!input.trim() || isLoading) return

    const userMessage = input.trim()
    setInput('')
    setMessages((prev) => [...prev, { role: 'user', text: userMessage }])
    setIsLoading(true)

    const replyText = getPresetReply(userMessage, messages.length, presetResponses, fallbackResponses)

    if (replyTimerRef.current) {
      window.clearTimeout(replyTimerRef.current)
    }

    replyTimerRef.current = window.setTimeout(() => {
      setMessages((prev) => [...prev, { role: 'model', text: replyText }])
      setIsLoading(false)
    }, 650)
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end font-sans">
      {isOpen && (
        <div className="mb-4 w-96 h-[min(560px,calc(100vh-160px))] bg-[#020410] border border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden animate-[fadeIn_0.2s_ease-out] ring-1 ring-white/10">
          <div className="bg-[#0b0f19]/80 backdrop-blur-md p-4 border-b border-slate-800 flex justify-between items-center absolute top-0 left-0 right-0 z-10">
            <div className="flex items-center gap-3">
              <div className="relative">
                <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-violet-600 to-cyan-600 flex items-center justify-center shadow-lg">
                  <Headphones className="w-4 h-4 text-white" />
                </div>
                <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-green-500 border-2 border-[#0b0f19] rounded-full" />
              </div>
              <div>
                <h3 className="text-white font-medium text-sm">{t('chat.title')}</h3>
                <p className="text-micro text-slate-400">{t('chat.status')}</p>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4 pt-20 space-y-6 bg-[#020410] scroll-smooth custom-scrollbar">
            {messages.map((msg, idx) => (
              <div key={idx} className={`flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}>
                <div className="shrink-0">
                  {msg.role === 'model' ? (
                    <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-violet-600/20 to-cyan-600/20 border border-violet-500/40 flex items-center justify-center">
                      <Headphones className="w-4 h-4 text-violet-300" />
                    </div>
                  ) : (
                    <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center">
                      <User className="w-4 h-4 text-slate-400" />
                    </div>
                  )}
                </div>

                <div className={`flex flex-col max-w-[80%] ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-micro font-bold text-slate-500">
                      {msg.role === 'user' ? t('chat.roleUser') : t('chat.roleAssistant')}
                    </span>
                  </div>
                  <div
                    className={`px-4 py-2.5 text-[13px] leading-relaxed shadow-sm ${
                      msg.role === 'user'
                        ? 'bg-[#2f2f2f] text-white rounded-2xl rounded-tr-sm border border-slate-700/40'
                        : 'bg-[#0f1623] text-slate-200 rounded-2xl rounded-tl-sm border border-slate-700/60'
                    }`}
                  >
                    {msg.text}
                  </div>
                </div>
              </div>
            ))}

            {isLoading && (
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-violet-600/20 to-cyan-600/20 border border-violet-500/40 flex items-center justify-center shrink-0">
                  <Headphones className="w-4 h-4 text-violet-300" />
                </div>
                <div className="flex items-center gap-1 h-8">
                  <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce" />
                  <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce delay-75" />
                  <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce delay-150" />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <div className="p-4 bg-[#020410] relative">
            <div className="absolute top-0 left-0 w-full h-12 bg-gradient-to-b from-transparent to-[#020410] -translate-y-full pointer-events-none" />

            <form onSubmit={handleSubmit} className="relative group">
              <div className="relative bg-[#1e1e1e] rounded-3xl border border-slate-700/50 shadow-lg focus-within:border-slate-600 focus-within:ring-1 focus-within:ring-slate-600 transition-all overflow-hidden">
                <input
                  type="text"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder={t('chat.placeholder')}
                  className="w-full bg-transparent border-none text-white text-[13px] px-4 py-3.5 pr-12 focus:outline-none placeholder:text-slate-500"
                />
                <button
                  type="submit"
                  disabled={!input.trim() || isLoading}
                  className={`absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-full transition-all duration-200 ${
                    input.trim() ? 'bg-blue-500 text-white hover:bg-blue-400' : 'bg-[#2f2f2f] text-slate-500 cursor-not-allowed'
                  }`}
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>
              <div className="text-micro text-slate-600 text-center mt-3 font-medium">
                {t('chat.disclaimer')}
              </div>
            </form>
          </div>
        </div>
      )}

      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`group flex items-center justify-center w-14 h-14 rounded-full shadow-[0_4px_20px_rgba(0,0,0,0.4)] transition-all duration-300 hover:scale-105 active:scale-95 ${
          isOpen ? 'bg-slate-800 rotate-90' : 'bg-gradient-to-tr from-violet-600 to-cyan-600'
        }`}
      >
        {isOpen ? (
          <X className="w-6 h-6 text-white" />
        ) : (
          <div className="relative">
            <Headphones className="w-6 h-6 text-white" />
            <span className="absolute -top-1 -right-1 flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-300 opacity-75" />
              <span className="relative inline-flex rounded-full h-3 w-3 bg-cyan-400" />
            </span>
          </div>
        )}
      </button>
    </div>
  )
}
