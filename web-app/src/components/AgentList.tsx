import React from 'react';
import { Agent, Category } from '../types';

interface AgentListProps {
  agents: Agent[];
  categories: Category[];
  selectedAgent: Agent | null;
  onSelectAgent: (agent: Agent) => void;
  searchQuery: string;
}

const categoryIconMap: Record<string, string> = {
  infrastructure: '⚡',
  devops: '🔧',
  'developer-tools': '🚀',
  'cicd-automation': '🔄',
  'data-analytics': '📊',
  'security-monitoring': '🛡️',
  'ai-intelligent': '💬',
  'integration-hub': '🌐',
  'custom-solutions': '🧩',
  default: '📁',
};

const iconMap: Record<string, string> = {
  kafka: '📊',
  database: '🗄️',
  cloud: '☁️',
  storage: '💾',
  code: '💻',
  book: '📚',
  test: '🧪',
  pipeline: '🔄',
  chart: '📈',
  support: '💬',
  server: '🖥️',
  puzzle: '🧩',
  default: '🤖',
};

export const AgentList: React.FC<AgentListProps> = ({
  agents,
  categories,
  selectedAgent,
  onSelectAgent,
  searchQuery,
}) => {

  const filteredAgents = agents.filter(
    (agent) =>
      agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.capabilities.some((cap) =>
        cap.toLowerCase().includes(searchQuery.toLowerCase())
      )
  );

  const agentsByCategory = categories.reduce((acc, category) => {
    acc[category.id] = filteredAgents.filter(
      (agent) => agent.category === category.id
    );
    return acc;
  }, {} as Record<string, Agent[]>);

  return (
    <div className="space-y-10 pb-8">
      {categories.map((category) => {
        const categoryAgents = agentsByCategory[category.id] || [];
        if (categoryAgents.length === 0) return null;

        // Show only first 3 agents
        const displayAgents = categoryAgents.slice(0, 3);
        const categoryIcon = categoryIconMap[category.id] || categoryIconMap.default;

        return (
          <div key={category.id} className="space-y-5">
            {/* Category Header */}
            <div className="flex items-center gap-4 pb-2 border-b border-purple-500/20">
              <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-gradient-to-br from-indigo-600/30 via-purple-600/30 to-pink-600/30 border border-purple-400/40 shadow-lg shadow-purple-500/30">
                <span className="text-3xl">{categoryIcon}</span>
              </div>
              <div className="flex-1">
                <h2 className="text-2xl font-bold bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400 bg-clip-text text-transparent drop-shadow-lg">
                  {category.name}
                </h2>
                <p className="text-sm text-slate-300/80 mt-1 font-light">{category.description}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-purple-300 font-semibold px-3 py-2 bg-gradient-to-r from-indigo-600/20 via-purple-600/20 to-pink-600/20 rounded-lg border border-purple-400/40 shadow-md shadow-purple-500/20">
                  {displayAgents.length} of {categoryAgents.length} {categoryAgents.length === 1 ? 'agent' : 'agents'}
                </span>
              </div>
            </div>

            {/* Flashcards Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {displayAgents.map((agent) => {
                const agentIcon = iconMap[agent.icon] || iconMap.default;
                const isComingSoon = agent.status === 'coming-soon';
                const isDisabled = isComingSoon;

                return (
                  <div
                    key={agent.id}
                    onClick={() => !isDisabled && onSelectAgent(agent)}
                    className={`group relative p-6 rounded-2xl transition-all duration-300 ${
                      isDisabled 
                        ? 'cursor-not-allowed opacity-60 bg-gradient-to-br from-slate-900/60 to-slate-950/60 border border-slate-700/50' 
                        : 'cursor-pointer hover:scale-[1.03] hover:shadow-2xl'
                    } ${
                      selectedAgent?.id === agent.id
                        ? 'bg-gradient-to-br from-indigo-600/30 via-purple-600/25 to-pink-600/30 border-2 border-purple-400/70 shadow-2xl shadow-purple-500/50 ring-2 ring-purple-400/40'
                        : 'bg-gradient-to-br from-slate-900/90 to-slate-950/90 border-2 border-slate-700/50 hover:border-purple-400/50 hover:from-slate-900/95 hover:to-slate-950/95 backdrop-blur-sm hover:shadow-purple-500/30'
                    }`}
                  >
                    {/* Coming Soon Badge */}
                    {isComingSoon && (
                      <div className="absolute top-3 right-3 px-3 py-1.5 bg-gradient-to-r from-amber-500/30 to-orange-500/30 border border-amber-400/50 text-amber-200 text-xs rounded-lg font-bold shadow-lg backdrop-blur-sm">
                        Coming Soon
                      </div>
                    )}

                    {/* Icon with Background */}
                    <div className="flex justify-center mb-4">
                      <div className={`relative flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-indigo-600/30 via-purple-600/30 to-pink-600/30 border border-purple-400/40 shadow-lg shadow-purple-500/30 transition-all duration-300 ${
                        !isDisabled && 'group-hover:scale-110 group-hover:rotate-3 group-hover:shadow-xl group-hover:shadow-purple-500/50'
                      }`}>
                        <span className="text-5xl">{agentIcon}</span>
                        {!isDisabled && (
                          <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-purple-400/0 to-pink-400/0 group-hover:from-purple-400/10 group-hover:to-pink-400/10 transition-all duration-300" />
                        )}
                      </div>
                    </div>

                    {/* Agent Name */}
                    <h3 className="font-bold text-center text-white mb-2 line-clamp-1 text-lg">
                      {agent.name}
                    </h3>

                    {/* Description */}
                    <p className="text-sm text-slate-300/80 text-center line-clamp-2 mb-4 min-h-[2.5rem] font-light">
                      {agent.description}
                    </p>

                    {/* Capabilities Count */}
                    <div className="flex items-center justify-center gap-2 pt-3 border-t border-slate-700/50">
                      <div className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-indigo-600/20 via-purple-600/20 to-pink-600/20 rounded-lg border border-purple-400/40 shadow-sm shadow-purple-500/20">
                        <span className="text-sm">⚡</span>
                        <span className="text-xs font-semibold text-purple-300">{agent.capabilities.length} capabilities</span>
                      </div>
                    </div>

                    {/* Hover Effect Border */}
                    {!isDisabled && (
                      <div className="absolute inset-0 rounded-2xl bg-gradient-to-br from-purple-500/0 to-pink-500/0 group-hover:from-purple-500/5 group-hover:to-pink-500/5 transition-all duration-300 pointer-events-none" />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
      {filteredAgents.length === 0 && (
        <div className="text-center py-16">
          <div className="inline-block p-6 rounded-2xl bg-gradient-to-br from-slate-900/90 to-slate-950/90 border-2 border-slate-700/50 mb-4 shadow-xl">
            <div className="text-6xl mb-4">🔍</div>
          </div>
          <h3 className="text-xl font-bold text-white mb-2">No Agents Found</h3>
          <p className="text-slate-300/70 font-light">Try adjusting your search query: "{searchQuery}"</p>
        </div>
      )}
    </div>
  );
};
