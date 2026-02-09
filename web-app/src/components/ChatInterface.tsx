import React, { useState, useRef, useEffect } from 'react';
import { Agent, ChatMessage, AgentResponse } from '../types';
import { sendQuery } from '../services/agentService';

interface ChatInterfaceProps {
  agent: Agent;
  onBack?: () => void;
}

export const ChatInterface: React.FC<ChatInterfaceProps> = ({ agent, onBack }) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const getExampleQueries = (): string[] => {
    // Use exampleQueries from agent if available, otherwise return defaults
    if (agent.exampleQueries && agent.exampleQueries.length > 0) {
      return agent.exampleQueries.map(q => `"${q}"`);
    }
    
    // Fallback defaults
    return [
      '"Help me get started"',
      '"What can you do?"',
    ];
  };

  useEffect(() => {
    // Reset chat when agent changes
    const examples = getExampleQueries();
    const exampleText = examples.map(ex => `- ${ex}`).join('\n');
    
    setMessages([
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: `Hello! I'm the ${agent.name}. ${agent.description}\n\nTry asking me things like:\n${exampleText}`,
        timestamp: new Date(),
      },
    ]);
  }, [agent]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: input,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response: AgentResponse = await sendQuery(agent.endpoint, input);

      const assistantMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.success
          ? response.message || 'Operation completed successfully'
          : response.error || 'An error occurred',
        timestamp: new Date(),
        // Only include data if it's not null and not empty
        ...(response.data != null && response.data !== '' && response.data !== '[]' && response.data !== '{}' && (
          Array.isArray(response.data) ? response.data.length > 0 : true
        ) ? { data: response.data } : {}),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      const errorMessage: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: 'Sorry, I encountered an error processing your request.',
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const formatData = (data: unknown): string => {
    if (Array.isArray(data)) {
      return data.map((item) => `• ${typeof item === 'string' ? item : JSON.stringify(item)}`).join('\n');
    }
    if (typeof data === 'object' && data !== null) {
      return JSON.stringify(data, null, 2);
    }
    return String(data);
  };

  return (
    <div className="flex flex-col h-full bg-slate-900/60 backdrop-blur-sm rounded-xl shadow-2xl border border-purple-500/30">
      <div className="p-5 border-b border-purple-500/30 bg-gradient-to-r from-indigo-600/20 via-purple-600/20 to-pink-600/20 backdrop-blur-sm rounded-t-xl shadow-lg shadow-purple-900/30">
        <div className="flex items-center justify-between">
          <div className="flex-1">
            <h2 className="text-xl font-bold bg-gradient-to-r from-indigo-300 via-purple-300 to-pink-300 bg-clip-text text-transparent drop-shadow-lg">{agent.name}</h2>
            <p className="text-sm text-slate-300/80 mt-1 font-light">Category: {agent.category}</p>
          </div>
          {onBack && (
            <button
              onClick={onBack}
              className="flex items-center gap-2 px-4 py-2 bg-slate-800/60 hover:bg-slate-800/80 text-white rounded-lg transition-all duration-200 border border-purple-500/30 shadow-md shadow-purple-500/20 hover:shadow-lg hover:shadow-purple-500/30"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              <span>Back to Agents</span>
            </button>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">
        {messages.map((message) => {
          return (
            <div
              key={message.id}
              className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[80%] rounded-xl p-4 ${
                  message.role === 'user'
                    ? 'bg-gradient-to-br from-indigo-600 via-purple-600 to-pink-600 text-white shadow-lg shadow-purple-500/50'
                    : 'bg-slate-800/60 backdrop-blur-sm text-white border border-purple-500/30'
                }`}
              >
                <p className="whitespace-pre-wrap">{message.content}</p>
                {message.data !== undefined && (
                  <pre className="mt-3 p-3 bg-slate-950/70 text-emerald-300 rounded-lg text-xs overflow-x-auto border border-emerald-500/30 shadow-inner">
                    {String(formatData(message.data))}
                  </pre>
                )}
                <p
                  className={`text-xs mt-2 ${
                    message.role === 'user' ? 'text-purple-100' : 'text-slate-400'
                  }`}
                >
                  {message.timestamp.toLocaleTimeString()}
                </p>
              </div>
            </div>
          );
        })}
        {isLoading && (
          <div className="flex justify-start">
            <div className="bg-white/10 backdrop-blur-sm border border-white/10 rounded-xl p-4">
              <div className="flex space-x-2">
                <div className="w-2 h-2 bg-indigo-400 rounded-full animate-bounce" />
                <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce animation-delay-100" />
                <div className="w-2 h-2 bg-pink-400 rounded-full animate-bounce animation-delay-200" />
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSubmit} className="p-5 border-t border-purple-500/30">
        <div className="flex gap-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type your message..."
            className="flex-1 px-5 py-3 bg-slate-800/60 backdrop-blur-sm border border-purple-500/30 rounded-xl text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent transition-all shadow-md shadow-purple-900/20"
            disabled={isLoading}
          />
          <button
            type="submit"
            disabled={isLoading || !input.trim()}
            className="px-6 py-3 bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 text-white font-medium rounded-xl hover:shadow-xl hover:shadow-purple-500/50 disabled:from-gray-600 disabled:to-gray-600 disabled:cursor-not-allowed disabled:shadow-none transition-all duration-300"
          >
            Send
          </button>
        </div>
      </form>
    </div>
  );
};
