import React, { useState, useRef, useEffect } from 'react';
import { Agent, ChatMessage, AgentResponse } from '../types';
import { sendQuery } from '../services/agentService';

interface ChatInterfaceProps {
  agent: Agent;
}

export const ChatInterface: React.FC<ChatInterfaceProps> = ({ agent }) => {
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
        data: response.data,
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
    <div className="flex flex-col h-full bg-white/5 backdrop-blur-sm rounded-xl shadow-2xl border border-white/10">
      <div className="p-5 border-b border-white/10 bg-gradient-to-r from-blue-500/20 to-cyan-500/20 backdrop-blur-sm rounded-t-xl">
        <h2 className="text-xl font-bold bg-gradient-to-r from-blue-300 to-cyan-300 bg-clip-text text-transparent">{agent.name}</h2>
        <p className="text-sm text-blue-200/70 mt-1">Category: {agent.category}</p>
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
                    ? 'bg-gradient-to-br from-blue-500 to-cyan-500 text-white shadow-lg shadow-blue-500/50'
                    : 'bg-white/10 backdrop-blur-sm text-white border border-white/10'
                }`}
              >
                <p className="whitespace-pre-wrap">{message.content}</p>
                {message.data !== undefined && (
                  <pre className="mt-3 p-3 bg-slate-900/50 text-green-300 rounded-lg text-xs overflow-x-auto border border-green-500/20">
                    {String(formatData(message.data))}
                  </pre>
                )}
                <p
                  className={`text-xs mt-2 ${
                    message.role === 'user' ? 'text-blue-100' : 'text-blue-200/60'
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
                <div className="w-2 h-2 bg-blue-400 rounded-full animate-bounce" />
                <div className="w-2 h-2 bg-cyan-400 rounded-full animate-bounce animation-delay-100" />
                <div className="w-2 h-2 bg-indigo-400 rounded-full animate-bounce animation-delay-200" />
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSubmit} className="p-5 border-t border-white/10">
        <div className="flex gap-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type your message..."
            className="flex-1 px-5 py-3 bg-white/10 backdrop-blur-sm border border-white/20 rounded-xl text-white placeholder-blue-200/50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            disabled={isLoading}
          />
          <button
            type="submit"
            disabled={isLoading || !input.trim()}
            className="px-6 py-3 bg-gradient-to-r from-blue-500 to-cyan-500 text-white font-medium rounded-xl hover:shadow-lg hover:shadow-blue-500/50 disabled:from-gray-600 disabled:to-gray-600 disabled:cursor-not-allowed disabled:shadow-none transition-all duration-300"
          >
            Send
          </button>
        </div>
      </form>
    </div>
  );
};
