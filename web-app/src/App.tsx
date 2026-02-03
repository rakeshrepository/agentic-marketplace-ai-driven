import { useState } from 'react';
import { Agent } from './types';
import { agentRegistry } from './config/agentRegistry';
import { AgentList } from './components/AgentList';
import { ChatInterface } from './components/ChatInterface';
import { SearchBar } from './components/SearchBar';

function App() {
  const [selectedAgent, setSelectedAgent] = useState<Agent | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-900 to-slate-900">
      {/* Animated Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-blue-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob"></div>
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-cyan-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob animation-delay-2000"></div>
        <div className="absolute bottom-0 left-1/3 w-96 h-96 bg-indigo-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob animation-delay-4000"></div>
      </div>

      {/* Header */}
      <header className="relative backdrop-blur-sm bg-white/5 border-b border-white/10">
        <div className="max-w-7xl mx-auto px-6 py-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="relative">
                <div className="absolute inset-0 bg-gradient-to-r from-blue-500 to-cyan-500 rounded-xl blur opacity-75 animate-pulse"></div>
                <div className="relative bg-gradient-to-r from-blue-600 to-cyan-600 p-3 rounded-xl">
                  <span className="text-3xl">🤖</span>
                </div>
              </div>
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-blue-400 via-cyan-400 to-indigo-400 bg-clip-text text-transparent">
                  Agentic Marketplace
                </h1>
                <p className="text-sm text-blue-300/80 mt-1">
                  ✨ AI-powered agents for intelligent automation
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="px-4 py-2 rounded-lg bg-green-500/20 border border-green-500/30 text-green-300 text-sm font-medium">
                🟢 All Systems Online
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="relative max-w-7xl mx-auto px-6 py-8">
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-[calc(100vh-200px)]">
          {/* Left Panel - Agent List */}
          <div className="lg:col-span-1 overflow-hidden flex flex-col">
            <div className="mb-4">
              <SearchBar value={searchQuery} onChange={setSearchQuery} />
            </div>
            <div className="flex-1 overflow-y-auto pr-2 custom-scrollbar">
              <AgentList
                agents={agentRegistry.agents}
                categories={agentRegistry.categories}
                selectedAgent={selectedAgent}
                onSelectAgent={setSelectedAgent}
                searchQuery={searchQuery}
              />
            </div>
          </div>

          {/* Right Panel - Chat Interface */}
          <div className="lg:col-span-2 min-h-0">
            {selectedAgent ? (
              <ChatInterface agent={selectedAgent} />
            ) : (
              <div className="h-full flex items-center justify-center backdrop-blur-sm bg-white/5 rounded-2xl border border-white/10 shadow-2xl">
                <div className="text-center px-8">
                  <div className="relative inline-block mb-6">
                    <div className="absolute inset-0 bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full blur-xl opacity-50 animate-pulse"></div>
                    <div className="relative text-6xl">💬</div>
                  </div>
                  <h3 className="text-2xl font-bold text-white mb-3">
                    Select an Agent to Start
                  </h3>
                  <p className="text-blue-300/70 max-w-md">
                    Choose an AI agent from the list to begin your intelligent conversation
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
