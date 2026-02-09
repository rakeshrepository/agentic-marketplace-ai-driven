import { useState, useEffect } from 'react';
import { Agent, Category } from './types';
import { getAllAgents, getAllCategories } from './services/agentService';
import { AgentList } from './components/AgentList';
import { ChatInterface } from './components/ChatInterface';
import { SearchBar } from './components/SearchBar';
import { CategoryTabs } from './components/CategoryTabs';
import AgentOnboarding from './components/AgentOnboarding';

function App() {
  const [currentPage, setCurrentPage] = useState<'home' | 'onboard'>('home');
  const [selectedAgent, setSelectedAgent] = useState<Agent | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [agents, setAgents] = useState<Agent[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [fetchedAgents, fetchedCategories] = await Promise.all([
        getAllAgents(),
        getAllCategories()
      ]);
      setAgents(fetchedAgents);
      setCategories(fetchedCategories);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  // Filter agents based on selected category and search query
  const filteredAgents = agents.filter(agent => {
    const matchesCategory = selectedCategory === 'all' || agent.category === selectedCategory;
    const matchesSearch = agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.capabilities.some(cap => cap.toLowerCase().includes(searchQuery.toLowerCase()));
    return matchesCategory && matchesSearch;
  });

  // Calculate agent counts per category
  const agentCounts = agents.reduce((acc, agent) => {
    acc[agent.category] = (acc[agent.category] || 0) + 1;
    return acc;
  }, {} as Record<string, number>);

  // Render onboarding page
  if (currentPage === 'onboard') {
    return <AgentOnboarding />;
  }

  // Render home page
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-indigo-950 to-slate-950">
      {/* Animated Background */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-0 left-1/4 w-96 h-96 bg-indigo-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob"></div>
        <div className="absolute top-0 right-1/4 w-96 h-96 bg-purple-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob animation-delay-2000"></div>
        <div className="absolute bottom-0 left-1/3 w-96 h-96 bg-pink-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob animation-delay-4000"></div>
      </div>

      {/* Header */}
      <header className="relative backdrop-blur-sm bg-slate-900/50 border-b border-purple-500/20 shadow-2xl shadow-purple-900/20">
        <div className="max-w-7xl mx-auto px-6 py-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className="relative">
                <div className="absolute inset-0 bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 rounded-xl blur opacity-75 animate-pulse"></div>
                <div className="relative bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 p-3 rounded-xl shadow-xl">
                  <span className="text-3xl">🤖</span>
                </div>
              </div>
              <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400 bg-clip-text text-transparent drop-shadow-2xl">
                  Agentic Marketplace
                </h1>
                <p className="text-sm text-slate-300/80 mt-1 font-light">
                  ✨ AI-powered agents for intelligent automation
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setCurrentPage('onboard')}
                className="px-4 py-2 bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 hover:from-indigo-700 hover:via-purple-700 hover:to-pink-700 text-white rounded-lg font-medium transition-all duration-200 shadow-lg shadow-purple-500/40 hover:shadow-xl hover:shadow-purple-500/60 flex items-center gap-2"
              >
                <span>✨</span>
                <span>Register Your Agent</span>
              </button>
              <div className="px-4 py-2 rounded-lg bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 text-sm font-medium shadow-md shadow-emerald-500/20">
                🟢 All Systems Online
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Category Tabs */}
      <CategoryTabs
        categories={categories}
        selectedCategory={selectedCategory}
        onSelectCategory={setSelectedCategory}
        agentCounts={agentCounts}
      />

      {/* Main Content */}
      <main className="relative max-w-7xl mx-auto px-6 py-6">
        {!selectedAgent ? (
          <>
            <div className="mb-8">
              <SearchBar value={searchQuery} onChange={setSearchQuery} />
            </div>
            {loading ? (
              <div className="flex items-center justify-center py-20">
                <div className="text-center">
                  <div className="inline-block p-4 rounded-2xl bg-gradient-to-br from-indigo-600/30 via-purple-600/30 to-pink-600/30 mb-4 animate-pulse shadow-xl shadow-purple-500/30 border border-purple-400/40">
                    <div className="text-5xl">🤖</div>
                  </div>
                  <div className="text-purple-300 font-medium">Loading agents...</div>
                </div>
              </div>
            ) : (
              <div className="overflow-y-auto custom-scrollbar">
                <AgentList
                  agents={filteredAgents}
                  categories={categories}
                  selectedAgent={selectedAgent}
                  onSelectAgent={setSelectedAgent}
                  searchQuery={searchQuery}
                />
              </div>
            )}
          </>
        ) : (
          <div className="min-h-[calc(100vh-200px)]">
            <ChatInterface agent={selectedAgent} onBack={() => setSelectedAgent(null)} />
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
