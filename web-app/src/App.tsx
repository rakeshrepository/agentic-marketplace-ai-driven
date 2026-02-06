import { useState, useEffect } from 'react';
import { Agent, Category } from './types';
import { getAllAgents } from './services/agentService';
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
    loadAgents();
  }, []);

  const loadAgents = async () => {
    try {
      setLoading(true);
      const fetchedAgents = await getAllAgents();
      setAgents(fetchedAgents);
      
      // Extract unique categories from agents
      const uniqueCategories = new Map<string, Category>();
      fetchedAgents.forEach(agent => {
        if (!uniqueCategories.has(agent.category)) {
          uniqueCategories.set(agent.category, {
            id: agent.category,
            name: getCategoryName(agent.category),
            description: getCategoryDescription(agent.category),
            icon: getCategoryIcon(agent.category),
          });
        }
      });
      setCategories(Array.from(uniqueCategories.values()));
    } catch (error) {
      console.error('Failed to load agents:', error);
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

  const getCategoryName = (id: string): string => {
    const names: Record<string, string> = {
      'infrastructure': 'Infrastructure & DevOps',
      'data-analytics': 'Data & Analytics',
      'communication': 'Communication',
    };
    return names[id] || id;
  };

  const getCategoryDescription = (id: string): string => {
    const descriptions: Record<string, string> = {
      'infrastructure': 'Manage your infrastructure, databases, and messaging systems',
      'data-analytics': 'Process, analyze, and visualize your data',
      'communication': 'Email, messaging, and notification management',
    };
    return descriptions[id] || '';
  };

  const getCategoryIcon = (id: string): string => {
    const icons: Record<string, string> = {
      'infrastructure': '🏗️',
      'data-analytics': '📊',
      'communication': '💬',
    };
    return icons[id] || '📁';
  };

  // Render onboarding page
  if (currentPage === 'onboard') {
    return <AgentOnboarding />;
  }

  // Render home page
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
              <button
                onClick={() => setCurrentPage('onboard')}
                className="px-4 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white rounded-lg font-medium transition-all duration-200 shadow-lg hover:shadow-xl flex items-center gap-2"
              >
                <span>✨</span>
                <span>Register Your Agent</span>
              </button>
              <div className="px-4 py-2 rounded-lg bg-green-500/20 border border-green-500/30 text-green-300 text-sm font-medium">
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
                  <div className="inline-block p-4 rounded-2xl bg-gradient-to-br from-blue-500/20 to-cyan-500/20 mb-4 animate-pulse">
                    <div className="text-5xl">🤖</div>
                  </div>
                  <div className="text-blue-300 font-medium">Loading agents...</div>
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
