import React, { useState, useRef, useEffect } from 'react';
import { MessageSquare, X, Send, Bot, Sparkles, User } from 'lucide-react';
import { ChatMessage } from '../types';
import { generateSalesResponse } from '../services/geminiService';

export const ChatWidget: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'model', text: '你好！我是 Datapillar 智能助手。有什么可以帮您的吗？' }
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isOpen]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage = input.trim();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMessage }]);
    setIsLoading(true);

    try {
      const responseText = await generateSalesResponse(messages, userMessage);
      setMessages(prev => [...prev, { role: 'model', text: responseText }]);
    } catch (error) {
      setMessages(prev => [...prev, { role: 'model', text: "连接似乎有点问题，请稍后再试。" }]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end font-sans">
      {isOpen && (
        <div className="mb-4 w-[360px] md:w-[400px] h-[600px] bg-[#020410] border border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden animate-[fadeIn_0.2s_ease-out] ring-1 ring-white/10">
          {/* Modern Minimal Header */}
          <div className="bg-[#0b0f19]/80 backdrop-blur-md p-4 border-b border-slate-800 flex justify-between items-center absolute top-0 left-0 right-0 z-10">
            <div className="flex items-center gap-3">
              <div className="relative">
                <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-violet-600 to-cyan-600 flex items-center justify-center shadow-lg">
                    <Sparkles className="w-4 h-4 text-white" />
                </div>
                <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 bg-green-500 border-2 border-[#0b0f19] rounded-full"></div>
              </div>
              <div>
                <h3 className="text-white font-medium text-sm">Datapillar AI</h3>
                <p className="text-[10px] text-slate-400">Always active</p>
              </div>
            </div>
            <button 
                onClick={() => setIsOpen(false)} 
                className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-white/5 text-slate-400 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto p-4 pt-20 space-y-6 bg-[#020410] scroll-smooth custom-scrollbar">
            {messages.map((msg, idx) => (
              <div key={idx} className={`flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'}`}>
                
                {/* Avatar */}
                <div className="shrink-0">
                    {msg.role === 'model' ? (
                        <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center">
                            <Bot className="w-4 h-4 text-violet-400" />
                        </div>
                    ) : (
                        <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center">
                            <User className="w-4 h-4 text-slate-400" />
                        </div>
                    )}
                </div>

                {/* Bubble */}
                <div className={`flex flex-col max-w-[80%] ${msg.role === 'user' ? 'items-end' : 'items-start'}`}>
                    <div className="flex items-center gap-2 mb-1">
                        <span className="text-[10px] font-bold text-slate-500">
                            {msg.role === 'user' ? 'You' : 'Datapillar'}
                        </span>
                    </div>
                    <div 
                      className={`px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
                        msg.role === 'user' 
                          ? 'bg-[#2f2f2f] text-white rounded-2xl rounded-tr-sm' 
                          : 'bg-transparent text-slate-300 rounded-2xl rounded-tl-sm pl-0'
                      }`}
                    >
                      {msg.text}
                    </div>
                </div>
              </div>
            ))}
            
            {isLoading && (
               <div className="flex gap-3">
                   <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0">
                        <Bot className="w-4 h-4 text-violet-400" />
                   </div>
                   <div className="flex items-center gap-1 h-8">
                        <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce"></div>
                        <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce delay-75"></div>
                        <div className="w-1.5 h-1.5 bg-slate-600 rounded-full animate-bounce delay-150"></div>
                   </div>
               </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* ChatGPT-style Floating Input Area */}
          <div className="p-4 bg-[#020410] relative">
            <div className="absolute top-0 left-0 w-full h-12 bg-gradient-to-b from-transparent to-[#020410] -translate-y-full pointer-events-none"></div>
            
            <form onSubmit={handleSubmit} className="relative group">
                <div className="relative bg-[#1e1e1e] rounded-3xl border border-slate-700/50 shadow-lg focus-within:border-slate-600 focus-within:ring-1 focus-within:ring-slate-600 transition-all overflow-hidden">
                    <input
                        type="text"
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        placeholder="Message Datapillar..."
                        className="w-full bg-transparent border-none text-white text-sm px-4 py-3.5 pr-12 focus:outline-none placeholder:text-slate-500"
                    />
                    <button 
                        type="submit" 
                        disabled={!input.trim() || isLoading}
                        className={`absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-full transition-all duration-200 ${
                            input.trim() 
                            ? 'bg-white text-black hover:bg-slate-200' 
                            : 'bg-[#2f2f2f] text-slate-500 cursor-not-allowed'
                        }`}
                    >
                        <Send className="w-4 h-4" />
                    </button>
                </div>
                <div className="text-[10px] text-slate-600 text-center mt-3 font-medium">
                    Datapillar AI can make mistakes. Check important info.
                </div>
            </form>
          </div>
        </div>
      )}

      {/* Floating Toggle Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`group flex items-center justify-center w-14 h-14 rounded-full shadow-[0_4px_20px_rgba(0,0,0,0.4)] transition-all duration-300 hover:scale-105 active:scale-95 ${
            isOpen ? 'bg-slate-800 rotate-90' : 'bg-white hover:bg-slate-100'
        }`}
      >
        {isOpen ? (
            <X className="w-6 h-6 text-white" />
        ) : (
            <div className="relative">
                <MessageSquare className="w-6 h-6 text-black fill-black" />
                <span className="absolute -top-1 -right-1 flex h-3 w-3">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-violet-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-3 w-3 bg-violet-500"></span>
                </span>
            </div>
        )}
      </button>
    </div>
  );
};