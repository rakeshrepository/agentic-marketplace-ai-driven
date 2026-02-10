import React, { useState, useRef, useEffect } from 'react';
import { aiService } from '../services/aiService';
import { mcpService } from '../services/mcpClient';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  toolCalls?: Array<{ name: string; server: string }>;
}

export const McpChatInterface: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [availableTools, setAvailableTools] = useState<number>(0);
  const [connectedServers, setConnectedServers] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // Initialize MCP connections on mount
  useEffect(() => {
    const initMcp = async () => {
      try {
        await mcpService.initialize();
        const tools = await mcpService.getAllTools();
        setAvailableTools(tools.length);
        
        const servers = [...new Set(tools.map(t => t.server))];
        setConnectedServers(servers);
        
        console.log(`✓ MCP initialized: ${tools.length} tools from ${servers.length} servers`);
      } catch (error) {
        console.error('Failed to initialize MCP:', error);
      }
    };

    initMcp();

    // Welcome message
    setMessages([{
      id: crypto.randomUUID(),
      role: 'assistant',
      content: `Hello! I'm your AI assistant powered by MCP (Model Context Protocol). I can help you manage Kafka topics, databases, and more!\n\nTry asking me things like:\n• "Create a Kafka topic called orders with 3 partitions"\n• "List all Kafka topics"\n• "Show me cluster information"\n• "Delete the test-topic"`,
      timestamp: new Date(),
    }]);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content: input,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response = await aiService.chat(input, 'session-1');

      const assistantMessage: Message = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: response.message,
        timestamp: new Date(),
        toolCalls: response.toolCalls?.map(tc => ({
          name: tc.name,
          server: tc.server
        })),
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      const errorMessage: Message = {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: `Sorry, I encountered an error: ${error instanceof Error ? error.message : 'Unknown error'}`,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gradient-to-br from-slate-900 via-purple-900 to-slate-900">
      {/* Header */}
      <div className="bg-slate-800/50 backdrop-blur-sm border-b border-purple-500/20 p-4">
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white flex items-center gap-2">
              <span className="text-3xl">🤖</span>
              MCP AI Assistant
            </h1>
            <p className="text-sm text-purple-300 mt-1">
              Powered by Model Context Protocol
            </p>
          </div>
          <div className="text-right">
            <div className="text-xs text-purple-400">
              {connectedServers.length > 0 ? (
                <>
                  <div className="flex items-center gap-2 justify-end mb-1">
                    <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
                    <span>Connected</span>
                  </div>
                  <div className="text-purple-300">
                    {availableTools} tools from {connectedServers.length} server{connectedServers.length !== 1 ? 's' : ''}
                  </div>
                  <div className="text-purple-400 text-xs mt-1">
                    {connectedServers.join(', ')}
                  </div>
                </>
              ) : (
                <div className="flex items-center gap-2 justify-end">
                  <span className="w-2 h-2 bg-yellow-500 rounded-full"></span>
                  <span>Connecting...</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Chat Messages */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="max-w-4xl mx-auto space-y-4">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-3xl rounded-2xl px-6 py-4 ${
                  message.role === 'user'
                    ? 'bg-gradient-to-r from-purple-600 to-blue-600 text-white'
                    : 'bg-slate-800/50 backdrop-blur-sm text-white border border-purple-500/20'
                }`}
              >
                <div className="whitespace-pre-wrap break-words">{message.content}</div>
                
                {/* Show tool calls if any */}
                {message.toolCalls && message.toolCalls.length > 0 && (
                  <div className="mt-3 pt-3 border-t border-purple-500/20">
                    <div className="text-xs text-purple-300 flex items-center gap-2">
                      <span>🔧</span>
                      <span>
                        Used: {message.toolCalls.map(tc => `${tc.name} (${tc.server})`).join(', ')}
                      </span>
                    </div>
                  </div>
                )}
                
                <div className="text-xs opacity-70 mt-2">
                  {message.timestamp.toLocaleTimeString()}
                </div>
              </div>
            </div>
          ))}

          {isLoading && (
            <div className="flex justify-start">
              <div className="bg-slate-800/50 backdrop-blur-sm rounded-2xl px-6 py-4 border border-purple-500/20">
                <div className="flex items-center gap-2 text-purple-300">
                  <div className="w-2 h-2 bg-purple-500 rounded-full animate-bounce"></div>
                  <div className="w-2 h-2 bg-purple-500 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }}></div>
                  <div className="w-2 h-2 bg-purple-500 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }}></div>
                  <span className="ml-2">Thinking...</span>
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input Area */}
      <div className="bg-slate-800/50 backdrop-blur-sm border-t border-purple-500/20 p-4">
        <form onSubmit={handleSubmit} className="max-w-4xl mx-auto">
          <div className="flex gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder="Ask me anything about Kafka, databases, or other services..."
              disabled={isLoading}
              className="flex-1 bg-slate-900/50 border border-purple-500/30 rounded-xl px-6 py-4 text-white placeholder-purple-300/50 focus:outline-none focus:border-purple-500 focus:ring-2 focus:ring-purple-500/20 disabled:opacity-50"
            />
            <button
              type="submit"
              disabled={isLoading || !input.trim()}
              className="bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-xl px-8 py-4 font-semibold transition-all duration-200 transform hover:scale-105"
            >
              Send
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
